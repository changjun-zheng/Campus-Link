package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.module.forum.application.cmd.ModerationCommand;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.ReplyStatus;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 内容处置用例（F-SAFE-003 / CR-066）：平台侧把帖子或楼层在 {@code PUBLISHED ↔ REMOVED} 之间迁移。
 *
 * <p>
 * 与作者自助删除（{@link PostApplicationService#deletePost}）是**两件事、两列**：删除写
 * {@code is_deleted} 行级墓碑、只有作者能发起、不可自助恢复；处置写 {@code status}、由 SUPERADMIN 发起、
 * 可恢复。混用后审计将答不出"是谁删的"。
 *
 * <p>
 * 本服务**不做鉴权判定**（角色归 web 层的 {@code CurrentUser.requireRole}），也不判断"目标当前是否可见"
 * ——恢复一条已下架内容时它本来就必须是不可见的。
 */
@Service
@RequiredArgsConstructor
public class ContentModerationApplicationService {

    /** 审计 detail 里的标题截断长度：与 POST_DELETE 同口径 */
    private static final int AUDIT_TITLE_MAX_CHARS = 100;

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final AuditService auditService;
    private final NotificationApplicationService notificationService;

    /**
     * 处置帖子：状态迁移成功后记审计（同事务，二者同生同灭——生产红线第 4 项"内容处置必须写审计日志"）。
     *
     * <p>
     * 三条出口的取值口径：
     * ① 当前态即目标态 → 直接返回（幂等 200、**零审计**，比照"二次删除不重复记 {@code POST_DELETE}"）；
     * ② 帖子不存在或已被作者删除（墓碑行读不到）→ 3001，**不区分原因以防按 id 探测**；
     * ③ 条件更新影响 0 行（读与写之间被并发处置改走）→ 同样 3001，不假装成功。
     * 这条 ③ 就是 CR-060 登记的"读 → 写 TOCTOU"残余在本批的处置方式：**接受并显式选择出口**，
     * 单人试点 + 唯一 SUPERADMIN 下无真实竞争，不引入 SELECT FOR UPDATE。
     */
    @Transactional
    public void moderatePost(Long operatorId, long postId, ModerationCommand command) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);
        PostStatus target = PostStatus.valueOf(command.status());
        PostStatus current = post.getStatus();
        if (current == target) {
            return;
        }
        if (!postRepository.updateStatus(postId, target, current)) {
            throw new PostNotFoundException();
        }
        auditService.record(operatorId, target == PostStatus.REMOVED ? "POST_REMOVE" : "POST_RESTORE",
                "posts", postId, "from=%s to=%s reason=%s boardId=%s title=%s"
                        .formatted(current, target, reason(command), post.getBoardId(),
                                truncate(post.getTitle())));
        // F-SAFE-003 / CR-126：状态真的迁移后通知作者（幂等 / 并发失败出口在上面已返回或抛出）
        if (target == PostStatus.REMOVED) {
            notificationService.contentRemovedPost(operatorId, postId, post.getAuthorId());
        } else {
            notificationService.contentRestoredPost(operatorId, postId, post.getAuthorId());
        }
    }

    /**
     * 处置楼层：与帖子侧同构，差别只有两处——
     * ① {@code Reply} 聚合刻意不带 status，故走 {@link ReplyRepository#findStatusById} 的窄读；
     * ② 审计 detail 不带楼层正文与版块（要读整行才有，处置不需要那个代价），
     * {@code target_id} 已足够定位，配合 {@code posts} 侧的处置记录可复原现场。
     *
     * <p>
     * 父帖此刻是否可见**不在本用例判定范围**：恢复一条位于已下架帖里的楼层，它仍随父帖不可见，
     * 这是列表侧 {@code requireVisiblePost} 的既有行为，不在此重复表达。
     */
    @Transactional
    public void moderateReply(Long operatorId, long replyId, ModerationCommand command) {
        ReplyStatus current = replyRepository.findStatusById(replyId).orElseThrow(PostNotFoundException::new);
        ReplyStatus target = ReplyStatus.valueOf(command.status());
        if (current == target) {
            return;
        }
        if (!replyRepository.updateStatus(replyId, target, current)) {
            throw new PostNotFoundException();
        }
        // findByIds 刻意不过滤 status（只排墓碑），下架中的楼层读得到作者；读不到说明行已被并发删掉，按 3001 出口
        Reply reply = replyRepository.findByIds(List.of(replyId)).stream().findFirst()
                .orElseThrow(PostNotFoundException::new);
        auditService.record(operatorId, target == ReplyStatus.REMOVED ? "REPLY_REMOVE" : "REPLY_RESTORE",
                "replies", replyId, "from=%s to=%s reason=%s".formatted(current, target, reason(command)));
        if (target == ReplyStatus.REMOVED) {
            notificationService.contentRemovedReply(operatorId, replyId, reply.getAuthorId());
        } else {
            notificationService.contentRestoredReply(operatorId, replyId, reply.getAuthorId());
        }
    }

    /**
     * 理由可空（PRD 只要求"处置留审计"，未要求必填理由）；空值统一落 "-"，避免 detail 里出现 {@code null} 字面量。
     * 不截断：上限已由 {@link ModerationCommand} 的 {@code @Size(200)} 挡住，再截一次只会让审计少信息。
     */
    private static String reason(ModerationCommand command) {
        String reason = command.reason();
        return reason == null || reason.isBlank() ? "-" : reason;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "-";
        }
        return text.length() > AUDIT_TITLE_MAX_CHARS ? text.substring(0, AUDIT_TITLE_MAX_CHARS) + "…" : text;
    }
}
