package com.campuslink.module.account.web;

import com.campuslink.module.account.application.UserProfileApplicationService.PublicProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 用户公开资料视图（他人主页 {@code /u/:id} 的资料卡）。
 *
 * <p>与 {@link UserVo} 刻意分成两个类型而不是给 {@code UserVo} 加五个计数：{@code UserVo} 走的是
 * 登录 / 注册 / {@code GET|PUT /users/me} 四个**本人视角**端点，带 {@code role} / {@code status} /
 * {@code verified}；本 VO 走公开端点，那三列一出现就是外泄面（封禁状态尤其——它属运营侧事实）。
 * <p>{@code likeCount} 与 {@code acceptedCount}（CR-104）的口径由发起人定死，写在这里防被后来者当「随手加的两个数」：
 * 前者是<b>该作者可见帖子与可见楼层收到的赞之和</b>（D-1），后者是<b>其回答被采纳为最佳答案的次数</b>（D-2，回答侧）；
 * 两者都只统计可见内容，且与 {@code postCount} 取自 forum 侧<b>同一次</b> {@code authorStats} 调用。
 *
 * 同理 {@code avatarUrl} / {@code school} / {@code grade} 也不在这里：注册链路从不写入、
 * 学籍名册的 {@code grade} 是「级」而产品口径已定为只展示「届」（CR-036 草案 R-6），下发一个前端不该用的列没有意义。
 *
 * <p>计数字段是 {@code int}：契约上是整数计数、前端 TS 侧统一 {@code number}，与 {@code PostSummaryVo} 的
 * {@code replyCount} / {@code likeCount} 同形。五个来源在库里都是 {@code COUNT(*)}（long），
 * 窄化只在这一处发生，且用饱和而非抛异常——展示层不该因为一个不可能到达的溢出而 500。
 */
@Schema(description = "用户公开资料（他人主页资料卡）")
public record UserProfileVo(Long id, String nickname, String major, String bio, Instant createdAt,
                            int postCount, int likeCount, int acceptedCount,
                            int followerCount, int followingCount) {

    public static UserProfileVo from(PublicProfile profile) {
        return new UserProfileVo(profile.id(), profile.nickname(), profile.major(), profile.bio(),
                profile.createdAt(), narrow(profile.postCount()), narrow(profile.likeCount()),
                narrow(profile.acceptedCount()), narrow(profile.followerCount()), narrow(profile.followingCount()));
    }

    /** 饱和收窄：单校规模（学籍名册量级）不可能溢出 int，留这道只是为了不在展示层抛异常 */
    private static int narrow(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
