package com.campuslink.module.forum.domain.model;

import java.time.Instant;

/**
 * 他人主页的公开楼层窄读载体（CR-104）：一条**可见**楼层 + 它的父帖定位信息。
 *
 * <p>与 {@link MyReplyRow} 分成两个类型而不是共用，是刻意的：两者的可见性口径**相反**——
 * {@code MyReplyRow} 带 {@code status}，因为"我的回帖"要把被平台下架的楼层露给作者本人（可逆性判据，CR-071）；
 * 本载体服务**路人视角**，被下架的楼层根本不该出现，于是它既不需要 {@code status}，
 * 也不该在公开出参里带上——把 {@code status} 塞进公开载体等于向每个匿名访客下发"这条被处置过"。
 *
 * <p>为什么仍不复用 {@code MyReplyRow}：共用后编译期看不出接错，而接错的后果是把平台已处置内容重新公开，
 * 与本仓 {@code findPageByAuthor}（单数，本人）与 {@code findPageByAuthors}（复数，路人）踩过的同一类风险同级。
 *
 * <p>{@code contentMd} 只在应用层内部消费（经 {@code MarkdownRenderer} 截成摘要），
 * 公开出参 {@code PublicReplyVo} 只有 {@code summary}，原文不出用例边界。
 */
public record PublicReplyRow(Long id,
                             Long postId,
                             String postTitle,
                             int floorNo,
                             String contentMd,
                             Instant createdAt) {
}
