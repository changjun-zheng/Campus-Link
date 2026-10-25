package com.campuslink.module.team.application.cmd;

import jakarta.validation.constraints.NotBlank;

/** 关闭入参（CR-137）：本轮只接受 {@code CLOSED}，其他值由应用层翻成 1001 */
public record UpdateTeamStatusCommand(@NotBlank String status) {
}
