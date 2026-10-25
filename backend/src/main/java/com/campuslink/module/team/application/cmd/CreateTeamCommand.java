package com.campuslink.module.team.application.cmd;

import com.campuslink.module.team.domain.model.Team;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 发布招募入参（F-TEAM-001 / CR-137）。
 *
 * <p>长度 / 人数上下限与 {@link Team} 的领域常量同源；{@code direction} 的枚举解析在应用层
 * （非法值 {@code 1001}），{@code contact} 的尖括号禁则由 {@code @Pattern} 表达。
 */
public record CreateTeamCommand(

        @NotBlank @Size(min = 2, max = Team.MAX_TITLE_CHARS) String title,

        @NotBlank @Size(max = Team.MAX_DESCRIPTION_CHARS) String descriptionMd,

        @NotBlank String direction,

        @Size(max = Team.MAX_TARGET_CHARS) String target,

        @NotNull @Min(Team.MIN_CAPACITY) @Max(Team.MAX_CAPACITY) Integer capacity,

        @NotBlank @Size(min = 5, max = Team.MAX_CONTACT_CHARS)
        @Pattern(regexp = "^[^<>]*$", message = "联系方式不能包含尖括号") String contact,

        LocalDate deadline) {
}
