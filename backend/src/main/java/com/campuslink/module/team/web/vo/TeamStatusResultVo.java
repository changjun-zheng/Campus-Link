package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.application.cmd.TeamResults.TeamStatusResult;

/** 关闭 / 审批后的组队状态（CR-137） */
public record TeamStatusResultVo(String status) {

    public static TeamStatusResultVo from(TeamStatusResult result) {
        return new TeamStatusResultVo(result.status().name());
    }
}
