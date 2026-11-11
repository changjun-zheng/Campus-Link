package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 管理端标签用例（F-ADMIN-004 标签侧 / CR-132，重命名与合并由 CR-217 补齐）：列表分页 + 删除 + 重命名 + 合并。
 *
 * <p>删除是**破坏性操作**：适配器在一个事务内先删关联、再清受影响帖子的 {@code posts.tags}
 * 冗余列、最后删 {@code tags} 行；成功后记一条 {@code TAG_DELETE} 审计（含标签名与受影响帖子数）。
 * 标签不存在 → {@code 404 / 3001} 且零审计（比照「二次删除不重复记 POST_DELETE」的口径）。
 *
 * <p>重命名 / 合并同样由适配器在单事务内同步 {@code tags}、{@code post_tags} 与 {@code posts.tags}
 * 三处；重命名撞其它标签名 → {@code 409 / 3008}（唯一键 {@code uk_tags_name} 是终局兜底），
 * 新旧同名视为幂等成功、不写库不记审计；合并不可撤销，审计记在已被删除的源标签 id 上。
 */
@Service
@RequiredArgsConstructor
public class AdminTagApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 与发帖入参「单个标签最长 32 字」同一口径（CR-083 / CR-217） */
    private static final int TAG_NAME_MAX_CHARS = 32;

    private final TagRepository tagRepository;
    private final AuditService auditService;

    /** 标签分页：{@code sort} 只认 use_count（默认，降序）与 name（升序）；keyword 空白视同未传 */
    public AdminTags page(String keyword, String sort, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = tagRepository.countTags(keyword);
        int offset = (int) Math.min((long) (currentPage - 1) * pageSize, Integer.MAX_VALUE);
        List<AdminTagView> items = tagRepository.pageTags(keyword, sort, offset, pageSize).stream()
                .map(t -> new AdminTagView(t.id(), t.name(), t.useCount(), t.createdAt()))
                .toList();
        return new AdminTags(items, total, currentPage, pageSize);
    }

    /** 删除标签并审计；标签不存在抛 3001（不删任何数据、不记审计） */
    public void delete(long operatorId, long tagId) {
        TagRepository.TagSummary tag = tagRepository.findTag(tagId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        int affectedPosts = tagRepository.deleteTag(tagId);
        auditService.record(operatorId, "TAG_DELETE", "tags", tagId,
                "tagName=" + tag.name() + ", affectedPosts=" + affectedPosts);
    }

    /**
     * 重命名（CR-217）：去首尾空白后 1~32 字；与**其它**标签同名 → 3008；标签不存在 → 3001；
     * 新旧同名视为幂等成功（不写库、不记审计）。唯一键冲突在并发下兜底为 3008。
     */
    public void rename(long operatorId, long tagId, String rawName) {
        String newName = rawName == null ? "" : rawName.trim();
        if (newName.isEmpty() || newName.length() > TAG_NAME_MAX_CHARS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        TagRepository.TagSummary tag = tagRepository.findTag(tagId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (newName.equals(tag.name())) {
            return;
        }
        tagRepository.findTagByName(newName)
                .filter(existing -> existing.id() != tagId)
                .ifPresent(existing -> {
                    throw new ApiException(ResultCode.TAG_NAME_TAKEN);
                });
        int affectedPosts;
        try {
            affectedPosts = tagRepository.renameTag(tagId, newName);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ResultCode.TAG_NAME_TAKEN);
        }
        auditService.record(operatorId, "TAG_RENAME", "tags", tagId,
                "tagName=" + tag.name() + "->" + newName + ", affectedPosts=" + affectedPosts);
    }

    /**
     * 合并（CR-217）：源与目标必须都存在且不同；源标签的帖子关联、冗余列与使用次数并入目标，
     * 源行删除。审计记在源标签 id 上（它已不存在，detail 里同时带目标名）。
     */
    public void merge(long operatorId, long sourceId, long targetId) {
        if (sourceId == targetId) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        TagRepository.TagSummary source = tagRepository.findTag(sourceId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        TagRepository.TagSummary target = tagRepository.findTag(targetId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        int affectedPosts = tagRepository.mergeTag(sourceId, targetId);
        auditService.record(operatorId, "TAG_MERGE", "tags", sourceId,
                "source=" + source.name() + ", target=" + target.name()
                        + ", affectedPosts=" + affectedPosts);
    }

    /** 分页结果（application 载体；web 层再映射成 VO） */
    public record AdminTags(List<AdminTagView> items, long total, int page, int size) {
    }

    /** 标签条目：{@code useCount} 是累计挂载次数（软删帖子不回退） */
    public record AdminTagView(long id, String name, int useCount, Instant createdAt) {
    }
}
