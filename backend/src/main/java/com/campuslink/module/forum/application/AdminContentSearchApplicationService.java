package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.AdminContentSearchGateway;
import com.campuslink.module.forum.domain.gateway.AdminContentSearchGateway.ContentRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * 运营后台内容搜索用例（F-ADMIN-002 / CR-141）：按类型 / 关键词 / 用户 / 时间检索帖子与楼层。
 *
 * <p>口径：
 * <ul>
 *   <li>{@code kind} 只认 {@code reply}，其余（空 / 非法）一律按 {@code post}——与巡查队列同法；</li>
 *   <li>{@code keyword} 去首尾空白后须 2~50 字，空串视为不筛选；{@code days} 只接受 7 / 30 / 90，
 *       两者非法均 {@code 1001}（不静默归一：关键词过短会让 LIKE 退化成全表扫描）；</li>
 *   <li>作者筛选：{@code userId} 优先；否则 {@code authorKeyword} 经 account application 解析为 id 集合，
 *       解析为空即"无匹配"、直接返回空页（不发空 {@code IN ()}）；</li>
 *   <li>出参摘要一律服务端截成纯文本 200 字，**不下发 Markdown / HTML 原文**。</li>
 * </ul>
 *
 * <p>处置动作不在本服务：下架 / 恢复复用既有 {@code ContentModerationApplicationService}，
 * 审计仍为 {@code MODERATE_POST} / {@code MODERATE_REPLY}。
 */
@Service
@RequiredArgsConstructor
public class AdminContentSearchApplicationService {

    private static final Set<Integer> SEARCH_TIME_WINDOWS = Set.of(7, 30, 90);
    private static final int KEYWORD_MIN_CHARS = 2;
    private static final int KEYWORD_MAX_CHARS = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_CHARS = 200;
    private static final String NICKNAME_FALLBACK = "已注销用户";

    private final AdminContentSearchGateway adminContentSearchGateway;
    private final AccountApplicationService accountApplicationService;
    private final MarkdownRenderer markdownRenderer;

    public AdminContentPage search(String kind, String keyword, Long userId, String authorKeyword,
                                   Integer days, int page, int size) {
        String normalizedKind = "reply".equals(kind) ? "reply" : "post";
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty() && (kw.length() < KEYWORD_MIN_CHARS || kw.length() > KEYWORD_MAX_CHARS)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (days != null && !SEARCH_TIME_WINDOWS.contains(days)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        int currentPage = Math.max(page, 1);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<Long> authorIds = resolveAuthors(userId, authorKeyword);
        if (authorIds != null && authorIds.isEmpty()) {
            return new AdminContentPage(normalizedKind, List.of(), 0, currentPage, pageSize);
        }
        PageResult<ContentRow> found = adminContentSearchGateway.search(normalizedKind,
                kw.isEmpty() ? null : kw, authorIds, days, currentPage, pageSize);
        Map<Long, String> nicknames = accountApplicationService.authorBriefsOf(
                        found.items().stream().map(ContentRow::authorId).toList())
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().nickname()));
        List<AdminContentItem> items = found.items().stream()
                .map(row -> new AdminContentItem(row.id(), row.kind(), row.title(),
                        markdownRenderer.toPlainSummary(row.contentMd(), SUMMARY_MAX_CHARS),
                        row.boardName(), row.authorId(),
                        nicknames.getOrDefault(row.authorId(), NICKNAME_FALLBACK),
                        row.status(), row.createdAt()))
                .toList();
        return new AdminContentPage(normalizedKind, items, found.total(), currentPage, pageSize);
    }

    /** userId 优先；无 userId 且无昵称关键字时返回 null（= 不筛作者），昵称解析为空时返回空集合（= 无匹配） */
    private List<Long> resolveAuthors(Long userId, String authorKeyword) {
        if (userId != null) {
            return List.of(userId);
        }
        String kw = authorKeyword == null ? "" : authorKeyword.trim();
        if (kw.isEmpty()) {
            return null;
        }
        return accountApplicationService.accountIdsByNickname(kw);
    }

    /** 搜索结果（application 载体；web 层再映射成 VO） */
    public record AdminContentPage(String kind, List<AdminContentItem> items, long total, int page, int size) {
    }

    /** 搜索条目：{@code title} 为帖子标题（楼层取父帖标题），{@code summary} 为纯文本前 200 字 */
    public record AdminContentItem(long id, String kind, String title, String summary, String boardName,
                                   long authorId, String authorNickname, String status, Instant createdAt) {
    }
}
