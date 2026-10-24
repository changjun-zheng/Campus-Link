package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 管理端标签用例（F-ADMIN-004 标签侧 / CR-132）：列表分页 + 删除。
 *
 * <p>删除是**破坏性操作**：适配器在一个事务内先删关联、再清受影响帖子的 {@code posts.tags}
 * 冗余列、最后删 {@code tags} 行；成功后记一条 {@code TAG_DELETE} 审计（含标签名与受影响帖子数）。
 * 标签不存在 → {@code 404 / 3001} 且零审计（比照「二次删除不重复记 POST_DELETE」的口径）。
 *
 * <p>为什么不做合并 / 重命名：本 PRD 条目只有一行摘要、无详设；合并涉及目标选择与大批量
 * 冗余列重写，另开 CR 做（方案 §2 已登记）。
 */
@Service
@RequiredArgsConstructor
public class AdminTagApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

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

    /** 分页结果（application 载体；web 层再映射成 VO） */
    public record AdminTags(List<AdminTagView> items, long total, int page, int size) {
    }

    /** 标签条目：{@code useCount} 是累计挂载次数（软删帖子不回退） */
    public record AdminTagView(long id, String name, int useCount, Instant createdAt) {
    }
}
