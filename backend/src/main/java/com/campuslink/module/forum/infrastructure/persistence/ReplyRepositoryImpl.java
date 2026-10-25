package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.MyReplyRow;
import com.campuslink.module.forum.domain.model.PublicReplyRow;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.ReplyStatus;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 适配器：ReplyRepository 端口的 MyBatis-Plus 实现 */
@Repository
@RequiredArgsConstructor
public class ReplyRepositoryImpl implements ReplyRepository {

    /** replies.status 与 posts.status 同一词表；过滤条件取自 {@link ReplyStatus}，避免字面量存两份 */
    private static final String STATUS_PUBLISHED = ReplyStatus.PUBLISHED.name();

    private final ReplyMapper replyMapper;

    @Override
    public PageResult<Reply> findPageByPostId(Long postId, Collection<Long> excludedAuthorIds,
                                              int page, int size) {
        LambdaQueryWrapper<ReplyDO> query = new LambdaQueryWrapper<ReplyDO>()
                .eq(ReplyDO::getPostId, postId)
                .eq(ReplyDO::getStatus, STATUS_PUBLISHED)
                .eq(ReplyDO::getIsDeleted, false);
        // 拉黑过滤在 SQL 侧完成：total 与楼层列表同源（CR-129）
        if (excludedAuthorIds != null && !excludedAuthorIds.isEmpty()) {
            query.notIn(ReplyDO::getAuthorId, excludedAuthorIds);
        }
        query.orderByDesc(ReplyDO::getIsAccepted)
                .orderByAsc(ReplyDO::getFloorNo);
        IPage<ReplyDO> result = replyMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(ReplyConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public PageResult<MyReplyRow> findPageByAuthor(Long authorId, int page, int size) {
        // 父帖可见性与父帖标题在同一条 SQL 里判定（ReplyMapper#findPageByAuthor）：
        // 内存拼两次查询会让 total 与过滤条件不同源，翻页就会出现"少了但计数还在"
        IPage<MyReplyDO> result = replyMapper.findPageByAuthor(new Page<>(page, size), authorId);
        return new PageResult<>(result.getRecords().stream()
                .map(d -> new MyReplyRow(d.getId(), d.getPostId(), d.getPostTitle(), d.getFloorNo(),
                        d.getContentMd(), ReplyStatus.valueOf(d.getStatus()), d.getCreatedAt()))
                .toList(), result.getTotal(), page, size);
    }

    @Override
    public PageResult<PublicReplyRow> findPublicPageByAuthor(Long authorId, int page, int size) {
        // 与本人视角那条一样：楼层与父帖的可见性在同一条 SQL 判定，total 与条件同源（ReplyMapper#findPublicPageByAuthor）
        IPage<PublicReplyDO> result = replyMapper.findPublicPageByAuthor(new Page<>(page, size), authorId);
        return new PageResult<>(result.getRecords().stream()
                .map(d -> new PublicReplyRow(d.getId(), d.getPostId(), d.getPostTitle(), d.getFloorNo(),
                        d.getContentMd(), d.getCreatedAt()))
                .toList(), result.getTotal(), page, size);
    }

    @Override
    public long countVisibleByAuthor(Long authorId) {
        // WHERE 与 findPublicPageByAuthor 同源：资料卡「回帖 N」与主页回帖时间线 total 必须一致
        return replyMapper.countVisibleByAuthor(authorId);
    }

    @Override
    public long sumVisibleLikeCountByAuthor(Long authorId) {
        return replyMapper.sumVisibleLikeCountByAuthor(authorId);
    }

    @Override
    public long countAcceptedVisibleByAuthor(Long authorId) {
        return replyMapper.countAcceptedVisibleByAuthor(authorId);
    }

    @Override
    public Optional<Reply> findVisibleById(Long id) {
        // 与 findPageByPostId 同源的双条件：已下架（status=REMOVED）与已删除都不应对写用例暴露（CR-060 / BUG-002）
        return Optional.ofNullable(replyMapper.selectOne(new LambdaQueryWrapper<ReplyDO>()
                        .eq(ReplyDO::getId, id)
                        .eq(ReplyDO::getStatus, STATUS_PUBLISHED)
                        .eq(ReplyDO::getIsDeleted, false)))
                .map(ReplyConverter::toDomain);
    }

    @Override
    public List<Reply> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return replyMapper.selectList(new LambdaQueryWrapper<ReplyDO>()
                        .in(ReplyDO::getId, ids)
                        .eq(ReplyDO::getIsDeleted, false)).stream()
                .map(ReplyConverter::toDomain)
                .toList();
    }

    @Override
    public List<Reply> findVisibleByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 与 findVisibleById 同源的双条件：引用摘要渲染给读者，已下架 / 已删除的楼层不出现（CR-085）
        return replyMapper.selectList(new LambdaQueryWrapper<ReplyDO>()
                        .in(ReplyDO::getId, ids)
                        .eq(ReplyDO::getStatus, STATUS_PUBLISHED)
                        .eq(ReplyDO::getIsDeleted, false)).stream()
                .map(ReplyConverter::toDomain)
                .toList();
    }

    @Override
    public Optional<ReplyStatus> findStatusById(Long id) {
        // 只读 status 一列：已删行按"不存在"处理（与 findById 的墓碑约定同源），已下架行**必须**能读到
        ReplyDO row = replyMapper.selectOne(new LambdaQueryWrapper<ReplyDO>()
                .select(ReplyDO::getStatus)
                .eq(ReplyDO::getId, id)
                .eq(ReplyDO::getIsDeleted, false));
        return Optional.ofNullable(row).map(ReplyDO::getStatus).map(ReplyStatus::valueOf);
    }

    @Override
    public boolean updateStatus(Long replyId, ReplyStatus target, ReplyStatus expectedCurrent) {
        // 与帖子侧同款：条件带原状态，受影响行数即"这次是否真的改了"，并发后到方拿 false 而不重复记审计
        return replyMapper.update(null, new LambdaUpdateWrapper<ReplyDO>()
                .set(ReplyDO::getStatus, target.name())
                .eq(ReplyDO::getId, replyId)
                .eq(ReplyDO::getStatus, expectedCurrent.name())
                .eq(ReplyDO::getIsDeleted, false)) > 0;
    }

    @Override
    public void updateAcceptedFlags(Long postId, Long newlyAcceptedReplyId) {
        replyMapper.update(null, new LambdaUpdateWrapper<ReplyDO>()
                .set(ReplyDO::getIsAccepted, false)
                .eq(ReplyDO::getPostId, postId)
                .eq(ReplyDO::getIsAccepted, true));
        replyMapper.update(null, new LambdaUpdateWrapper<ReplyDO>()
                .set(ReplyDO::getIsAccepted, true)
                .eq(ReplyDO::getId, newlyAcceptedReplyId));
    }

    @Override
    public Reply save(Reply reply) {
        ReplyDO d = ReplyConverter.toDo(reply);
        replyMapper.insert(d);
        return ReplyConverter.toDomain(d);
    }

    @Override
    public int adjustLikeCount(Long replyId, int delta) {
        // 同 PostRepositoryImpl（BUG-008 / CR-117）：按符号取固定片段、减方向以 GREATEST 兜底
        replyMapper.update(null, new LambdaUpdateWrapper<ReplyDO>()
                .setSql(delta >= 0
                        ? "like_count = like_count + 1"
                        : "like_count = GREATEST(like_count - 1, 0)")
                .eq(ReplyDO::getId, replyId));
        return replyMapper.selectById(replyId).getLikeCount();
    }
}
