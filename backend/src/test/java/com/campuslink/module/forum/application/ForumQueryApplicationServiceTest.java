package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.MyPostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.MyReplyItem;
import com.campuslink.module.forum.application.cmd.ForumResults.PostBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;
import com.campuslink.module.forum.application.cmd.ForumResults.SimilarPostResult;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.FavoriteRepository;
import com.campuslink.module.forum.domain.gateway.LikeRepository;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.MyReplyRow;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostSortOrder;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.ReplyStatus;
import com.campuslink.module.forum.domain.model.SimilarPostRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询用例：摘要服务端截断（设计 §3.2）、分页口径归一（page≥1 / size≤100）、
 * 昵称跨上下文一次批量查 + 缺失回落（设计 §4.3）、不可读帖子一律 3001。
 */
@ExtendWith(MockitoExtension.class)
class ForumQueryApplicationServiceTest {

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
    private com.campuslink.module.account.application.FollowApplicationService followApplicationService;

    private AppProperties appProperties;
    private ForumQueryApplicationService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        service = new ForumQueryApplicationService(boardRepository, postRepository, replyRepository,
                likeRepository, favoriteRepository, accountApplicationService, followApplicationService,
                new MarkdownRenderer(), appProperties);
    }

    @Test
    @DisplayName("帖子列表：昵称一页一次批量查，版块名随项带出，查不到的昵称回落「已注销用户」")
    void listPostsResolvesBoardAndNicknamesInBatch() {
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 20))
                .thenReturn(new PageResult<>(List.of(post(1L, 100L), post(2L, 200L)), 2, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(accountApplicationService.nicknamesOf(List.of(100L, 200L))).thenReturn(Map.of(100L, "张三"));

        PageResult<PostSummary> page = service.listPosts(null, "latest", 1, 20, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(PostSummary::authorNickname)
                .containsExactly("张三", "已注销用户");
        assertThat(page.items()).extracting(PostSummary::boardCode).containsExactly("qna", "qna");
        assertThat(page.items()).extracting(PostSummary::boardName).containsExactly("技术问答", "技术问答");
        verify(accountApplicationService, times(1)).nicknamesOf(any());
    }

    @Test
    @DisplayName("列表点赞回显（CR-080）：登录时一页**一次**批量取已赞集合，并逐条落到 likedByMe")
    void listPostsMarksLikedByMeFromASingleBatchQuery() {
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 20))
                .thenReturn(new PageResult<>(List.of(post(1L, 101L), post(2L, 102L), post(3L, 103L)), 3, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());
        when(accountApplicationService.nicknamesOf(List.of(101L, 102L, 103L)))
                .thenReturn(Map.of(101L, "甲", 102L, "乙", 103L, "丙"));
        when(likeRepository.findLikedTargetIds(42L,
                com.campuslink.module.forum.domain.model.LikeTargetType.POST, List.of(1L, 2L, 3L)))
                .thenReturn(java.util.Set.of(2L));

        PageResult<PostSummary> page = service.listPosts(null, "latest", 1, 20, 42L);

        assertThat(page.items()).extracting(PostSummary::likedByMe).containsExactly(false, true, false);
        // 断言"只发生一次、且问的是整页 id"——逐条查（N+1）会在这里红
        verify(likeRepository, times(1)).findLikedTargetIds(42L,
                com.campuslink.module.forum.domain.model.LikeTargetType.POST, List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("列表点赞回显（CR-080）：匿名请求**一次仓储都不调**，likedByMe 恒 false（与详情同口径）")
    void anonymousListNeverTouchesLikeRepository() {
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 20))
                .thenReturn(new PageResult<>(List.of(post(1L, 101L)), 1, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());
        when(accountApplicationService.nicknamesOf(List.of(101L))).thenReturn(Map.of(101L, "甲"));

        PageResult<PostSummary> page = service.listPosts(null, "latest", 1, 20, null);

        assertThat(page.items()).extracting(PostSummary::likedByMe).containsExactly(false);
        org.mockito.Mockito.verifyNoInteractions(likeRepository);
    }

    @Test
    @DisplayName("列表点赞回显（CR-080）：空页不去查已赞集合（无 id 可问，省一次往返）")
    void emptyListSkipsLikeLookup() {
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());

        assertThat(service.listPosts(null, "latest", 1, 20, 42L).items()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(likeRepository);
    }

    @Test
    @DisplayName("分页归一：page<1 → 1、size>100 → 100，响应回显生效值")
    void pagingIsNormalized() {
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 100)).thenReturn(new PageResult<>(List.of(), 0, 1, 100));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());

        PageResult<PostSummary> page = service.listPosts(null, "latest", 0, 500, null);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
        verify(postRepository).findPage(null, PostSortOrder.LATEST, 1, 100);
    }

    @Test
    @DisplayName("未知版块 code → 3001，且不查帖子")
    void unknownBoardCodeIsNotFound() {
        when(boardRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPosts("nope", "latest", 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).findPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("摘要：去 Markdown 记号后截断到 120 字（服务端截断，前端不兜底）")
    void summaryIsStrippedAndTruncated() {
        String longMarkdown = "# 标题\n\n" + "正文".repeat(70);
        when(postRepository.findPage(null, PostSortOrder.LATEST, 1, 20))
                .thenReturn(new PageResult<>(List.of(post(1L, 100L, longMarkdown)), 1, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());
        when(accountApplicationService.nicknamesOf(any())).thenReturn(Map.of(100L, "张三"));

        String summary = service.listPosts(null, "latest", 1, 20, null).items().get(0).summary();

        assertThat(summary).doesNotContain("#").hasSize(120);
    }

    @Test
    @DisplayName("sort：hot（含大写与前后空白）按热榜查；latest 与缺省走同一路径（回归保护）")
    void sortAcceptsHotAndLatest() {
        when(postRepository.findPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());

        service.listPosts(null, "hot", 1, 20, null);
        service.listPosts(null, "HOT", 1, 20, null);
        service.listPosts(null, "  hot  ", 1, 20, null);
        verify(postRepository, times(3)).findPage(null, PostSortOrder.HOT, 1, 20);

        service.listPosts(null, "latest", 1, 20, null);
        service.listPosts(null, "LATEST", 1, 20, null);
        verify(postRepository, times(2)).findPage(null, PostSortOrder.LATEST, 1, 20);
    }

    @Test
    @DisplayName("sort：非法值 / 空串 / null → 1001，且不查仓储（不静默归一到最新序）")
    void sortRejectsUnknownValue() {
        for (String bad : Arrays.asList("foo", "new", "", "   ", null)) {
            assertThatThrownBy(() -> service.listPosts(null, bad, 1, 20, null))
                    .as("sort=%s 应被拒绝", bad)
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        }

        verify(postRepository, never()).findPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("详情：返回发布时渲染好的 contentHtml 与作者昵称")
    void detailReturnsRenderedHtml() {
        when(postRepository.findById(9L)).thenReturn(Optional.of(post(9L, 100L)));
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(accountApplicationService.nicknamesOf(List.of(100L))).thenReturn(Map.of(100L, "张三"));

        PostDetail detail = service.postDetail(9L, null);

        assertThat(detail.contentHtml()).isEqualTo("<p>正文</p>");
        assertThat(detail.boardCode()).isEqualTo("qna");
        assertThat(detail.boardName()).isEqualTo("技术问答");
        assertThat(detail.authorNickname()).isEqualTo("张三");
    }

    @Test
    @DisplayName("不可读的帖子（不存在 / REMOVED）→ 3001，楼层不暴露")
    void invisiblePostHidesDetailAndReplies() {
        when(postRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.postDetail(9L, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        assertThatThrownBy(() -> service.listReplies(9L, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(replyRepository, never()).findPageByPostId(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("REMOVED 的帖子对详情与楼层同样不可见 → 3001")
    void removedPostIsNotVisible() {
        when(postRepository.findById(9L)).thenReturn(Optional.of(
                Post.rehydrate(9L, 1L, 100L, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                        PostStatus.REMOVED, 0, 0, false, null, CREATED_AT, CREATED_AT)));

        assertThatThrownBy(() -> service.postDetail(9L, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    @DisplayName("楼层列表：floorNo 原样透出、昵称一并解析（楼层 = 回复序号）")
    void repliesExposeFloorNoAndNicknames() {
        when(postRepository.findById(9L)).thenReturn(Optional.of(post(9L, 100L)));
        when(replyRepository.findPageByPostId(9L, 1, 20))
                .thenReturn(new PageResult<>(List.of(reply(11L, 100L, 1), reply(12L, 200L, 2)), 2, 1, 20));
        when(accountApplicationService.nicknamesOf(List.of(100L, 200L)))
                .thenReturn(Map.of(100L, "张三", 200L, "李四"));

        PageResult<ReplyItem> page = service.listReplies(9L, 1, 20, null);

        assertThat(page.items()).extracting(ReplyItem::floorNo).containsExactly(1, 2);
        assertThat(page.items()).extracting(ReplyItem::authorNickname).containsExactly("张三", "李四");
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("详情登录态回显：viewerId 非空时批量查 likedByMe / favoritedByMe（F-FORUM-005）")
    void detailWithViewerPopulatesMyState() {
        when(postRepository.findById(9L)).thenReturn(Optional.of(post(9L, 100L)));
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(accountApplicationService.nicknamesOf(List.of(100L))).thenReturn(Map.of(100L, "张三"));
        when(likeRepository.findLikedTargetIds(42L, com.campuslink.module.forum.domain.model.LikeTargetType.POST,
                List.of(9L))).thenReturn(java.util.Set.of(9L));
        when(favoriteRepository.findFavoritedPostIds(42L, List.of(9L))).thenReturn(java.util.Set.of());

        PostDetail detail = service.postDetail(9L, 42L);

        assertThat(detail.likedByMe()).isTrue();
        assertThat(detail.favoritedByMe()).isFalse();
    }

    @Test
    @DisplayName("搜索：keyword 去首尾空白后传仓储，版块与昵称同列表口径解析（F-FORUM-008）")
    void searchResolvesBoardAndNicknamesLikeList() {
        when(postRepository.search("Redis", 1L, null, 1, 20))
                .thenReturn(new PageResult<>(List.of(post(1L, 100L), post(2L, 200L)), 2, 1, 20));
        when(boardRepository.findByCode("qna")).thenReturn(Optional.of(board(1L, "qna", "技术问答")));
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(accountApplicationService.nicknamesOf(List.of(100L, 200L))).thenReturn(Map.of(100L, "张三"));

        PageResult<PostSummary> page = service.searchPosts("  Redis  ", "qna", null, 1, 20, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(PostSummary::authorNickname).containsExactly("张三", "已注销用户");
        assertThat(page.items()).extracting(PostSummary::boardCode).containsExactly("qna", "qna");
        verify(postRepository).search("Redis", 1L, null, 1, 20);
    }

    @Test
    @DisplayName("搜索：keyword 过短（1 字，ngram 分不出词）→ 1001，且不查仓储")
    void searchRejectsSingleCharKeyword() {
        assertThatThrownBy(() -> service.searchPosts("R", null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verify(postRepository, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("搜索：keyword 缺失 / 空白 → 1001（缺参另由框架层 400 兜住，这里是应用层兜底）")
    void searchRejectsBlankKeyword() {
        assertThatThrownBy(() -> service.searchPosts(null, null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.searchPosts("   ", null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verify(postRepository, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("搜索：keyword 超 50 字 → 1001")
    void searchRejectsOverlongKeyword() {
        assertThatThrownBy(() -> service.searchPosts("词".repeat(51), null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("搜索：days 不在白名单（7 / 30 / 90）→ 1001，白名单值原样传仓储")
    void searchRejectsUnknownTimeWindow() {
        assertThatThrownBy(() -> service.searchPosts("Redis", null, 15, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        when(postRepository.search("Redis", null, 30, 1, 20)).thenReturn(new PageResult<>(List.of(), 0, 1, 20));
        when(boardRepository.findAllEnabled()).thenReturn(List.of());
        service.searchPosts("Redis", null, 30, 1, 20, null);
        verify(postRepository).search("Redis", null, 30, 1, 20);
    }

    @Test
    @DisplayName("搜索：未知版块 code → 3001，且不查帖子")
    void searchUnknownBoardCodeIsNotFound() {
        when(boardRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchPosts("Redis", "nope", null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    /**
     * CR-066：通知读时组装的标题摘要必须与详情页同一套可见性判据。
     * 改之前这里只靠 {@code findByIds} 排墓碑、不看 status，于是帖子被管理员下架后**标题仍留在别人的通知里**，
     * 点进去才 404——标题属内容本身，内容不可见时不该继续外泄。
     */
    @Test
    @DisplayName("通知摘要：已下架（REMOVED）帖子的标题不再返回，命中判定与详情页同源")
    void postBriefsExcludeRemovedPosts() {
        when(postRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                post(1L, 100L), post(2L, 200L, PostStatus.REMOVED)));

        Map<Long, PostBrief> briefs = service.postBriefsOf(List.of(1L, 2L));

        assertThat(briefs).containsOnlyKeys(1L);
        assertThat(briefs.get(1L).title()).isEqualTo("标题");
    }

    @Test
    @DisplayName("通知摘要：楼层的所属帖与楼层号**刻意不看 status**——已下架帖里的楼层仍要有落点")
    void replyBriefsKeepFloorInfoRegardlessOfPostVisibility() {
        when(replyRepository.findByIds(List.of(11L))).thenReturn(List.of(reply(11L, 200L, 3)));
        when(postRepository.findByIds(List.of(9L))).thenReturn(List.of(post(9L, 100L, PostStatus.REMOVED)));

        Map<Long, ReplyBrief> replyBriefs = service.replyBriefsOf(List.of(11L));
        Map<Long, PostBrief> postBriefs = service.postBriefsOf(List.of(9L));

        // 楼层号在、标题不在 → 通知条目"不给跳转"的判定由帖子侧单独决定（见 NotificationApplicationService#toItem）
        assertThat(replyBriefs.get(11L).postId()).isEqualTo(9L);
        assertThat(replyBriefs.get(11L).floorNo()).isEqualTo(3);
        assertThat(postBriefs).isEmpty();
    }

    /**
     * CR-071 / F-ACC-007b：「我的帖子」出参是**只面向作者**的 MyPostSummary（带 status），
     * 且这条链路一次都不查昵称——作者就是调用者本人，跨上下文那一次批量取昵称在此是纯浪费。
     */
    @Test
    @DisplayName("我的帖子：REMOVED 条目带着 status 出现、total 原样透传、不查昵称")
    void listMyPostsKeepsStatusAndSkipsNicknameLookup() {
        when(boardRepository.findAllEnabled()).thenReturn(List.of(board(1L, "qna", "技术问答")));
        when(postRepository.findPageByAuthor(42L, 1, 20)).thenReturn(new PageResult<>(
                List.of(post(1L, 42L), post(2L, 42L, PostStatus.REMOVED)), 45, 1, 20));

        PageResult<MyPostSummary> page = service.listMyPosts(42L, 1, 20);

        assertThat(page.total()).isEqualTo(45);
        assertThat(page.items()).extracting(MyPostSummary::status).containsExactly("PUBLISHED", "REMOVED");
        assertThat(page.items()).allSatisfy(item -> assertThat(item.boardCode()).isEqualTo("qna"));
        verify(accountApplicationService, never()).nicknamesOf(any());
    }

    @Test
    @DisplayName("我的帖子：分页归一沿用既有口径（size=999 → 100 且不报错，防被用来放大查询）")
    void listMyPostsNormalizesPaging() {
        when(boardRepository.findAllEnabled()).thenReturn(List.of());
        when(postRepository.findPageByAuthor(42L, 1, 100)).thenReturn(new PageResult<>(List.of(), 0, 1, 100));

        PageResult<MyPostSummary> page = service.listMyPosts(42L, 0, 999);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.items()).isEmpty();
        verify(postRepository).findPageByAuthor(42L, 1, 100);
    }

    /**
     * CR-071 / F-ACC-007c：父帖可见性在 SQL 里判（见 {@code ReplyRepositoryImplMyRepliesTest}），
     * 本层只做载体转换——尤其**不下发 contentMd 原文**，只给 120 字摘要。
     */
    @Test
    @DisplayName("我的回帖：父帖定位与 status 透传，正文只给摘要（超长被截断）")
    void listMyRepliesMapsRowsToSummaries() {
        when(replyRepository.findPageByAuthor(42L, 2, 20)).thenReturn(new PageResult<>(
                List.of(myReplyRow("内".repeat(300))), 45, 2, 20));

        PageResult<MyReplyItem> page = service.listMyReplies(42L, 2, 20);

        MyReplyItem item = page.items().getFirst();
        assertThat(page.total()).isEqualTo(45);
        assertThat(item.postId()).isEqualTo(9L);
        assertThat(item.postTitle()).isEqualTo("父帖标题");
        assertThat(item.floorNo()).isEqualTo(3);
        assertThat(item.status()).isEqualTo("REMOVED");
        assertThat(item.summary()).hasSizeLessThan(300).hasSizeGreaterThan(100);
    }

    private static MyReplyRow myReplyRow(String contentMd) {
        return new MyReplyRow(11L, 9L, "父帖标题", 3, contentMd, ReplyStatus.REMOVED, CREATED_AT);
    }

    private static Post post(Long id, Long authorId) {
        return post(id, authorId, "正文");
    }

    private static Post post(Long id, Long authorId, String contentMd) {
        return Post.rehydrate(id, 1L, authorId, BoardType.QUESTION, "标题", contentMd, "<p>正文</p>",
                PostStatus.PUBLISHED, 0, 0, false, null, CREATED_AT, CREATED_AT);
    }

    private static Post post(Long id, Long authorId, PostStatus status) {
        return Post.rehydrate(id, 1L, authorId, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                status, 0, 0, false, null, CREATED_AT, CREATED_AT);
    }

    private static Board board(Long id, String code, String name) {
        return Board.rehydrate(id, code, name, "描述", BoardType.QUESTION, 1);
    }

    private static Reply reply(Long id, Long authorId, int floorNo) {
        return Reply.rehydrate(id, 9L, authorId, floorNo, "内容", "<p>内容</p>", false, 0, CREATED_AT);
    }

    // ── Similar-post recommendation (findSimilarPosts) ──────────────────────────────────────────

    @Test
    @DisplayName("相似帖子：功能开关关闭时返回空列表，不查仓储")
    void findSimilarPostsReturnsEmptyWhenDisabled() {
        appProperties.getAi().getSimilar().setEnabled(false);

        List<SimilarPostResult> result = service.findSimilarPosts("如何学习Java编程", 42L);

        assertThat(result).isEmpty();
        verify(postRepository, never()).findSimilar(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("相似帖子：标题为 null 时返回空列表，不查仓储")
    void findSimilarPostsReturnsEmptyForNullTitle() {
        List<SimilarPostResult> result = service.findSimilarPosts(null, 42L);

        assertThat(result).isEmpty();
        verify(postRepository, never()).findSimilar(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("相似帖子：标题长度 < minTitleLength(6) 时返回空列表，不查仓储")
    void findSimilarPostsReturnsEmptyForShortTitle() {
        List<SimilarPostResult> result = service.findSimilarPosts("Java", 42L);

        assertThat(result).isEmpty();
        verify(postRepository, never()).findSimilar(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("相似帖子：标题超过 100 字符时抛 1001（CR-077 Critical #1）")
    void findSimilarPostsThrowsForOverlongTitle() {
        String longTitle = "a".repeat(101);

        assertThatThrownBy(() -> service.findSimilarPosts(longTitle, 42L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(postRepository, never()).findSimilar(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("相似帖子：Q&A 版块不存在时返回空列表，不查仓储")
    void findSimilarPostsReturnsEmptyWhenQnaBoardMissing() {
        when(boardRepository.findByCode("qna")).thenReturn(Optional.empty());

        List<SimilarPostResult> result = service.findSimilarPosts("如何学习Java编程", 42L);

        assertThat(result).isEmpty();
        verify(postRepository, never()).findSimilar(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("相似帖子：正常情况下返回相似帖子列表，排除当前用户自己的帖子")
    void findSimilarPostsReturnsResultsExcludingOwnPosts() {
        when(boardRepository.findByCode("qna")).thenReturn(Optional.of(board(1L, "qna", "技术问答")));
        when(postRepository.findSimilar("如何学习Java编程", 1L, 42L, 3))
                .thenReturn(List.of(
                        new SimilarPostRow(10L, 1L, "Java入门", 2, false, CREATED_AT),
                        new SimilarPostRow(11L, 1L, "Java进阶", 1, true, CREATED_AT)));

        List<SimilarPostResult> result = service.findSimilarPosts("如何学习Java编程", 42L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).boardCode()).isEqualTo("qna");
        assertThat(result.get(0).boardName()).isEqualTo("技术问答");
        assertThat(result.get(1).id()).isEqualTo(11L);
        assertThat(result.get(1).accepted()).isTrue();
        // excludeAuthorId=42 passed to repository — own posts excluded at SQL level
        verify(postRepository).findSimilar("如何学习Java编程", 1L, 42L, 3);
    }
}
