package com.campuslink.module.team.application.cmd;

import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.TeamApplicationItem;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.domain.model.TeamStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 组队用例出参载体（F-TEAM-001 / CR-137）：application 层不感知 web 的 VO 形状，
 * web 层再逐字段映射成契约 VO。
 */
public final class TeamResults {

    private TeamResults() {
    }

    public record TeamPage(List<TeamListItem> list, long total, int page, int size) {
    }

    public record MyTeamPage(List<MyTeamItem> list, long total, int page, int size) {
    }

    public record ApplicationPage(List<TeamApplicationItem> list, long total, int page, int size) {
    }

    /**
     * 详情：{@code descriptionHtml} 是服务端渲染 + jsoup 白名单后的 HTML；
     * {@code contact} 只在 {@code contactVisible=true}（发起人 / 已加入成员）时下发。
     */
    public record TeamDetail(long id,
                             String title,
                             String descriptionHtml,
                             TeamDirection direction,
                             String target,
                             int capacity,
                             long acceptedCount,
                             TeamStatus status,
                             LocalDate deadline,
                             long ownerId,
                             String ownerNickname,
                             Instant createdAt,
                             List<TeamMemberProfile> members,
                             TeamMemberRole myRole,
                             TeamMemberStatus myApplicationStatus,
                             String contact,
                             boolean contactVisible) {
    }

    /** 申请结果：前端据此把按钮切成「待审批」，详情再刷新 */
    public record TeamApplyResult(long userId, TeamMemberStatus status, Instant appliedAt) {
    }

    /** 关闭 / 审批后的组队状态（{@code CLOSED} 或 {@code FULL}） */
    public record TeamStatusResult(TeamStatus status) {
    }
}
