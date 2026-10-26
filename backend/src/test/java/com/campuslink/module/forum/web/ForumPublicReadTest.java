package com.campuslink.module.forum.web;

import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.InteractionApplicationService;
import com.campuslink.module.forum.application.PostApplicationService;
import com.campuslink.module.forum.application.ReplyApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.BoardType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 五个公开端点必须**无需登录即可读**（sprint-2.md A3：未登录可看列表与详情；F-FORUM-008 起含站内搜索）。
 *
 * <p>本类的断言价值主要在编译期：读端点保持 {@code @PublicEndpoint}、匿名调用（authentication 为 null）可读。
 * CR-048 起详情 / 楼层列表的签名带 {@code Authentication}（登录请求附带 likedByMe 等回显），但匿名时
 * 只作登录态回显、不做任何校验——若有人给读端点补登录拦截，这里会先失败。
 */
class ForumPublicReadTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-12T08:00:00Z");

    private final ForumQueryApplicationService forumQueryService = mock(ForumQueryApplicationService.class);
    private final PostApplicationService postApplicationService = mock(PostApplicationService.class);
    private final ReplyApplicationService replyApplicationService = mock(ReplyApplicationService.class);
    private final InteractionApplicationService interactionApplicationService = mock(InteractionApplicationService.class);

    private final BoardController boardController = new BoardController(forumQueryService);
    private final PostController postController = new PostController(forumQueryService, postApplicationService,
            interactionApplicationService, mock(com.campuslink.module.forum.application.PostViewCounter.class));
    private final ReplyController replyController = new ReplyController(forumQueryService, replyApplicationService,
            interactionApplicationService);

    @Test
    @DisplayName("版块列表：匿名可读")
    void boardsAreReadableAnonymously() {
        when(forumQueryService.listBoards())
                .thenReturn(List.of(Board.rehydrate(1L, "qna", "技术问答", "提问", BoardType.QUESTION, 1)));

        var response = boardController.list();

        assertThat(response.data()).extracting("code").containsExactly("qna");
    }

    @Test
    @DisplayName("帖子列表与详情：匿名可读（含 sort=hot 与 filter=unanswered——新参数没有把公开读端点变成受保护端点）")
    void postsAreReadableAnonymously() {
        when(forumQueryService.listPosts(null, "latest", null, 1, 20, null)).thenReturn(new PageResult<>(
                List.of(new PostSummary(1L, "qna", "技术问答", "标题", "张三", 0, 0, 0, null, null, "摘要", CREATED_AT, false, false, false)),
                1, 1, 20));
        when(forumQueryService.listPosts(null, "hot", null, 1, 20, null)).thenReturn(new PageResult<>(
                List.of(new PostSummary(1L, "qna", "技术问答", "标题", "张三", 0, 0, 0, null, null, "摘要", CREATED_AT, false, false, false)),
                1, 1, 20));
        when(forumQueryService.listPosts(null, "latest", "unanswered", 1, 20, null)).thenReturn(new PageResult<>(
                List.of(new PostSummary(2L, "qna", "技术问答", "待解答的题", "张三", 0, 0, 0, null, null, "摘要", CREATED_AT, false, false, false)),
                1, 1, 20));
        when(forumQueryService.postDetail(1L, null)).thenReturn(new PostDetail(1L, "qna", "技术问答", "QUESTION",
                "标题", "<p>正文</p>", 42L, "张三", 0, 0, 0, null, false, false, false, CREATED_AT, false));

        var list = postController.list(null, "latest", null, 1, 20, null);
        var hotList = postController.list(null, "hot", null, 1, 20, null);
        var unansweredList = postController.list(null, "latest", "unanswered", 1, 20, null);
        var detail = postController.detail(1L, null, null);

        assertThat(list.data().list()).hasSize(1);
        assertThat(list.data().total()).isEqualTo(1);
        assertThat(hotList.data().list()).hasSize(1);
        assertThat(unansweredList.data().list()).extracting("title").containsExactly("待解答的题");
        assertThat(detail.data().contentHtml()).isEqualTo("<p>正文</p>");
        assertThat(detail.data().likedByMe()).isFalse();
        assertThat(detail.data().favoritedByMe()).isFalse();
    }

    @Test
    @DisplayName("站内搜索：匿名可读（F-FORUM-008）")
    void searchIsReadableAnonymously() {
        when(forumQueryService.searchPosts("Redis", null, null, 1, 20, null)).thenReturn(new PageResult<>(
                List.of(new PostSummary(1L, "qna", "技术问答", "Redis 怎么用", "张三", 0, 0, 0, null, null, "摘要", CREATED_AT, false, false, false)),
                1, 1, 20));

        var response = postController.search("Redis", null, null, 1, 20, null);

        assertThat(response.data().list()).extracting("title").containsExactly("Redis 怎么用");
        assertThat(response.data().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("楼层列表：匿名可读")
    void repliesAreReadableAnonymously() {
        when(forumQueryService.listReplies(1L, 1, 20, null)).thenReturn(new PageResult<>(
                List.of(new ReplyItem(11L, 1, "<p>一</p>", 42L, "张三", false, 0, false, null, CREATED_AT)), 1, 1, 20));

        var response = replyController.list(1L, 1, 20, null);

        assertThat(response.data().list()).extracting("floorNo").containsExactly(1);
        assertThat(response.data().list()).extracting("likedByMe").containsExactly(false);
    }
}

