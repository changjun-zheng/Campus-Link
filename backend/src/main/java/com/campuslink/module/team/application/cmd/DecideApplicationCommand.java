package com.campuslink.module.team.application.cmd;

import jakarta.validation.constraints.NotBlank;

/** 审批入参（CR-137）：{@code ACCEPT} / {@code REJECT}，其他值由应用层翻成 1001 */
public record DecideApplicationCommand(@NotBlank String decision) {
}
