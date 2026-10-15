package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.HotScoreInput;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostSortOrder;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.SimilarPostRow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 帖子仓储端口（端口定义在 domain、实现在 infrastructure，DIP） */
public interface PostRepository {

    /**
     * 可见帖子分页：{@code boardId} 为 null 表示全站；固定 {@code status='PUBLISHED' AND is_deleted=0}，
     * 排序由 {@code sort} 决定（{@link PostSortOrder#LATEST} = created_at DESC，即引入热榜之前的行为；
     * {@link PostSortOrder#HOT} = hot_score DESC），两者都以 id DESC 兜底，避免同值时翻页错位。
     *
     * <p>与 {@link #findById} 不同，这里在 SQL 侧一并过滤 status——列表的 total 必须与查询条件同源，
     * 不能在内存里剔除后再算页数。
     */
    PageResult<Post> findPage(Long boardId, PostSortOrder sort, int page, int size);

    /**
     * "我的帖子"分页（F-ACC-007b）：本人发帖时间倒序，{@code author_id = ? AND is_deleted = 0}，
     * 排序 {@code created_at DESC, id DESC}（同值兜底防翻页错位，同 {@link #findPage} 口径）。
     *
     * <p>与 {@link #findPage} 的差别是刻意的、且**只面向作者本人**：这里**不过滤 {@code status}**，
     * 于是 {@code status=REMOVED}（平台下架）第一次在前台露出——作者不知道自己少了哪一帖，
     * 就既无法等待恢复也无法申诉；自删（{@code is_deleted=1}）则对作者也不出现，与全站既有读路径
     * 的墓碑约定一致（本仓没有恢复能力，列出已删行等于给一个死列表）。
     *
     * <p>因此本方法的调用方**必须**是"作者本人"（id 只能取自令牌，见控制器的无身份参数约束）；
     * 把它接到任何带外部传入 authorId 的端点上，等于把下架内容公开。
     */
    PageResult<Post> findPageByAuthor(Long authorId, int page, int size);

    /**
     * 作者可见帖子计数（他人主页 {@code /u/:id} 资料卡的「帖子 N」）：条件与 {@link #findPageByAuthors}
     * **逐字同源**（{@code author_id = ? AND status='PUBLISHED' AND is_deleted=0}）。
     *
     * <p>同源不是洁癖而是唯一判据：资料卡上的数字与它下方那份列表若由两处各写一遍条件，
     * 迟早出现「帖子 12」而列表只有 10 条的对不上——这类偏差在前端看不出来，只能靠口径同源堵死。
     */
    long countVisibleByAuthor(Long authorId);

    /**
     * 作者**可见帖子**的获赞总数（他人主页资料卡的「获赞 N」，CR-104 / D-1）：
     * {@code SUM(like_count)}，WHERE 与 {@link #countVisibleByAuthor} 逐字同源
     * （{@code author_id = ? AND status='PUBLISHED' AND is_deleted=0}）。
     *
     * <p>读的是 {@code posts.like_count} 这一冗余计数列（V1 就位、由 {@link #adjustLikeCount} 单点增减），
     * 不在这里 JOIN {@code likes} 现算——现算要扫全表点赞行，而计数列的准确性已由点赞用例与真机冒烟守住。
     *
     * <p>无可见帖子时返回 0（SQL 侧 {@code COALESCE}），不返回 {@code null}：调用方是展示层计数，
     * 让它去处理"聚合空集"等于把 SQL 细节漏到用例里。
     */
    long sumVisibleLikeCountByAuthor(Long authorId);

    /**
     * 站内搜索（F-FORUM-008）：标题全文（ngram）+ tags 冗余列 LIKE 兜底，只搜 PUBLISHED 且未删除，
     * 按相关度得分 DESC、created_at DESC 排序。{@code boardId} 为 null 表示全站；{@code days} 为 null 表示不限时间窗。
     */
    PageResult<Post> search(String keyword, Long boardId, Integer days, int page, int size);

    /**
     * 按 id 查**未删除**的帖子（{@code is_deleted=1} 视为不存在，属仓储读侧约定：软删是行级墓碑，
     * 不进入聚合）；{@code status} 是否可见由应用层按 {@link Post#isVisible()} 判定。
     */
    Optional<Post> findById(Long id);

    /**
     * 批量按 id 查**未删除**的帖子（读侧约定同 {@link #findById}）；通知读时组装用（F-SOC-001），
     * 一页一次取齐标题，不做 N+1。空集合由实现短路，不会发出 {@code IN ()} 非法 SQL。
     */
    List<Post> findByIds(Collection<Long> ids);

    /** 新增帖子（INSERT）；返回带数据库生成 id 与时间戳的聚合 */
    Post save(Post post);

    /**
     * 分配楼层号（设计 §4.1，本 Sprint 唯一并发点）：在同一事务内先把 {@code posts.reply_count} 自增
     * （行锁先行，使同一帖子的并发回帖被串行化），再读回自增后的值作为本次回复的 floor_no。
     *
     * <p>为什么不加 {@code uk(post_id, floor_no)}：需新增迁移，与「本 Sprint 零迁移」冲突；
     * 该取舍登记在 sprint-2-design §6（D-2）。返回值为 floor_no。
     */
    int incrementReplyCountAndGet(Long postId);

