package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.model.HotScoreInput;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostSortOrder;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.SimilarPostRow;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 适配器：PostRepository 端口的 MyBatis-Plus 实现 */
@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper postMapper;

    @Override
    public PageResult<Post> findPage(Long boardId, PostSortOrder sort, Collection<Long> excludedAuthorIds,
                                     int page, int size) {
        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<>();
        if (boardId != null) {
            query.eq(PostDO::getBoardId, boardId);
        }
        query.eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .eq(PostDO::getIsDeleted, false);
        // 拉黑过滤在 SQL 侧完成：total 与查询条件同源，翻页不会出现"少了但计数还在"（CR-129）。
        // 匿名帖例外（CR-139 / D4）：按真实作者过滤会让"看不到某人的匿名帖"变成身份侧信道，
        // 故条件写成 (is_anonymous = 1 OR author_id NOT IN (...))
        if (excludedAuthorIds != null && !excludedAuthorIds.isEmpty()) {
            query.and(w -> w.eq(PostDO::getIsAnonymous, true)
                    .or().notIn(PostDO::getAuthorId, excludedAuthorIds));
        }
        if (sort == PostSortOrder.HOT) {
            query.orderByDesc(PostDO::getHotScore);
        }
        query.orderByDesc(PostDO::getCreatedAt)
                .orderByDesc(PostDO::getId);
        IPage<PostDO> result = postMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(PostConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public PageResult<Post> findPageByAuthor(Long authorId, int page, int size) {
        // 与 findPage 的唯一差别就是不写 status 条件：作者要能看到"我哪一帖被下架了"（F-ACC-007b）；
        // 墓碑列照写——自删对作者本人也不出现。total 与这两个条件同源（不在内存里剔）。
        IPage<PostDO> result = postMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getAuthorId, authorId)
                .eq(PostDO::getIsDeleted, false)
                .orderByDesc(PostDO::getCreatedAt)
                .orderByDesc(PostDO::getId));
        return new PageResult<>(result.getRecords().stream().map(PostConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public PageResult<Post> findPageByAuthors(Collection<Long> authorIds, Collection<Long> excludedAuthorIds,
                                              int page, int size) {
        if (authorIds == null || authorIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, size);
        }
        // 匿名帖不进他人主页时间线 / 关注 Feed（CR-139 / D3）：这两条链路的驱动条件就是 author_id，
        // 列出来等于自曝；visibleQuery() 已统一排除
        LambdaQueryWrapper<PostDO> query = visibleQuery()
                .in(PostDO::getAuthorId, authorIds);
        if (excludedAuthorIds != null && !excludedAuthorIds.isEmpty()) {
            query.notIn(PostDO::getAuthorId, excludedAuthorIds);
        }
        IPage<PostDO> result = postMapper.selectPage(new Page<>(page, size), query
                .orderByDesc(PostDO::getCreatedAt)
                .orderByDesc(PostDO::getId));
        return new PageResult<>(result.getRecords().stream().map(PostConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public long countVisibleByAuthor(Long authorId) {
        // 与 findPageByAuthors 共用 visibleQuery()：他人主页资料卡的「帖子 N」与它下方那份列表因此不可能对不上
        return postMapper.selectCount(visibleQuery().eq(PostDO::getAuthorId, authorId));
    }

    @Override
    public long sumVisibleLikeCountByAuthor(Long authorId) {
        // SQL 字面量与 visibleQuery() 三条件逐字相同，同源性由用例侧「下架帖的赞不计入」守住（见 PostMapper javadoc）
        return postMapper.sumVisibleLikeCountByAuthor(authorId);
    }

    /**
     * 可见帖子条件（{@code status='PUBLISHED' AND is_deleted=0}）的**单一构造点**：
     * {@link #findPageByAuthors}（列表）与 {@link #countVisibleByAuthor}（计数）共用，
     * 使端口 javadoc 里那句「逐字同源」由代码而不是由注释保证。
     *
     * <p>刻意不抽给 {@link #findPage}：那条还带版块与两种排序的分支，合并后反而看不清主干；
     * 而同源性有硬要求的只有「同一个作者的计数 vs 列表」这一对。
     */
    private static LambdaQueryWrapper<PostDO> visibleQuery() {
        return new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .eq(PostDO::getIsDeleted, false)
                // 匿名帖不计入作者维度的列表与公开统计（CR-139 / D3）
                .eq(PostDO::getIsAnonymous, false);
    }

    @Override
    public PageResult<Post> search(String keyword, Long boardId, Integer days, Collection<Long> excludedAuthorIds,
                                   int page, int size) {
        IPage<PostDO> result = postMapper.search(new Page<>(page, size), keyword, boardId, days, excludedAuthorIds);
        return new PageResult<>(result.getRecords().stream().map(PostConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(postMapper.selectOne(new LambdaQueryWrapper<PostDO>()
                        .eq(PostDO::getId, id)
                        .eq(PostDO::getIsDeleted, false)))
                .map(PostConverter::toDomain);
    }

    @Override
    public List<Post> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return postMapper.selectList(new LambdaQueryWrapper<PostDO>()
                        .in(PostDO::getId, ids)
                        .eq(PostDO::getIsDeleted, false)).stream()
                .map(PostConverter::toDomain)
                .toList();
    }

    @Override
    public Post save(Post post) {
        PostDO d = PostConverter.toDo(post);
        postMapper.insert(d);
        return PostConverter.toDomain(d);
    }

    @Override
    public boolean revise(Long postId, String title, String contentMd, String contentHtml, String coverUrl,
                          List<String> tags) {
        // 条件带 is_deleted=0 + status=PUBLISHED：受影响行数即"这次是否真的改到了可见帖子"，
        // 并发下架 / 删除时拿 false，调用方返回 3001 而不是假装成功（同 markDeleted 的纪律）。
        // 只 SET 编辑相关列——计数与 hot_score 在读取后被并发推进，整行回写会抹掉它们。
        return postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getTitle, title)
                .set(PostDO::getContentMd, contentMd)
                .set(PostDO::getContentHtml, contentHtml)
                .set(PostDO::getCoverUrl, coverUrl)
                .set(PostDO::getTags, tags == null || tags.isEmpty() ? null : String.join(",", tags))
                .eq(PostDO::getId, postId)
                .eq(PostDO::getIsDeleted, false)
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())) > 0;
    }

    @Override
    public void incrementViewCount(Long postId) {
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .eq(PostDO::getId, postId)
                .setSql("view_count = view_count + 1"));
    }

    @Override
    public int incrementReplyCountAndGet(Long postId) {
        // 行锁先行的 UPDATE：同一帖子的并发回帖在此串行化；随后读回的值即本次 floor_no（设计 §4.1）
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .setSql("reply_count = reply_count + 1")
                .eq(PostDO::getId, postId));
        return postMapper.selectById(postId).getReplyCount();
    }

    @Override
    public void updateAcceptedReply(Long postId, Long replyId) {
        // 行锁先行的 UPDATE：并发采纳在此串行化，后写覆盖前写即"可更换"语义（F-QA-001）
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getIsAccepted, true)
                .set(PostDO::getAcceptedReplyId, replyId)
                .eq(PostDO::getId, postId));
    }

    @Override
    public boolean markDeleted(Long postId) {
        // 条件带 is_deleted=0：受影响行数即"这次是否真的删掉了它"，并发二次删除拿到 false 而不重复记审计
        return postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getIsDeleted, true)
                .eq(PostDO::getId, postId)
                .eq(PostDO::getIsDeleted, false)) > 0;
    }

    @Override
    public boolean updateStatus(Long postId, PostStatus target, PostStatus expectedCurrent) {
        // 条件带原状态：受影响行数即"这次是否真的改了状态"，并发下后到的一方拿 false 而不重复记审计
        return postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getStatus, target.name())
                .eq(PostDO::getId, postId)
                .eq(PostDO::getStatus, expectedCurrent.name())) > 0;
    }

    @Override
    public int adjustLikeCount(Long postId, int delta) {
        // 计数增减只按符号取固定片段（BUG-008 / CR-117）：delta 不进 SQL，安全约束不再藏在调用约定里；
        // 减方向以 GREATEST 兜底，计数不会降到 0 以下。
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .setSql(delta >= 0
                        ? "like_count = like_count + 1"
                        : "like_count = GREATEST(like_count - 1, 0)")
                .eq(PostDO::getId, postId));
        return postMapper.selectById(postId).getLikeCount();
    }

    @Override
    public int adjustFavoriteCount(Long postId, int delta) {
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .setSql(delta >= 0
                        ? "favorite_count = favorite_count + 1"
                        : "favorite_count = GREATEST(favorite_count - 1, 0)")
                .eq(PostDO::getId, postId));
        return postMapper.selectById(postId).getFavoriteCount();
    }

    @Override
    public List<HotScoreInput> findHotCandidates(Instant since, int limit, Long afterId) {
        LambdaQueryWrapper<PostDO> query = new LambdaQueryWrapper<PostDO>()
                .select(PostDO::getId, PostDO::getReplyCount, PostDO::getLikeCount,
                        PostDO::getFavoriteCount, PostDO::getCreatedAt)
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .eq(PostDO::getIsDeleted, false)
                .ge(PostDO::getCreatedAt, since)
                .orderByAsc(PostDO::getId);
        if (afterId != null) {
            query.gt(PostDO::getId, afterId);
        }
        // searchCount=false：游标分批只需要这一批的数据，总数由调用方按"取满即继续"推进
        return postMapper.selectPage(new Page<>(1, limit, false), query).getRecords().stream()
                .map(d -> new HotScoreInput(d.getId(), d.getReplyCount(), d.getLikeCount(),
                        d.getFavoriteCount(), d.getCreatedAt()))
                .toList();
    }

    @Override
    public void updateHotScore(Long postId, double score) {
        // 定向单列 UPDATE：绝不整行回写，否则会覆盖刷新期间并发变化的三个互动计数
        postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getHotScore, score)
                .eq(PostDO::getId, postId));
    }

    @Override
    public int resetHotScoresBefore(Instant since) {
        // 只碰“已经不是候选”且分数非 0 的行：窗口外的老帖、已删除、非 PUBLISHED
        return postMapper.update(null, new LambdaUpdateWrapper<PostDO>()
                .set(PostDO::getHotScore, 0d)
                .ne(PostDO::getHotScore, 0d)
                .and(notCandidate -> notCandidate
                        .eq(PostDO::getIsDeleted, true)
                        .or().ne(PostDO::getStatus, PostStatus.PUBLISHED.name())
                        .or().lt(PostDO::getCreatedAt, since)));
    }

    @Override
    public List<SimilarPostRow> findSimilar(String title, Long boardId, Long excludeAuthorId, int limit) {
        return postMapper.findSimilar(title, boardId, excludeAuthorId, limit).stream()
                .map(d -> new SimilarPostRow(d.getId(), d.getBoardId(), d.getAuthorId(), d.getTitle(),
                        d.getReplyCount() == null ? 0 : d.getReplyCount(),
                        Boolean.TRUE.equals(d.getIsAccepted()), d.getCreatedAt()))
                .toList();
    }
}
