package com.campuslink.module.team.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组队关系规则（CR-137）：发起人即成员；申请待审批；只有 PENDING 可审批 */
class TeamMemberTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    @DisplayName("发起人建队即 ACCEPTED，不占审批流程")
    void ownerIsAccepted() {
        TeamMember owner = TeamMember.owner(7L, 1L, NOW);

        assertThat(owner.getRole()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(TeamMemberStatus.ACCEPTED);
        assertThat(owner.getDecidedAt()).isEqualTo(NOW);
        assertThat(owner.isPending()).isFalse();
    }

    @Test
    @DisplayName("申请初始 PENDING；接受 / 拒绝后 decidedAt 落时间")
    void applicationTransitions() {
        TeamMember member = TeamMember.application(7L, 2L, NOW);
        assertThat(member.getStatus()).isEqualTo(TeamMemberStatus.PENDING);
        assertThat(member.isPending()).isTrue();

        Instant decided = NOW.plusSeconds(60);
        member.accept(decided);
        assertThat(member.getStatus()).isEqualTo(TeamMemberStatus.ACCEPTED);
        assertThat(member.getDecidedAt()).isEqualTo(decided);
        assertThat(member.isAccepted()).isTrue();
    }

    @Test
    @DisplayName("非 PENDING 不可再次审批；已拒绝也可读回")
    void nonPendingCannotBeDecidedAgain() {
        TeamMember member = TeamMember.application(7L, 2L, NOW);
        member.reject(NOW.plusSeconds(10));

        assertThat(member.getStatus()).isEqualTo(TeamMemberStatus.REJECTED);
        assertThatThrownBy(() -> member.accept(NOW.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> member.reject(NOW.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }
}
