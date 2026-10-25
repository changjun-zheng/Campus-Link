package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.MyReplyRow;
import com.campuslink.module.forum.domain.model.PublicReplyRow;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.ReplyStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 回复仓储端口（端口定义在 domain、实现在 infrastructure，DIP） */
public interface ReplyRepository {

    /** 某帖的楼层分页：固定 {@code status='PUBLISHED' AND is_deleted=0}，按 is_accepted DESC、floor_no ASC（F-QA-001 最佳答案置顶，设计 §3.5） */
    PageResult<Reply> findPageByPostId(Long postId, Collection<Long> excludedAuthorIds,
                                       Collection<Long> excludedReplyIds, int page, int size);

    /**
     * "我的回帖"分页（F-ACC-007c）：本人楼层时间倒序，**父帖不可见即整条不出现**
     * （父帖 {@code is_deleted=1} 或 {@code status<>'PUBLISHED'} 都算不可见），
     * 楼层自身的 {@code status} 不过滤、随载体返回（作者要能看到"我哪一层被下架"）。
     *
     * <p>返回 {@link MyReplyRow} 而非 {@link Reply}：聚合刻意不带 status，且父帖标题必须与父帖的
     * 可见性判定在**同一条 SQL** 里完成——两次查询在内存拼使 {@code total} 与条件不同源。
     * 实现见 {@code ReplyMapper#findPageByAuthor}。
     *
     * <p>调用方约束同 {@code PostRepository#findPageByAuthor}：id 只能取自令牌，不接受外部传入。
     */
    PageResult<MyReplyRow> findPageByAuthor(Long authorId, int page, int size);

    /**
     * 他人主页的公开楼层分页（路人视角，CR-104）：与 {@link #findPageByAuthor} 的**唯一**差别是多一道
     * 楼层自身的可见性过滤——{@code r.status='PUBLISHED'}，父帖侧条件（未删 + PUBLISHED）逐字相同。
     *
     * <p>两条分开写而不是给本人视角那条加布尔开关：开关参数会让"哪一条被下架的楼层该露给谁"
     * 变成调用方的记忆题，而本仓对已处置内容的收口判据一直是**读侧结构上不可能漏**（见 {@code findVisibleById}）。
     *
     * <p>返回 {@link PublicReplyRow}（无 {@code status}）而非 {@code MyReplyRow}：见该载体的 javadoc——
     * 公开出参带 {@code status} 等于向匿名访客下发处置事实。
     */
    PageResult<PublicReplyRow> findPublicPageByAuthor(Long authorId, int page, int size);

    /**
     * 作者**可见楼层**的获赞总数（获赞数 D-1 的第二半，CR-104）：{@code SUM(r.like_count)}，
     * WHERE 与 {@link #findPublicPageByAuthor} 同源（楼层与父帖双 {@code PUBLISHED} 且未删）。
     *
     * <p>只算可见楼层：一条被平台下架的回答继续替作者挣着赞，等于让已处置内容仍在产出对外信号。
     */
    long sumVisibleLikeCountByAuthor(Long authorId);

    /**
     * 作者**可见楼层**数（他人主页资料卡的「回帖 N」与声望计分，CR-138）：{@code COUNT(*)}，
     * WHERE 与 {@link #findPublicPageByAuthor} 逐字相同（楼层与父帖双可见），
     * 故资料卡上的「回帖 N」与主页下方回帖时间线的 total 同源、不会互相矛盾。
     *
     * <p>只算可见楼层：被下架 / 父帖不可见的楼层不计入，与获赞、被采纳两数同一纪律（D6）。
     */
    long countVisibleByAuthor(Long authorId);

    /**
     * 作者**被采纳为最佳答案**的楼层数（被采纳数，D-2 取回答侧，CR-104）：
     * {@code COUNT(*)} WHERE {@code r.is_accepted=1} 且楼层与父帖均可见（同上同源条件）。
     *
     * <p>{@code is_accepted} 由采纳用例与 {@link #updateAcceptedFlags} 单点维护（换采纳时旧位清零），
     * 故此计数与帖子详情上的「已采纳」标识同源；父帖可见性一并参与，是因为"题都被下了架、答案还挂着被采纳数"
     * 属同一类外泄。
     */
    long countAcceptedVisibleByAuthor(Long authorId);

