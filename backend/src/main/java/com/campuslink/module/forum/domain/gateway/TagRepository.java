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
}
