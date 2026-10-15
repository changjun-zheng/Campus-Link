package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.FollowApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.AuthorStats;
import com.campuslink.module.forum.application.cmd.ForumResults.MyPostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.MyReplyItem;
import com.campuslink.module.forum.application.cmd.ForumResults.PostBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.PublicReplyItem;
import com.campuslink.module.forum.application.cmd.ForumResults.QuotedBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;
import com.campuslink.module.forum.application.cmd.ForumResults.SimilarPostResult;
import com.campuslink.module.forum.domain.exception.BoardNotFoundException;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.FavoriteRepository;
import com.campuslink.module.forum.domain.gateway.LikeRepository;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.LikeTargetType;
import com.campuslink.module.forum.domain.model.MyReplyRow;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostSortOrder;
import com.campuslink.module.forum.domain.model.PublicReplyRow;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.SimilarPostRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论坛查询用例：版块列表 / 帖子列表 / 帖子详情 / 楼层列表。
 *
 * <p>作者昵称跨上下文取，**只调 account 的 application 服务**（ADR-012），一页一次批量查、不做 N+1（设计 §4.3）。
 * 版块是固定 6 行参考数据，一次取全后按 id 取用，不再逐帖查。
 */
@Service
@RequiredArgsConstructor
public class ForumQueryApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 列表摘要上限（设计 §3.2，服务端截断） */
    private static final int SUMMARY_MAX_CHARS = 120;
    /** 作者已注销等取不到昵称时的回落文案（设计 §4.3） */
    private static final String NICKNAME_FALLBACK = "已注销用户";
    /** 搜索关键词长度：下限对齐 ngram 分词（默认 ngram_token_size=2，单字分不出词），上限防超长串放大查询 */
    private static final int KEYWORD_MIN_CHARS = 2;
    private static final int KEYWORD_MAX_CHARS = 50;
    /** 搜索时间窗白名单（天）；不在白名单即 1001，不做静默归一 */
    private static final Set<Integer> SEARCH_TIME_WINDOWS = Set.of(7, 30, 90);
    /** 「关注」Feed 的作者数上限（CR-074 R2）：单校规模无忧，超限截断并在产品侧引导取关（本期不做） */
    private static final int FOLLOWING_FEED_AUTHOR_CAP = 500;

    /** 相似帖子标题长度上限（CR-077 Critical #1）：超过即 1001，与搜索关键词上限同理——防超长串放大全文检索 */
    private static final int SIMILAR_TITLE_MAX_CHARS = 100;

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final LikeRepository likeRepository;
    private final FavoriteRepository favoriteRepository;
    private final AccountApplicationService accountApplicationService;
    private final FollowApplicationService followApplicationService;
    private final MarkdownRenderer markdownRenderer;
    private final AppProperties appProperties;

    public List<Board> listBoards() {
        return boardRepository.findAllEnabled();
    }

    /**
     * 帖子列表：boardCode 缺省为全站；sort 缺省 {@code latest}（= 引入热榜之前完全一致的行为），
     * {@code hot} 走 hot_score 倒序（F-FORUM-003）；不存在的版块按资源不存在处理（设计 §3.2）。
     *
     * <p>sort 非法值 / 空串 → 1001，**不静默归一到 latest**——与同端点族 {@code days} 只受理 7/30/90 的口径一致：
     * 静默降级会让调用方以为拿到了热榜、实际拿到的是最新序，这种错法查不出来。
     */
    public PageResult<PostSummary> listPosts(String boardCode, String sort, int page, int size, Long viewerId) {
        PostSortOrder sortOrder = PostSortOrder.fromCode(sort)
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAM));
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        Long boardId = boardIdOf(boardCode);
        PageResult<Post> found = postRepository.findPage(boardId, sortOrder, currentPage, pageSize);
        return new PageResult<>(toSummaries(found.items(), viewerId), found.total(), currentPage, pageSize);
    }

    /**
     * 站内搜索（F-FORUM-008）：标题全文（ngram，中文 2 字起可命中）+ tags 冗余列 LIKE 兜底，
     * 按相关度 + 时间排序，可按版块 / 时间窗筛选；出参与帖子列表同口径（{@link PostSummary}）。
     * keyword 去首尾空白后长度须 2~50，days 只接受 7 / 30 / 90——不满足即 1001，不静默归一（与分页口径不同：
     * 分页越界只是翻页放大，关键词过短会退化成全表扫描且 ngram 分不出词）。
     */
    public PageResult<PostSummary> searchPosts(String keyword, String boardCode, Integer days, int page, int size,
                                               Long viewerId) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.length() < KEYWORD_MIN_CHARS || kw.length() > KEYWORD_MAX_CHARS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (days != null && !SEARCH_TIME_WINDOWS.contains(days)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        Long boardId = boardIdOf(boardCode);
        PageResult<Post> found = postRepository.search(kw, boardId, days, currentPage, pageSize);
        return new PageResult<>(toSummaries(found.items(), viewerId), found.total(), currentPage, pageSize);
    }

    /**
     * 帖子详情：contentHtml 为发布时渲染好的 HTML，请求时零渲染（ADR-005）。
     * viewerId 为 null（匿名）时 likedByMe / favoritedByMe 恒 false——详情端点保持公开，登录态回显是**附加**能力（F-FORUM-005）。
     */
    public PostDetail postDetail(Long postId, Long viewerId) {
        Post post = requireVisiblePost(postId);
        Board board = boardsById().get(post.getBoardId());
        String nickname = accountApplicationService.nicknamesOf(List.of(post.getAuthorId()))
                .getOrDefault(post.getAuthorId(), NICKNAME_FALLBACK);
        boolean likedByMe = viewerId != null
                && likeRepository.findLikedTargetIds(viewerId, LikeTargetType.POST, List.of(postId)).contains(postId);
        boolean favoritedByMe = viewerId != null
                && favoriteRepository.findFavoritedPostIds(viewerId, List.of(postId)).contains(postId);
        return new PostDetail(post.getId(),
                board == null ? null : board.getCode(),
                board == null ? null : board.getName(),
                board == null ? null : board.getType().name(),
                post.getTitle(), post.getContentHtml(), post.getAuthorId(), nickname,
                post.getReplyCount(), post.getLikeCount(), post.getViewCount(), post.getTags(), post.isAccepted(),
                likedByMe, favoritedByMe, post.getCreatedAt());
    }

    /**
     * 楼层列表：帖子不可读则不暴露其楼层；viewerId 为 null（匿名）时 likedByMe 恒 false（F-FORUM-005）。
     *
     * <p>引用摘要（F-009 / CR-085）：被引楼层一页一次批量取（{@code findVisibleByIds}，可见性口径与
     * 单条版同源），excerpt 走 {@code toPlainSummary} 120 字；被引楼层不可见（下架 / 已删除）即不渲染。
     * 引用作者的昵称**并入同一批** {@code nicknamesOf}（楼层作者 + 被引楼层作者一次取齐），不做 N+1。
     */
    public PageResult<ReplyItem> listReplies(Long postId, int page, int size, Long viewerId) {
        requireVisiblePost(postId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<Reply> found = replyRepository.findPageByPostId(postId, currentPage, pageSize);
        Map<Long, Reply> quotedVisible = replyRepository.findVisibleByIds(
                        found.items().stream().map(Reply::getQuotedReplyId)
                                .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Reply::getId, Function.identity()));
        Set<Long> authorIds = new LinkedHashSet<>(found.items().stream().map(Reply::getAuthorId).toList());
        quotedVisible.values().forEach(quoted -> authorIds.add(quoted.getAuthorId()));
        Map<Long, String> nicknames = accountApplicationService.nicknamesOf(List.copyOf(authorIds));
        Set<Long> likedReplyIds = viewerId == null ? Set.of()
                : likeRepository.findLikedTargetIds(viewerId, LikeTargetType.REPLY,
                        found.items().stream().map(Reply::getId).toList());
        List<ReplyItem> items = found.items().stream()
                .map(reply -> new ReplyItem(reply.getId(), reply.getFloorNo(), reply.getContentHtml(),
                        reply.getAuthorId(), nicknames.getOrDefault(reply.getAuthorId(), NICKNAME_FALLBACK),
                        reply.isAccepted(), reply.getLikeCount(),
                        likedReplyIds.contains(reply.getId()),
                        quotedBriefOf(reply, quotedVisible, nicknames), reply.getCreatedAt()))
                .toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /**
     * 被引用楼层摘要（F-009 / CR-085）：目标不在可见集合（不存在 / 已下架 / 已删除）→ null，
     * 前端不渲染引用块——"内容不可见不外泄"同 CR-066 通知标题回落的判定思路。
     */
    private QuotedBrief quotedBriefOf(Reply reply, Map<Long, Reply> quotedVisible, Map<Long, String> nicknames) {
        if (reply.getQuotedReplyId() == null) {
            return null;
        }
        Reply target = quotedVisible.get(reply.getQuotedReplyId());
        if (target == null) {
            return null;
        }
        return new QuotedBrief(target.getId(), target.getFloorNo(),
                nicknames.getOrDefault(target.getAuthorId(), NICKNAME_FALLBACK),
                markdownRenderer.toPlainSummary(target.getContentMd(), SUMMARY_MAX_CHARS));
    }

    /**
     * 我的收藏（F-FORUM-005）：按收藏时间倒序分页，出参复用 {@link PostSummary}（列表口径一致）。
     * 收藏行为本人可见的私有列表——调用方（Controller）已要求登录。
     */
    public PageResult<PostSummary> listMyFavorites(long userId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<Post> found = favoriteRepository.findFavoritePosts(userId, currentPage, pageSize);
        return new PageResult<>(toSummaries(found.items(), userId), found.total(), currentPage, pageSize);
    }

    /**
     * 我的帖子（F-ACC-007b）：本人发帖时间倒序，出参是**只面向作者**的 {@link MyPostSummary}
     * （带 status，故不复用与全站共享的 {@link PostSummary}）。
     *
     * <p>可见性口径写在仓储侧（{@code PostRepository#findPageByAuthor}：排墓碑、不过滤 status），
     * 本方法不再二次过滤——{@code total} 与 SQL 条件同源是这条链路的硬判据。
     * 与 {@link #listMyFavorites} 的区别也在这：收藏页的驱动分页行是 {@code favorites}，剔除必然致偏；
     * 这里驱动分页的就是 {@code posts} 本身，没有理由不做同源。
     *
     * <p>不查作者昵称：条目本来就是本人的，跨上下文那一次批量取昵称在此是纯浪费。
     */
    public PageResult<MyPostSummary> listMyPosts(long userId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<Post> found = postRepository.findPageByAuthor(userId, currentPage, pageSize);
        Map<Long, Board> boardsById = boardsById();
        List<MyPostSummary> items = found.items().stream()
                .map(post -> {
                    Board board = boardsById.get(post.getBoardId());
                    return new MyPostSummary(post.getId(), board == null ? null : board.getCode(), post.getTitle(),
                            markdownRenderer.toPlainSummary(post.getContentMd(), SUMMARY_MAX_CHARS),
                            post.getStatus().name(), post.getCreatedAt());
                })
                .toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /**
     * 我的回帖（F-ACC-007c）：本人楼层时间倒序 + 父帖定位。父帖不可见的整条不出现——
     * 该判定在 SQL 里与父帖标题一次做完（{@code ReplyRepository#findPageByAuthor}），
     * 所以"列出来了却点不进"在这条链路上结构上不可能发生。
     */
    public PageResult<MyReplyItem> listMyReplies(long userId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<MyReplyRow> found = replyRepository.findPageByAuthor(userId, currentPage, pageSize);
        List<MyReplyItem> items = found.items().stream()
                .map(row -> new MyReplyItem(row.id(), row.postId(), row.postTitle(), row.floorNo(),
                        markdownRenderer.toPlainSummary(row.contentMd(), SUMMARY_MAX_CHARS),
                        row.status().name(), row.createdAt()))
                .toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /**
     * 「关注」Feed（F-SOC-002 提前 / CR-074）：本人关注作者的时间序帖子，出参与全站列表同口径。
     * 未关注任何人 → 空页（前端给「去关注」空态）；作者数超上限截断（R2，实施方案 §7）。
     * 跨上下文经 account 的 application 拿关注 id 列表（ADR-012，方向与昵称解析同路）。
     */
    public PageResult<PostSummary> listFollowingPosts(long userId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        List<Long> authorIds = followApplicationService.followingIds(userId);
        if (authorIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, currentPage, pageSize);
        }
        PageResult<Post> found = postRepository.findPageByAuthors(
                authorIds.size() > FOLLOWING_FEED_AUTHOR_CAP ? authorIds.subList(0, FOLLOWING_FEED_AUTHOR_CAP) : authorIds,
                currentPage, pageSize);
        return new PageResult<>(toSummaries(found.items(), userId), found.total(), currentPage, pageSize);
    }

    /**
     * 他人主页的公开帖子分页（{@code GET /api/v1/users/{id}/posts}）：可见性口径与全站列表
     * **完全一致**（{@code status='PUBLISHED' AND is_deleted=0}），出参也复用 {@link PostSummary}——
     * 于是主页那份时间线与首页 / 版块 / 搜索里的同一条帖子长得一样，前端得以复用同一个列表项组件。
     *
     * <p>⚠️ 与 {@link #listMyPosts} 的口径**相反**，别弄混：那条是作者本人视角（下架的也要露出、带 status），
     * 本条是路人视角（下架的一律不出现）。两者分别走仓储的 {@code findPageByAuthor}（单数）与
     * {@code findPageByAuthors}（复数），接错一个就是把平台已处置的内容重新公开。
     *
     * <p>复用 {@code findPageByAuthors}（单元素集合）而不新写一条 SQL：它的 WHERE 与
     * {@code countVisibleByAuthor} 共用同一个构造点，资料卡的「帖子 N」与这份列表因此同源。
     *
     * <p>作者存在性在本方法里过一道门（而不是交给控制器）：不存在 / 已注销 → 2007 / 404，
     * 不对外区分。跳上下文只调 account 的 application（ADR-012 / 守护测试 G4）。
     */
    public PageResult<PostSummary> listVisiblePostsByAuthor(long authorId, int page, int size, Long viewerId) {
        accountApplicationService.publicAccountOf(authorId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<Post> found = postRepository.findPageByAuthors(List.of(authorId), currentPage, pageSize);
        return new PageResult<>(toSummaries(found.items(), viewerId), found.total(), currentPage, pageSize);
    }

    /**
     * 作者可见帖子数（他人主页资料卡的「帖子 N」）。
     *
     * <p>不做作者存在性校验：唯一调用方（account 的 {@code UserProfileApplicationService}）已先过一道
     * 同一口径的门，在这里再查一次 users 是纯浪费。把它接到其它调用方时需自己补上那道门。
     */
    public long countVisiblePostsByAuthor(long authorId) {
        return postRepository.countVisibleByAuthor(authorId);
    }

    /**
     * 作者三个对外计数（他人主页资料卡，CR-104）：**一次**取齐，不让 account 侧为三个数字来回调三次——
     * 三次调用等于允许「帖子 12 / 获赞 30 / 被采纳 3」来自三个不同时刻，同一张卡上的数会对不上。
     *
     * <p>三个口径全部限定「可见内容」，逐条与 {@code AuthorStats} 的 javadoc 对应；
     * {@code likeCount} 是 D-1 发起人定下的「帖子获赞 + 楼层获赞之和」，两次 SUM 相加，
     * 其中楼层侧要 JOIN 父帖（下架帖的楼层赞不计入）。
     *
     * <p>与 {@link #countVisiblePostsByAuthor} 一样**不做作者存在性校验**：唯一调用方
     * （account 的 {@code UserProfileApplicationService}）已先过同一口径的门。
     * 保留 {@link #countVisiblePostsByAuthor} 不删：它是本方法里「帖子 N」的单一取数点，
     * 也是既有单测断言该口径的那把把手。
     */
    public AuthorStats authorStats(long authorId) {
        return new AuthorStats(
                postRepository.countVisibleByAuthor(authorId),
                postRepository.sumVisibleLikeCountByAuthor(authorId)
                        + replyRepository.sumVisibleLikeCountByAuthor(authorId),
                replyRepository.countAcceptedVisibleByAuthor(authorId));
    }

    /**
     * 他人主页的公开楼层时间线（{@code GET /api/v1/users/{id}/replies}，CR-104）：
     * 楼层与父帖**双可见**（{@code status='PUBLISHED' AND is_deleted=0}）才出现，时间倒序分页。
     *
     * <p>⚠️ 与 {@link #listMyReplies} 的口径**相反**，别弄混：那条是作者本人视角（自己哪一层被下架要看得见、
     * 故带 {@code status}），本条是路人视角（被处置的楼层不得再次公开，故出参里没有 {@code status}）。
     * 两条分别走仓储的 {@code findPageByAuthor} 与 {@code findPublicPageByAuthor}，
     * 接错一个就是把平台已处置内容重新公开——这也是本方法不复用 {@code MyReplyItem} 的原因。
     *
     * <p>作者存在性在本方法里过一道门（同 {@link #listVisiblePostsByAuthor}）：不存在 / 已注销 → 2007 / 404，
     * 对外不区分。
     */
    public PageResult<PublicReplyItem> listPublicRepliesByAuthor(long authorId, int page, int size) {
        accountApplicationService.publicAccountOf(authorId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageResult<PublicReplyRow> found = replyRepository.findPublicPageByAuthor(authorId, currentPage, pageSize);
        List<PublicReplyItem> items = found.items().stream()
                .map(row -> new PublicReplyItem(row.id(), row.postId(), row.postTitle(), row.floorNo(),
                        markdownRenderer.toPlainSummary(row.contentMd(), SUMMARY_MAX_CHARS), row.createdAt()))
                .toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /**
     * 帖子标题批量读（F-SOC-001 通知读时组装）：只回**可见**帖子的标题，读不到的 id 不出现在结果中。
     *
     * <p>过滤条件与 {@link #postDetail} 同源（{@link Post#isVisible()}）——CR-066 之前这里只排墓碑行
     * （{@code findByIds} 的 {@code is_deleted}）不看 status，于是"帖子被管理员下架"后**标题仍留在
     * 别人的通知里**、点进去才 404。标题属内容本身，内容不可见时不该继续外泄。
     *
     * <p>本方法与 {@link #replyBriefsOf} 是**只读摘要出口**，存在的理由是同一条架构规则：
     * notification 上下文不得引用 forum 的 domain 类型（守护测试 G4），跨上下文只能拿到 application 层的载体。
     */
    public Map<Long, PostBrief> postBriefsOf(Collection<Long> postIds) {
        return postRepository.findByIds(postIds).stream()
                .filter(Post::isVisible)
                .collect(Collectors.toMap(Post::getId, post -> new PostBrief(post.getTitle())));
    }

    /**
     * 楼层归属批量读（同上）：回所属帖子与楼层号，供通知条目跳转定位。
     *
     * <p>与 {@link #postBriefsOf} 的差别是刻意的：这里**不看 status**（见
     * {@code ReplyRepository#findByIds}），因为一条已下架楼层仍需要"在第几楼"这个落点；
     * 而条目能否点击由帖子侧摘要是否命中决定（帖子下架 / 已删 → 整条不可点）。
     */
    public Map<Long, ReplyBrief> replyBriefsOf(Collection<Long> replyIds) {
        return replyRepository.findByIds(replyIds).stream()
                .collect(Collectors.toMap(Reply::getId, reply -> new ReplyBrief(reply.getPostId(), reply.getFloorNo())));
    }

    /**
     * Similar-post recommendation (publish-time assist, silent degradation):
     * returns an empty list when the feature is disabled, the title is too short, or the Q&A board is missing.
     * Title is trimmed before length checks; exceeding {@value #SIMILAR_TITLE_MAX_CHARS} chars throws 1001.
     *
     * <p>CR-077 Critical #1: trim + explicit max-length validation replaces the non-functional {@code @Size} on
     * {@code @RequestParam} (which would trigger an unhandled {@code HandlerMethodValidationException} → 500).
     * <p>CR-077 Warning #7: no longer calls {@code toSummaries()} — constructs the narrow
     * {@link SimilarPostResult} directly from the board already fetched and the row data.
     */
    public List<SimilarPostResult> findSimilarPosts(String title, Long currentUserId) {
        AppProperties.Ai.Similar cfg = appProperties.getAi().getSimilar();
        if (!cfg.isEnabled()) {
            return List.of();
        }
        String t = (title == null) ? "" : title.trim();
        if (t.length() > SIMILAR_TITLE_MAX_CHARS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (t.length() < cfg.getMinTitleLength()) {
            return List.of();
        }
        Board board = boardRepository.findByCode("qna").orElse(null);
        if (board == null) {
            return List.of();
        }
        List<SimilarPostRow> rows = postRepository.findSimilar(t, board.getId(), currentUserId, cfg.getMaxResults());
        return rows.stream()
                .map(row -> new SimilarPostResult(row.id(), row.title(), board.getCode(), board.getName(),
                        row.replyCount(), row.accepted(), row.createdAt()))
                .toList();
    }

    /** 不可读（不存在 / 已删除 / 非 PUBLISHED）统一 404 / 3001，三种情况不对外区分——防按 id 探测 */
    private Post requireVisiblePost(Long postId) {
        return postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
    }

    private Map<Long, Board> boardsById() {
        return boardRepository.findAllEnabled().stream()
                .collect(Collectors.toMap(Board::getId, Function.identity()));
    }

    /** boardCode 空 / 空白 → 全站（null）；查不到该版块 → 3001，与列表口径一致 */
    private Long boardIdOf(String boardCode) {
        if (boardCode == null || boardCode.isBlank()) {
            return null;
        }
        return boardRepository.findByCode(boardCode)
                .orElseThrow(BoardNotFoundException::new)
                .getId();
    }

    /**
     * 列表 / 收藏 / 搜索 / 关注 Feed / 他人主页五处共用的摘要映射：版块名、作者昵称、已赞集合**各自一页一次批量解析**
     * （设计 §4.3，不做 N+1）。
     *
     * <p>{@code viewerId} 为 null（匿名）时 likedByMe 恒 false 且**一次仓储都不调**——与 {@link #postDetail}
     * 同一口径（F-FORUM-005：公开可读、登录态回显是附加能力）。[CR-080] 新增此参数，是为了让列表能就地显示
     * "我赞没赞过"，而不必为每行发一次请求。
     */
    private List<PostSummary> toSummaries(List<Post> posts, Long viewerId) {
        Map<Long, Board> boardsById = boardsById();
        Map<Long, String> nicknames = accountApplicationService.nicknamesOf(
                posts.stream().map(Post::getAuthorId).toList());
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> likedIds = viewerId == null || postIds.isEmpty()
                ? Set.of()
                : likeRepository.findLikedTargetIds(viewerId, LikeTargetType.POST, postIds);
        return posts.stream()
                .map(post -> {
                    Board board = boardsById.get(post.getBoardId());
                    return new PostSummary(post.getId(),
                            board == null ? null : board.getCode(),
                            board == null ? null : board.getName(),
                            post.getTitle(),
                            nicknames.getOrDefault(post.getAuthorId(), NICKNAME_FALLBACK),
                            post.getReplyCount(), post.getLikeCount(), post.getViewCount(), post.getCoverUrl(), post.getTags(),
                            markdownRenderer.toPlainSummary(post.getContentMd(), SUMMARY_MAX_CHARS),
                            post.getCreatedAt(), post.isAccepted(), likedIds.contains(post.getId()));
                })
                .toList();
    }

    /** page 归一到 ≥1；size 归一到 1~100（设计 §3：默认 20、上限 100）——不报错，避免 size 被用来放大查询 */
    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
