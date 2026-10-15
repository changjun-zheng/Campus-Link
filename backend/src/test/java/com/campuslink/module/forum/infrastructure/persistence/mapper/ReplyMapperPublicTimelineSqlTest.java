package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.campuslink.module.forum.domain.model.PublicReplyRow;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 他人主页三条聚合与那条分页 SQL 的**字面量结构测试**（CR-104）。
 *
 * <p>为什么用反射读注解而不是 Mockito 断言返回值：这四条 WHERE 写在 {@code @Select} 里，
 * Mockito 打桩的仓储测试（{@code ForumQueryApplicationServicePublicTimelineTest}）根本看不见列语义——
 * 条件少写一条，返回照样是个像样的数字、页面照样渲染。本仓对这一类缺陷的先例做法是
 * "断言 SQL 片段"（见 {@code PostRepositoryImplCountVisibleByAuthorTest} 的同一立论），
 * 只是本处的条件存于注解字面量，于是取注解就是那条断言的等价物。
 *
 * <p>代价如实记一句：本类守的是**字面里有没有写**，不守"数据库真的按它滤了"；后者落在真机取证
 * （实施方案 §5：下架帖的楼层不进时间线、不进获赞与被采纳数）。
 */
class ReplyMapperPublicTimelineSqlTest {

    @Test
    @DisplayName("公开楼层时间线必须同时滤楼层侧与父帖侧的可见性（少一条就是把已处置内容重新公开）")
    void publicTimelineFiltersBothReplyAndParent() {
        String sql = selectOf(ReplyMapper.class, "findPublicPageByAuthor");

        assertThat(sql).contains("r.status = 'PUBLISHED'");
        assertThat(sql).contains("p.status = 'PUBLISHED'");
        assertThat(sql).contains("r.is_deleted = 0");
        assertThat(sql).contains("p.is_deleted = 0");
        assertThat(sql).contains("r.author_id = #{authorId}");
    }

    @Test
    @DisplayName("不取 r.status 列：公开载体没有这一位，SQL 也不该把它捞回来（PublicReplyRow 无该字段）")
    void publicTimelineDoesNotSelectStatusColumn() {
        String sql = selectOf(ReplyMapper.class, "findPublicPageByAuthor");
        String selectList = sql.substring(sql.indexOf("SELECT") + 6, sql.indexOf("FROM"));

        assertThat(selectList).doesNotContain("r.status");
        assertThat(java.util.Arrays.stream(PublicReplyRow.class.getRecordComponents())
                .map(c -> c.getName())).doesNotContain("status");
    }

    @Test
    @DisplayName("镜像一对成立：本人视角那条刻意**不**滤楼层自身 status（作者要看得见自己被下架的楼层）")
    void personalTimelineStillOmitsTheStatusFilter() {
        String personal = selectOf(ReplyMapper.class, "findPageByAuthor");

        assertThat(personal).doesNotContain("r.status = 'PUBLISHED'");
        assertThat(personal).contains("p.status = 'PUBLISHED'");
    }

    @Test
    @DisplayName("获赞 SUM 与时间线同 WHERE，且用 COALESCE 兜空（无可见楼层时要回 0，不能让拆箱拿到 null）")
    void likeSumSharesTheVisibilityPredicateAndDefaultsToZero() {
        String sql = selectOf(ReplyMapper.class, "sumVisibleLikeCountByAuthor");

        assertThat(sql).contains("COALESCE(SUM(r.like_count), 0)");
        assertThat(sql).contains("r.status = 'PUBLISHED'");
        assertThat(sql).contains("p.status = 'PUBLISHED'");
        assertThat(sql).contains("r.is_deleted = 0");
        assertThat(sql).contains("p.is_deleted = 0");
    }

    @Test
    @DisplayName("被采纳数＝可见楼层 + is_accepted=1（少写可见性会让被下架的回答继续替作者挣对外信号）")
    void acceptedCountRequiresVisibleContent() {
        String sql = selectOf(ReplyMapper.class, "countAcceptedVisibleByAuthor");

        assertThat(sql).contains("r.is_accepted = 1");
        assertThat(sql).contains("r.status = 'PUBLISHED'");
        assertThat(sql).contains("p.status = 'PUBLISHED'");
        assertThat(sql).contains("p.is_deleted = 0");
    }

    @Test
    @DisplayName("帖子侧 SUM 与「帖子 N」计数同一组条件（资料卡上「帖子 12 / 获赞 30」不许来自两套 WHERE）")
    void postLikeSumMatchesVisibleQueryConditions() {
        String sql = selectOf(PostMapper.class, "sumVisibleLikeCountByAuthor");

        assertThat(sql).contains("COALESCE(SUM(like_count), 0)");
        assertThat(sql).contains("status = 'PUBLISHED'");
        assertThat(sql).contains("is_deleted = 0");
        assertThat(sql).contains("author_id = #{authorId}");
    }

    private static String selectOf(Class<?> mapper, String methodName) {
        for (Method method : mapper.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Select annotation = method.getAnnotation(Select.class);
                assertThat(annotation).as("%s#%s 应为 @Select 注解 SQL", mapper.getSimpleName(), methodName)
                        .isNotNull();
                return String.join(" ", annotation.value());
            }
        }
        throw new AssertionError("未找到方法: " + mapper.getSimpleName() + "#" + methodName);
    }
}
