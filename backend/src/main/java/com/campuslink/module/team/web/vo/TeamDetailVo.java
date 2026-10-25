package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.application.cmd.TeamResults.TeamDetail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 组队详情（CR-137）：{@code descriptionHtml} 为服务端渲染 + 白名单净化后的 HTML；
 * {@code contact} 仅在 {@code contactVisible=true} 时出现（发起人 / 已加入成员）。
 */
public record TeamDetailVo(long id,
                           String title,
                           String descriptionHtml,
                           String direction,
                           String directionLabel,
                           String target,
                           int capacity,
                           long acceptedCount,
                           String status,
                           LocalDate deadline,
                           long ownerId,
                           String ownerNickname,
                           Instant createdAt,
                           List<TeamMemberVo> members,
                           String myRole,
                           String myApplicationStatus,
                           String contact,
                           boolean contactVisible) {

    public static TeamDetailVo from(TeamDetail detail) {
        return new TeamDetailVo(detail.id(), detail.title(), detail.descriptionHtml(),
                detail.direction().name(), detail.direction().getLabel(), detail.target(), detail.capacity(),
                detail.acceptedCount(), detail.status().name(), detail.deadline(), detail.ownerId(),
                detail.ownerNickname(), detail.createdAt(),
                detail.members().stream().map(TeamMemberVo::from).toList(),
                detail.myRole() == null ? null : detail.myRole().name(),
                detail.myApplicationStatus() == null ? null : detail.myApplicationStatus().name(),
                detail.contact(), detail.contactVisible());
    }
}
