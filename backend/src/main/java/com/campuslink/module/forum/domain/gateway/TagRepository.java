package com.campuslink.module.forum.domain.gateway;

import java.util.List;

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
}
