package com.campuslink.module.account.web;

import com.campuslink.module.account.application.UserProfileApplicationService.PublicProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 用户公开资料视图（{@code /u/:id} 的资料卡）。
 *
 * <p>与 {@link UserVo} 刻意分成两个类型而不是给 {@code UserVo} 加计数：{@code UserVo} 走的是
 * 登录 / 注册 / {@code GET|PUT /users/me} 四个**本人视角**端点，带 {@code role} / {@code status} /
 * {@code verified}；本 VO 走公开端点，那三列一出现就是外泄面（封禁状态尤其——它属运营侧事实）。
 *
 * <p>六个计数的口径由发起人定死，写在这里防被后来者当「随手加的数」：
 * <ul>
 *   <li>{@code postCount} / {@code replyCount} —— 可见帖子 / 可见楼层数，分别与主页下方两条时间线的 total 同源；</li>
 *   <li>{@code likeCount} —— 可见帖子与可见楼层收到的赞之和（D-1）；</li>
 *   <li>{@code acceptedCount} —— 其回答被采纳为最佳答案的次数（D-2，回答侧）；</li>
 *   <li>{@code followerCount} / {@code followingCount} —— 关注关系事实数（与关系列表 total 口径不同：列表剔除已注销）。</li>
 * </ul>
 * 前四个计数与 {@code reputation} 取自 forum 侧<b>同一次</b> {@code authorStats} 调用（CR-138），
 * 故同一张卡上的数字不会来自不同时刻。
 *
 * <p>等级组（CR-138）：{@code reputation} 为读时加权值（{@code ReputationRules}），
 * {@code level} 1~6 与 {@code levelName} 是档位；{@code nextLevelAt} 为下一档门槛，**满级时为 null**
 * （「没有下一级」不是 0），{@code levelProgress} 为档内 0~100 的整数百分比。
 * 声望只统计可见内容，故内容被下架 / 取消采纳会使其下降——不记录历史最高（D6）。
 *
 * <p>{@code grade}（CR-145 届徽章）：注册时快照的名册**入学年级**（{@code CohortYear.normalize}
 * 归一后的 4 位年份，如 "2024"），只读、无编辑入口；null 表示无名册年级（bypass 注册 /
 * 名册无值 / 已匿名化），前端不渲染徽章。文案用「{grade} 级」、不做毕业年换算（方案 §7 D1）。
 * 同理 {@code avatarUrl} / {@code school} 仍不在这里：注册链路从不写入、也没有展示位。
 *
 * <p>计数字段是 {@code int}：契约上是整数计数、前端 TS 侧统一 {@code number}，与 {@code PostSummaryVo} 的
 * {@code replyCount} / {@code likeCount} 同形。来源在库里都是 SQL 聚合（{@code COUNT} / {@code SUM} → long），
 * 窄化只在这一处发生，且用饱和而非抛异常——展示层不该因为一个不可能到达的溢出而 500。
 */
@Schema(description = "用户公开资料（他人主页资料卡）")
public record UserProfileVo(Long id, String nickname, String avatarUrl, String major, String bio, Instant createdAt,
                            String grade,
                            int postCount, int replyCount, int likeCount, int acceptedCount,
                            int reputation, int level, String levelName, Long nextLevelAt, int levelProgress,
                            int followerCount, int followingCount) {

    public static UserProfileVo from(PublicProfile profile) {
        return new UserProfileVo(profile.id(), profile.nickname(), profile.avatarUrl(), profile.major(), profile.bio(),
                profile.createdAt(), profile.grade(),
                narrow(profile.postCount()), narrow(profile.replyCount()),
                narrow(profile.likeCount()), narrow(profile.acceptedCount()),
                narrow(profile.reputation()), profile.level(), profile.levelName(), profile.nextLevelAt(),
                profile.levelProgress(), narrow(profile.followerCount()), narrow(profile.followingCount()));
    }

    /** 饱和收窄：单校规模（学籍名册量级）不可能溢出 int，留这道只是为了不在展示层抛异常 */
    private static int narrow(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
