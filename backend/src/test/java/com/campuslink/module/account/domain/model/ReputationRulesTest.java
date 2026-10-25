package com.campuslink.module.account.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 声望加权公式（CR-138 / D1）：被采纳 ×10 + 获赞 ×2 + 发帖 ×1 + 回帖 ×1。
 *
 * <p>这里逐项验证权重，因为四个项都是加法、接错一项公式仍"看起来合理"：
 * 把获赞也按 ×1 算，用户只会觉得"声望涨得慢"，没有任何报错。
 */
class ReputationRulesTest {

    @Test
    @DisplayName("四项权重各自生效：3 采纳 + 30 赞 + 12 帖 + 7 回帖 = 30 + 60 + 12 + 7")
    void weightsEachContribute() {
        assertThat(ReputationRules.reputation(3, 30, 12, 7)).isEqualTo(109);
    }

    @Test
    @DisplayName("单因子隔离：只给被采纳 / 只给获赞时，系数分别是 10 与 2")
    void eachFactorIsIsolated() {
        assertThat(ReputationRules.reputation(1, 0, 0, 0)).isEqualTo(10);
        assertThat(ReputationRules.reputation(0, 1, 0, 0)).isEqualTo(2);
        assertThat(ReputationRules.reputation(0, 0, 1, 0)).isEqualTo(1);
        assertThat(ReputationRules.reputation(0, 0, 0, 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("零贡献回 0，不是 null 也不是 1")
    void zeroContributionIsZero() {
        assertThat(ReputationRules.reputation(0, 0, 0, 0)).isZero();
    }

    @Test
    @DisplayName("极大计数不溢出：百万级四计数相加仍精确（单校规模远达不到这个量级）")
    void largeCountsStayExact() {
        assertThat(ReputationRules.reputation(1_000_000, 1_000_000, 1_000_000, 1_000_000))
                .isEqualTo(14_000_000);
    }
}
