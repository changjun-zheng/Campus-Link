package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.application.cmd.TeamResults.TeamApplyResult;

import java.time.Instant;

/** 申请结果（CR-137）：status 恒为 PENDING，前端据此切按钮并刷新详情 */
public record TeamApplyResultVo(long userId, String status, Instant appliedAt) {

    public static TeamApplyResultVo from(TeamApplyResult result) {
        return new TeamApplyResultVo(result.userId(), result.status().name(), result.appliedAt());
    }
}
