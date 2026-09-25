package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.FollowApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.FavoriteRepository;
import com.campuslink.module.forum.domain.gateway.LikeRepository;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 他人主页时间线（{@code GET /api/v1/users/{id}/posts}，F-ACC-002 本体最小版）的用例级口径。
 *
 * <p>SQL 里的可见性条件由 {@code PostRepositoryImplCountVisibleByAuthorTest} 守（那是 Mockito 看不见的列语义），
 * 本类守的是**接线**——尤其这一条：本方法必须走 {@code findPageByAuthors}（复数）而**不是**
 * {@code findPageByAuthor}（单数）。两者只差一个字母，口径却相反：单数是作者本人视角（下架的也露出、带 status），
 * 复数是路人视角（{@code PUBLISHED} 且未删）。接错一个，就是把平台已处置的内容重新公开，
 * 而且在应用层的其它测试里完全看不出来。
 */
@ExtendWith(MockitoExtension.class)
class ForumQueryApplicationServiceUserPostsTest {

    private static final long AUTHOR_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-12T08:00:00Z");

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private FollowApplicationService followApplicationService;

    private ForumQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ForumQueryApplicationService(boardRepository, postRepository, replyRepository,
                likeRepository, favoriteRepository, accountApplicationService, followApplicationService,
                new MarkdownRenderer(), new AppProperties());
    }

    @Test
    @DisplayName("作者不可公开（不存在 / 已注销）→ 2007 / 404，且一条帖子都不查（不给探针当放大器）")
    void invisibleAuthorIsNotFoundBeforeAnyQuery() {
        when(accountApplicationService.publicAccountOf(AUTHOR_ID))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.listVisiblePostsByAuthor(AUTHOR_ID, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verify(postRepository, never()).findPageByAuthors(any(), anyInt(), anyInt());
        verify(boardRepository, never()).findAllEnabled();
    }

    @Test
    @DisplayName("正常分页：出参与全站列表同形（PostSummary），版块名与昵称照常规批量解析")
    void listsAuthorPostsAsStandardSummaries() {
        when(postRepository.findPageByAuthors(List.of(AUTHOR_ID), 1, 20))
                .thenReturn(new PageResult<>(List.of(post(7L, AUTHOR_ID)), 1, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(accountApplicationService.nicknamesOf(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "张三同学"));

        PageResult<PostSummary> page = service.listVisiblePostsByAuthor(AUTHOR_ID, 1, 20, null);

        assertThat(page.total()).isEqualTo(1L);
        PostSummary item = page.items().getFirst();
        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.boardCode()).isEqualTo("qna");
        assertThat(item.boardName()).isEqualTo("技术问答");
        assertThat(item.authorNickname()).isEqualTo("张三同学");
    }

    @Test
    @DisplayName("反证守门：走 findPageByAuthors（复数 / 路人视角），绝不走 findPageByAuthor（单数 / 作者视角）")
    void deliberatelyUsesThePublicVisibilityPath() {
        when(postRepository.findPageByAuthors(List.of(AUTHOR_ID), 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());

        service.listVisiblePostsByAuthor(AUTHOR_ID, 1, 20, null);

        verify(postRepository, never()).findPageByAuthor(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("分页归一：page<1 → 1、size>100 → 100，响应回显生效值（与全站列表同一口径）")
    void pagingIsNormalized() {
        when(postRepository.findPageByAuthors(List.of(AUTHOR_ID), 1, 100))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 100));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());

        PageResult<PostSummary> page = service.listVisiblePostsByAuthor(AUTHOR_ID, 0, 500, null);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("帖子计数透传仓储，且不重复校验账号——唯一调用方（account 的资料聚合）已先过同一道门")
    void countPassesThroughWithoutRecheckingTheAccount() {
        when(postRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(7L);

        assertThat(service.countVisiblePostsByAuthor(AUTHOR_ID)).isEqualTo(7L);
        verify(accountApplicationService, never()).publicAccountOf(anyLong());
    }

    private static Post post(Long id, Long authorId) {
        return Post.rehydrate(id, 1L, authorId, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                PostStatus.PUBLISHED, 0, 0, 0, null, java.util.List.of(), false, null, CREATED_AT, CREATED_AT);
    }

    private static Board board(Long id, String code, String name) {
        return Board.rehydrate(id, code, name, "描述", BoardType.QUESTION, 1);
    }
}