    /**
     * 采纳最佳答案（F-QA-001）：定向 UPDATE {@code posts.is_accepted=1, accepted_reply_id=?}。
     * 与 {@link #incrementReplyCountAndGet} 同理走定向 SQL 而非整行回写——聚合无整行状态需要持久化，
     * 且 UPDATE posts 行锁先行使同一帖子的并发采纳在数据库层串行化（后写覆盖前写，即"可更换"语义）。
     */
    void updateAcceptedReply(Long postId, Long replyId);

    /**
     * 作者自助删除（F-FORUM-006）：定向 UPDATE {@code posts.is_deleted=1}，**条件带 {@code is_deleted=0}**。
     *
     * <p>返回是否有行被改：0 行只可能是并发下别人已删——调用方据此返回 3001 而不是再记一条审计
     * （同一次删除留两条 {@code POST_DELETE} 会让审计变成噪音）。
     *
     * <p>为什么改 {@code is_deleted} 而不是把 {@code status} 置为某个"已删除"值：本仓储的读侧约定是
     * "软删是行级墓碑、不进入聚合"（见 {@link #findById}），列表 / 搜索 / 热榜候选 / 通知摘要等读路径
     * 全部过滤该列；而 {@code status=REMOVED} 预留的语义是**平台处置**（机审 / 管理员下架），
     * 与作者自助删除是两件事，合并后将来无法区分"谁删的"。
     */
    boolean markDeleted(Long postId);

    /**
     * 内容处置（F-SAFE-003 / CR-066）：定向 UPDATE {@code posts.status}，**条件带原状态**。
     *
     * <p>返回是否有行被改：0 行只可能是并发下别人已把状态改成目标值——调用方据此**不再重复记审计**
     * （同 {@link #markDeleted} 的纪律）。原状态由调用方从 {@link #findById} 的聚合读出，
     * 因此"下架一个已下架的帖"与"恢复一个未下架的帖"都是幂等 200、零审计。
     *
     * <p>为什么写 {@code status} 而不是 {@code is_deleted}：前者是**平台处置**（可恢复、要留"谁删的"之分），
     * 后者是**作者自助删除的行级墓碑**；两列语义一旦合并，事后无法拆分。
     */
    boolean updateStatus(Long postId, PostStatus target, PostStatus expectedCurrent);

    /** 点赞计数增减（F-FORUM-005，delta 为 ±1）；返回增减后的 like_count，供响应回显 */
    int adjustLikeCount(Long postId, int delta);

    /** 收藏计数增减（F-FORUM-005，delta 为 ±1）；返回增减后的 favorite_count */
    int adjustFavoriteCount(Long postId, int delta);

    /**
     * 热榜候选分批读（ADR-006）：{@code status='PUBLISHED' AND is_deleted=0 AND created_at >= since}，
     * 按 id ASC 游标推进（{@code afterId} 为 null 表示从头开始），只取算分需要的 5 列，**不装配聚合**。
     *
     * <p>用游标而非 OFFSET：刷新窗口内的帖子每轮全量扫过，OFFSET 会随页数加深而放大扫描量。
     */
    List<HotScoreInput> findHotCandidates(Instant since, int limit, Long afterId);

    /**
     * 定向写回单帖热度分：{@code UPDATE posts SET hot_score=?}。
     *
     * <p>与 {@link #adjustLikeCount} 同理**绝不整行回写**——刷新是异步的，整行回写会把读取那一刻的
     * {@code reply_count} / {@code like_count} / {@code favorite_count} 覆盖回去，抹掉期间发生的互动。
     */
    void updateHotScore(Long postId, double score);

    /**
     * 把**不再是候选**的帖子热度分一次性置 0（窗口外的老帖、已删除、非 PUBLISHED），返回受影响行数。
     *
     * <p>为什么必须做：指数衰减只降不消，不置零则历史高分帖会永久留在热榜前列；
     * 已删除 / 下架的帖子更不该带着分数出现在 {@code sort=hot} 的排序依据里。
     * 实现只更新 {@code hot_score <> 0} 的行，避免每轮空写全表。
     */
    int resetHotScoresBefore(Instant since);

    /**
     * 阅读数 +1（CR-074）：定向 UPDATE {@code view_count = view_count + 1}，与 like/favorite 计数同理
     * 绝不整行回写。去重判定（每帖每账号每日一次）由应用层经 Redis 完成后才会调到这里。
     */
    void incrementViewCount(Long postId);

    /**
     * 「关注」Feed 分页（CR-074）：{@code author_id IN (?) AND status='PUBLISHED' AND is_deleted=0}，
     * 时间序倒排（与 {@link #findPage} 的 LATEST 同序、id DESC 兜底）。空集合由实现短路，不发 {@code IN ()}。
     */
    PageResult<Post> findPageByAuthors(Collection<Long> authorIds, int page, int size);

    /**
     * Similar-post recommendation (publish-time assist): full-text search on title within the given board,
     * excluding the caller's own posts. Only PUBLISHED + not-deleted posts are considered.
     * Results ordered by relevance DESC, created_at DESC, id DESC, capped at {@code limit}.
     *
     * <p>返回窄载体 {@link SimilarPostRow}（不装配完整聚合）：推荐条目只需 id / title / replyCount / accepted / createdAt，
     * SELECT * 再丢弃是纯浪费（CR-077 Warning #7）。
     */
    List<SimilarPostRow> findSimilar(String title, Long boardId, Long excludeAuthorId, int limit);
}
