package com.campuslink.module.forum.domain.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 出站端口：标签（CR-083 标签闭环）。tags / post_tags 表（V1）与 posts.tags 冗余列的
 * 全部写入收敛于此；调用方（发帖用例）保证 names 已规范化（≤5 个、非空）。
 */
public interface TagRepository {

    /**
     * 把标签挂到帖子上：逐个 find-or-create tags 行、写 post_tags 关联（重复幂等）、
     * 回写 posts.tags 冗余列（供列表展示与搜索 LIKE 兜底）。
     */
    void attachToPost(long postId, List<String> names);

    /**
     * 编辑时的**整组替换**（F-FORUM-007 / CR-128）：先解除该帖全部旧关联并回退旧标签的 use_count，
     * 再按 {@link #attachToPost} 挂新标签。空列表表示清空标签（关联与冗余列一并清空）。
     *
     * <p>为什么必须收成一个端口方法而不是让调用方先 detach 再 attach：两步之间若抛异常，
     * 会留下"旧关联已删、新标签未挂"的半截状态；事务边界与执行顺序应封在适配器里一处表达。
     */
    void replaceForPost(long postId, List<String> names);

    /**
     * 管理端标签列表（F-ADMIN-004 标签侧 / CR-132）：{@code keyword} 对名称做 LIKE（空白视同未传），
     * {@code sort} 只接受 {@code name} 与 {@code use_count} 两个分支（其余一律按 use_count 降序），
     * 排序在 SQL 的静态片段里分支、**不做字符串拼接**。
     */
    List<TagSummary> pageTags(String keyword, String sort, int offset, int limit);

    /** 与 {@link #pageTags} 同一 WHERE 的计数（total 与页内容同源） */
    long countTags(String keyword);

    /** 删除前的存在性校验用；已不存在返回 empty */
    Optional<TagSummary> findTag(long tagId);

    /**
     * 删除标签（CR-132）：事务内**先删 post_tags 关联**（{@code fk_pt_tag} 外键要求）、
     * 再把该标签从受影响帖子的 {@code posts.tags} 冗余列移除、最后删 {@code tags} 行。
     *
     * @return 受影响的帖子数（审计 detail 用）；标签已被并发删除时返回 0
     */
    int deleteTag(long tagId);

    /**
     * 管理端标签摘要。{@code useCount} 是**累计挂载次数**（{@code tags.use_count}，
     * 软删帖子不回退），不是"当前可见帖子数"；UI 文案需按此口径说明。
     */
    record TagSummary(long id, String name, int useCount, Instant createdAt) {
    }
}
