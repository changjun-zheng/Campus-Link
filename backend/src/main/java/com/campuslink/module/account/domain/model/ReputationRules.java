package com.campuslink.module.account.domain.model;

/**
 * 声望计分规则（F-GAME-001 / CR-138 / D1）：把既有可见贡献加权成一个对外展示的读数。
 *
 * <p>公式单点在这里，{@link CommunityLevel} 只负责把读数映射成档位——两者都是纯 JDK 规则，
 * 不依赖框架、不落库；主页每次读时聚合（D4），故**不存在**"公式改了要刷历史数据"的问题。
 *
 * <p>四个入参的口径由调用方（account 的 {@code UserProfileApplicationService}）保证，
 * 且全部来自 forum 侧**同一次** {@code authorStats}：只统计可见内容（未删 + PUBLISHED），
 * 被下架 / 取消采纳会如实让声望下降（D6，不承诺只升不降）。
 *
 * <p>权重定死为 被采纳 ×10 + 获赞 ×2 + 发帖 ×1 + 回帖 ×1；
 * 改权重或档位属产品口径变更，须另开 CR（D7），不在运行期可配。
 */
public final class ReputationRules {

    /** 被采纳为最佳答案的权重：最能体现"帮到了别人" */
    public static final long ACCEPTED_WEIGHT = 10;
    /** 获赞的权重（帖子赞 + 楼层赞之和） */
    public static final long LIKE_WEIGHT = 2;
    /** 发帖的权重 */
    public static final long POST_WEIGHT = 1;
    /** 回帖的权重 */
    public static final long REPLY_WEIGHT = 1;

    private ReputationRules() {
    }

    /**
     * 声望 = 被采纳 ×10 + 获赞 ×2 + 发帖 ×1 + 回帖 ×1。
     *
     * <p>入参都是 {@code COUNT(*)} 量级（单校规模），四项相加距 {@code long} 上限有十几个数量级，
     * 故不做溢出保护：为一个不可达的溢出加饱和逻辑，只会让公式本身变得难读。
     */
    public static long reputation(long acceptedCount, long likeCount, long postCount, long replyCount) {
        return acceptedCount * ACCEPTED_WEIGHT
                + likeCount * LIKE_WEIGHT
                + postCount * POST_WEIGHT
                + replyCount * REPLY_WEIGHT;
    }
}