    /**
     * 按 id 查**可见**的回复（{@code status='PUBLISHED' AND is_deleted=0}，与 {@link #findPageByPostId}
     * 的列表过滤**同源**）；不可见（已下架 / 已删除）一律按"不存在"处理。
     *
     * <p>供两条楼层写用例核验目标存在且可见：楼层点赞与采纳最佳答案（F-FORUM-005 / F-QA-001）。
     * 可见性守卫**只放在读侧这一处**，计数更新（{@link #adjustLikeCount}）不再重复表达同一条件；
     * {@code Reply} 聚合刻意不带 {@code status}（无业务规则消费它），故过滤条件下推至 SQL 适配器。
     */
    Optional<Reply> findVisibleById(Long id);

    /**
     * 批量按 id 查**可见**的回复（条件与 {@link #findVisibleById} 同源：{@code status='PUBLISHED' AND is_deleted=0}），
     * 供楼层列表组装引用摘要（F-009 / CR-085）：一页一次取齐被引楼层，不做 N+1。
     *
     * <p>与 {@link #findByIds} 的差别同单条版的口径：**引用摘要要渲染给读者，被下架 / 已删除的楼层
     * 一律不出现**（"内容不可见不外泄"）；未命中的 id 自然不在结果中，由调用方决定回落（quoted=null 不渲染）。
     */
    List<Reply> findVisibleByIds(Collection<Long> ids);

    /**
     * 批量按 id 查**未删除**的回复（只过滤 {@code is_deleted=0}，**不过滤 status**）；通知读时组装用（F-SOC-001），
     * 一页一次取齐所属帖与楼层号，不做 N+1。
     *
     * <p>与 {@link #findVisibleById} 的差别是刻意的：通知要能指出一条已下架楼层"在第几楼"，
     * 在此过滤 status 会让引用失去落点；写用例一律走 {@link #findVisibleById}。
     */
    List<Reply> findByIds(Collection<Long> ids);

    /**
     * 处置用例专用的**窄读**：只回该行的 {@code status}，行不存在或已删（{@code is_deleted=1}）时回空。
     *
     * <p>为什么单开一个方法而不复用 {@link #findVisibleById}：后者把"已下架"与"不存在"合并成"不可见"，
     * 而**恢复**一条已下架楼层必须先读出"它现在是 REMOVED"，否则无法与"这条根本不存在"区分。
     * {@code Reply} 聚合刻意不带 status（无业务规则消费它，见 {@link #findVisibleById} 注释），
     * 故本方法只回枚举值、不造半个聚合。
     */
    Optional<ReplyStatus> findStatusById(Long id);

    /**
     * 内容处置（F-SAFE-003 / CR-066）：定向 UPDATE {@code replies.status}，**条件带原状态**；
     * 语义、幂等纪律与"为什么不写 is_deleted"的取舍同 {@code PostRepository#updateStatus}。
     *
     * <p>条件里另加 {@code is_deleted=0}：已删楼层不该被"恢复成可见"——那是删除用例的墓碑，处置不越界。
     */
    boolean updateStatus(Long replyId, ReplyStatus target, ReplyStatus expectedCurrent);

    /**
     * 采纳标志切换（F-QA-001）：先清该帖旧的已采纳楼层，再把指定回复标为已采纳——两条 UPDATE
     * 由调用方事务包裹；并发采纳以 posts 行锁串行化（见 PostRepository#updateAcceptedReply）。
     */
    void updateAcceptedFlags(Long postId, Long newlyAcceptedReplyId);

    /** 新增回复（INSERT）；返回带数据库生成 id 与时间戳的聚合 */
    Reply save(Reply reply);

    /** 楼层点赞计数增减（F-FORUM-005，delta 为 ±1）；返回增减后的 like_count，供响应回显 */
    int adjustLikeCount(Long replyId, int delta);
}
