package com.campuslink.module.forum.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 帖子列表内容筛选口径（CR-142「等你来答」）：{@link #NONE} 不筛（缺省）、
 * {@link #UNANSWERED} 待解答（{@code type='QUESTION' AND is_accepted=0}）。
 *
 * <p>用枚举而非裸 String / boolean 穿层，理由同 {@link PostSortOrder}：wire 值只在
 * application 侧解析一次，端口与仓储只见枚举；将来若增加「已解决」等筛选项，不必再改端口签名。
 *
 * <p>「待解答」的定义是**问答帖且尚无采纳答案**——有回答但未采纳也算（提问者仍可期待更好的回答，
 * 或尚未看到满意的回答）；非问答帖（DISCUSSION）的 {@code is_accepted} 恒 0，语义上没有"待解答"可言，
 * 因此同时限定 {@code type='QUESTION'}，避免讨论帖混入。
 */
public enum PostListFilter {

    /** 不筛（缺省）：行为与引入本枚举之前完全一致 */
    NONE(null),

    /** 待解答：仅问答帖且尚无采纳答案 */
    UNANSWERED("unanswered");

    private final String code;

    PostListFilter(String code) {
        this.code = code;
    }

    /**
     * 解析 wire 值：null（未传）→ {@link #NONE}；其余去首尾空白 + 大小写不敏感比对。
     * 不识别的值（含空串）一律 {@link Optional#empty()}，**不静默归一到 NONE**——
     * 与 {@link PostSortOrder} / 同端点族 {@code days} 只受理 7/30/90 的口径一致：
     * 静默降级会让调用方以为拿到了待解答列表、实际拿到的是全量列表，这种错法查不出来。
     */
    public static Optional<PostListFilter> fromCode(String code) {
        if (code == null) {
            return Optional.of(NONE);
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (PostListFilter filter : values()) {
            if (filter.code != null && filter.code.equals(normalized)) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }
}
