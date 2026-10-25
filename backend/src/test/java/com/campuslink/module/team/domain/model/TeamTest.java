package com.campuslink.module.team.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组队聚合规则（F-TEAM-001 / CR-137）：字段校验、状态机三条边、关闭不可重开 */
class TeamTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    @DisplayName("新建招募：字段去首尾空白、目标赛事留空存 null、状态为 RECRUITING")
    void openNormalizesFields() {
        Team team = Team.open(5L, "  蓝桥杯组队  ", "  找两个队友  ", TeamDirection.ALGORITHM,
                "   ", 3, "  qq:123456  ", LocalDate.of(2026, 11, 30), NOW);

        assertThat(team.getId()).isNull();
        assertThat(team.getOwnerId()).isEqualTo(5L);
        assertThat(team.getTitle()).isEqualTo("蓝桥杯组队");
        assertThat(team.getDescriptionMd()).isEqualTo("找两个队友");
        assertThat(team.getTarget()).isNull();
        assertThat(team.getContact()).isEqualTo("qq:123456");
        assertThat(team.getStatus()).isEqualTo(TeamStatus.RECRUITING);
        assertThat(team.isRecruiting()).isTrue();
    }

    @Test
    @DisplayName("字段非法：标题过短 / 简介超长 / 人数越界 / 联系方式含尖括号一律拒绝")
    void openRejectsInvalidFields() {
        assertThatThrownBy(() -> Team.open(1L, "队", "简介", TeamDirection.OTHER, null, 2, "abcde", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Team.open(1L, "组队", "x".repeat(2001), TeamDirection.OTHER, null,
                2, "abcde", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Team.open(1L, "组队", "简介", TeamDirection.OTHER, null, 1, "abcde", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Team.open(1L, "组队", "简介", TeamDirection.OTHER, null, 21, "abcde", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Team.open(1L, "组队", "简介", TeamDirection.OTHER, null, 2, "a<b>c", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Team.open(1L, "组队", "简介", TeamDirection.OTHER, null, 2, "    ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("关闭幂等；关闭后 markFull 不复活")
    void closeIsIdempotentAndBlocksMarkFull() {
        Team team = team();

        team.close();
        team.close();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.CLOSED);

        team.markFull();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.CLOSED);
        assertThat(team.isRecruiting()).isFalse();
    }

    @Test
    @DisplayName("满员只从招募中置 FULL；关闭后不可重开（P2 已加入 4 人可正常转 FULL）")
    void markFullOnlyFromRecruiting() {
        Team team = team();

        team.markFull();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.FULL);

        team.close();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.CLOSED);
        team.markFull();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.CLOSED);
    }

    private static Team team() {
        return Team.open(1L, "组队", "简介", TeamDirection.BACKEND, "蓝桥杯", 4, "qq:123456", null, NOW);
    }
}
