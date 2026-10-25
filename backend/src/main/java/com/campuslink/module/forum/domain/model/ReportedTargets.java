package com.campuslink.module.forum.domain.model;

import java.util.Set;

/**
 * 举报人对**仍待处理**（工单 {@code PENDING}）的举报目标集合（F-SAFE-002 / CR-140）。
 *
 * <p>读侧据此对举报人本人隐藏被举报内容：帖子 id 用于列表 / 搜索 / Feed / 主页 / 相似帖 / 收藏 / 详情的排除，
 * 楼层 id 用于楼层列表与引用摘要的排除。
 *
 * <p>用户类举报（{@link Report#TARGET_USER}）不进入本载体——用户对象没有"内容"可隐，
 * 等价于单向拉黑，属 {@code user_blocks} 的语义（CR-129），本 CR 不混用。
 */
public record ReportedTargets(Set<Long> postIds, Set<Long> replyIds) {

    /** 匿名访问或没有待处理举报时的空集；调用方无需判 null */
    public static final ReportedTargets EMPTY = new ReportedTargets(Set.of(), Set.of());

    public ReportedTargets {
        postIds = postIds == null ? Set.of() : Set.copyOf(postIds);
        replyIds = replyIds == null ? Set.of() : Set.copyOf(replyIds);
    }
}
