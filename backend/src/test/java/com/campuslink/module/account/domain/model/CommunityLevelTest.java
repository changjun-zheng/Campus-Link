package com.campuslink.module.account.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 6 档等级的阈值映射与档内进度（CR-138 / D2）。
 *
 * <p>边界值是本类的主角：每一档都取「下限」「下限 - 1」两头验证，
 * 因为 `>=` 写成 `>` 或阈值错一位，页面照样渲染出一个看着合理的档位（Lv2 显示成 Lv1 不会报错）。
 */
class CommunityLevelTest {

    @ParameterizedTest(name = "声望 {0} → {1}")
    @CsvSource({
            "0, LV1", "49, LV1",
            "50, LV2", "199, LV2",
            "200, LV3", "499, LV3",
            "500, LV4", "999, LV4",
            "1000, LV5", "1999, LV5",
            "2000, LV6", "999999, LV6"
    })
    @DisplayName("阈值是下限闭区间：每一档取「下限」与「下限 - 1」两头对账")
    void mapsReputationToLevelAtBothSidesOfEveryThreshold(long reputation, CommunityLevel expected) {
        assertThat(CommunityLevel.of(reputation)).isEqualTo(expected);
    }

    @Test
    @DisplayName("对外档位数字与名称成对出现（徽章显示「Lv4 贡献者」，错配看不出来）")
    void exposesLevelAndDisplayNameInPairs() {
        assertThat(CommunityLevel.LV1.level()).isEqualTo(1);
        assertThat(CommunityLevel.LV1.displayName()).isEqualTo("初入");
        assertThat(CommunityLevel.LV2.displayName()).isEqualTo("学徒");
        assertThat(CommunityLevel.LV3.displayName()).isEqualTo("常客");
        assertThat(CommunityLevel.LV4.displayName()).isEqualTo("贡献者");
        assertThat(CommunityLevel.LV5.displayName()).isEqualTo("资深成员");
        assertThat(CommunityLevel.LV6.level()).isEqualTo(6);
        assertThat(CommunityLevel.LV6.displayName()).isEqualTo("社区之星");
    }

    @Test
    @DisplayName("满级无下一档：nextThreshold 回 null（出参 nextLevelAt 随之不出现），进度恒 100")
    void topLevelHasNoNextThreshold() {
        assertThat(CommunityLevel.LV6.nextThreshold()).isNull();
        assertThat(CommunityLevel.LV6.progressPercent(2000)).isEqualTo(100);
        assertThat(CommunityLevel.LV6.progressPercent(999999)).isEqualTo(100);
    }

    @Test
    @DisplayName("下一档门槛逐档对账：差 1 点升级时进度是真实值而不是提前 100")
    void nextThresholdAndProgressAtBothEndsOfEveryLevel() {
        assertThat(CommunityLevel.LV1.nextThreshold()).isEqualTo(50L);
        assertThat(CommunityLevel.LV2.nextThreshold()).isEqualTo(200L);
        assertThat(CommunityLevel.LV3.nextThreshold()).isEqualTo(500L);
        assertThat(CommunityLevel.LV4.nextThreshold()).isEqualTo(1000L);
        assertThat(CommunityLevel.LV5.nextThreshold()).isEqualTo(2000L);

        // 档位起点 → 0%；档位终点（下一档下限 - 1）→ 99%
        assertThat(CommunityLevel.LV1.progressPercent(0)).isZero();
        assertThat(CommunityLevel.LV1.progressPercent(49)).isEqualTo(98);
        assertThat(CommunityLevel.LV2.progressPercent(50)).isZero();
        assertThat(CommunityLevel.LV2.progressPercent(199)).isEqualTo(99);
        assertThat(CommunityLevel.LV5.progressPercent(1000)).isZero();
        assertThat(CommunityLevel.LV5.progressPercent(1999)).isEqualTo(99);
    }

    @Test
    @DisplayName("档内进度按比例取整：25 / 50 → 50%（不是四舍五入成别的档）")
    void progressIsProportionalInsideTheLevel() {
        assertThat(CommunityLevel.LV1.progressPercent(25)).isEqualTo(50);
        assertThat(CommunityLevel.LV3.progressPercent(350)).isEqualTo(50);
    }

    @Test
    @DisplayName("不可达输入不抛异常：负声望按最低档、进度 0（展示层不因输入兜底失败而 500）")
    void negativeReputationFallsBackToFirstLevel() {
        assertThat(CommunityLevel.of(-1)).isEqualTo(CommunityLevel.LV1);
        assertThat(CommunityLevel.LV1.progressPercent(-1)).isZero();
    }
}
