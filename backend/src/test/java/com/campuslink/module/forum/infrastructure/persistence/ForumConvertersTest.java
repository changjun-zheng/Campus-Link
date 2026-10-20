package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.Report;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 论坛三个防腐层转换器的双向映射（CR-111 覆盖率批）：
 * {@link BoardConverter}（只读参考数据）、{@link ReplyConverter}、{@link ReportConverter}。
 *
 * <p>守的是**可空列的归一规则**，它们是读侧渲染差异的来源：
 * 回复的 {@code like_count} 为 NULL 回落 0、{@code is_accepted} 为 NULL 视为 false；
 * 举报工单的 resolution / handler_id / closed_at 在未闭环时必须原样保持 null（不能补空串，
 * 否则运营页看不出"这条还没处理"）。
 *
 * <p>三个转换器共享同一套 DO ↔ record/聚合约定，合一个类是为了让上述归一规则排在一起可读；
 * 本类不证明持久化行为（无 SQL、无数据库）。
 */
class ForumConvertersTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T08:00:00Z");

    @Nested
    @DisplayName("BoardConverter")
    class BoardConverterCases {

        @Test
        @DisplayName("版块行转领域：id / code / name / description / type / sort 六列齐全，enabled 等表专有列不进模型")
        void mapsAllDisplayColumns() {
            BoardDO d = new BoardDO();
            d.setId(3L);
            d.setCode("backend");
            d.setName("后端开发");
            d.setDescription("Java / 数据库 / 中间件");
            d.setType(BoardType.DISCUSSION.name());
            d.setSort(4);
            d.setEnabled(true);

            Board board = BoardConverter.toDomain(d);

            assertThat(board.getId()).isEqualTo(3L);
            assertThat(board.getCode()).isEqualTo("backend");
            assertThat(board.getName()).isEqualTo("后端开发");
            assertThat(board.getDescription()).isEqualTo("Java / 数据库 / 中间件");
            assertThat(board.getType()).isEqualTo(BoardType.DISCUSSION);
            assertThat(board.getSort()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("ReplyConverter")
    class ReplyConverterCases {

        @Test
        @DisplayName("toDo：引用楼层 id 随聚合写出（CR-085），is_accepted 取自聚合而非 DO")
        void toDoCarriesQuotedReplyId() {
            Reply reply = Reply.rehydrate(5L, 7L, 42L, 2, "原文", "<p>原文</p>", 99L, true, 3, CREATED_AT);

            ReplyDO d = ReplyConverter.toDo(reply);

            assertThat(d.getId()).isEqualTo(5L);
            assertThat(d.getPostId()).isEqualTo(7L);
            assertThat(d.getAuthorId()).isEqualTo(42L);
            assertThat(d.getFloorNo()).isEqualTo(2);
            assertThat(d.getQuotedReplyId()).isEqualTo(99L);
            assertThat(d.getIsAccepted()).isTrue();
            assertThat(d.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("toDomain：like_count 为 NULL 回落 0（历史行），is_accepted 为 NULL 视为 false")
        void toDomainNormalizesNullableCounts() {
            ReplyDO d = new ReplyDO();
            d.setId(5L);
            d.setPostId(7L);
            d.setAuthorId(42L);
            d.setFloorNo(1);
            d.setContentMd("原文");
            d.setContentHtml("<p>原文</p>");
            d.setLikeCount(null);
            d.setIsAccepted(null);

            Reply reply = ReplyConverter.toDomain(d);

            assertThat(reply.getFloorNo()).isEqualTo(1);
            assertThat(reply.getLikeCount()).isZero();
            assertThat(reply.isAccepted()).isFalse();
            assertThat(reply.getQuotedReplyId()).isNull();
        }
    }

    @Nested
    @DisplayName("ReportConverter")
    class ReportConverterCases {

        @Test
        @DisplayName("toDo：未闭环工单的 resolution / handler_id / closed_at 保持 null")
        void toDoKeepsUnresolvedFieldsNull() {
            Report pending = new Report(9L, 42L, Report.TARGET_REPLY, 99L, "HARASS", "人身攻击",
                    "PENDING", null, null, CREATED_AT, null);

            ReportDO d = ReportConverter.toDo(pending);

            assertThat(d.getId()).isEqualTo(9L);
            assertThat(d.getTargetType()).isEqualTo(Report.TARGET_REPLY);
            assertThat(d.getTargetId()).isEqualTo(99L);
            assertThat(d.getStatus()).isEqualTo("PENDING");
            assertThat(d.getResolution()).isNull();
            assertThat(d.getHandlerId()).isNull();
            assertThat(d.getClosedAt()).isNull();
        }

        @Test
        @DisplayName("往返：闭环工单经 toDo → toDomain 后字段逐项守恒（含 resolution 与处理人）")
        void roundTripPreservesResolvedFields() {
            Report closed = new Report(9L, 42L, Report.TARGET_USER, 5L, "SPAM", "刷屏",
                    "CLOSED", "REMOVE：广告", 1L, CREATED_AT, CREATED_AT.plusSeconds(3600));

            Report back = ReportConverter.toDomain(ReportConverter.toDo(closed));

            assertThat(back).isEqualTo(closed);
        }
    }
}
