package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService.AdminContentItem;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService.AdminContentPage;
import com.campuslink.module.forum.domain.gateway.AdminContentSearchGateway;
import com.campuslink.module.forum.domain.gateway.AdminContentSearchGateway.ContentRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台内容搜索用例（F-ADMIN-002 / CR-141）：类型归一、关键词 / 时间窗校验、作者筛选优先级、
 * 分页归一与纯文本摘要。
 */
@ExtendWith(MockitoExtension.class)
class AdminContentSearchApplicationServiceTest {

    private static final long POST_ID = 7L;
    private static final long AUTHOR_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-25T08:00:00Z");

    @Mock
    private AdminContentSearchGateway gateway;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private MarkdownRenderer markdownRenderer;

    @InjectMocks
    private AdminContentSearchApplicationService service;

    @Test
    @DisplayName("缺省类型归一 post：空关键词不传、无作者筛选传 null、分页归一并截成纯文本摘要")
    void defaultsNormalizeAndMapToSummary() {
        when(gateway.search(eq("post"), isNull(), isNull(), isNull(), eq(1), eq(100)))
                .thenReturn(new PageResult<>(List.of(row("post", "帖子标题")), 1, 1, 100));
        when(markdownRenderer.toPlainSummary("正文", 200)).thenReturn("正文摘要");
        when(accountApplicationService.nicknamesOf(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "作者甲"));

        AdminContentPage result = service.search(null, "   ", null, null, null, 0, 999);

        assertThat(result.kind()).isEqualTo("post");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.total()).isEqualTo(1);
        AdminContentItem item = result.items().get(0);
        assertThat(item.id()).isEqualTo(POST_ID);
        assertThat(item.kind()).isEqualTo("post");
        assertThat(item.title()).isEqualTo("帖子标题");
        assertThat(item.summary()).isEqualTo("正文摘要");
        assertThat(item.boardName()).isEqualTo("技术问答");
        assertThat(item.authorNickname()).isEqualTo("作者甲");
        assertThat(item.status()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("reply 类型与关键词 / 时间窗原样下传；昵称关键字解析为作者 id 集合")
    void replyKindPassesFiltersAndResolvesAuthorKeyword() {
        when(accountApplicationService.accountIdsByNickname("张")).thenReturn(List.of(AUTHOR_ID));
        when(gateway.search("reply", "Redis", List.of(AUTHOR_ID), 7, 2, 10))
                .thenReturn(new PageResult<>(List.of(row("reply", "父帖标题")), 1, 2, 10));
        when(markdownRenderer.toPlainSummary("正文", 200)).thenReturn("摘要");
        when(accountApplicationService.nicknamesOf(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "张三"));

        AdminContentPage result = service.search("reply", " Redis ", null, " 张 ", 7, 2, 10);

        assertThat(result.kind()).isEqualTo("reply");
        assertThat(result.items().get(0).title()).isEqualTo("父帖标题");
        verify(gateway).search("reply", "Redis", List.of(AUTHOR_ID), 7, 2, 10);
    }

    @Test
    @DisplayName("userId 优先于 authorKeyword：有 userId 时不去解析昵称")
    void userIdTakesPrecedenceOverAuthorKeyword() {
        when(gateway.search("post", null, List.of(9L), null, 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.search("post", null, 9L, "张三", null, 1, 20);

        verify(accountApplicationService, never()).accountIdsByNickname(any());
    }

    @Test
    @DisplayName("关键词 1 字 / 超过 50 字 → 1001，且不查仓储")
    void invalidKeywordIsRejected() {
        assertThatThrownBy(() -> service.search("post", "a", null, null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.search("post", "x".repeat(51), null, null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("days 只接受 7 / 30 / 90：8 → 1001，且不查仓储")
    void invalidDaysIsRejected() {
        assertThatThrownBy(() -> service.search("post", null, null, null, 8, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("昵称关键字无匹配 → 空页，且不发空 IN 查询")
    void unmatchedAuthorKeywordShortCircuits() {
        when(accountApplicationService.accountIdsByNickname("不存在")).thenReturn(List.of());

        AdminContentPage result = service.search("post", null, null, "不存在", null, 1, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(gateway, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    private static ContentRow row(String kind, String title) {
        return new ContentRow(POST_ID, kind, title, "正文", "技术问答",
                AUTHOR_ID, "作者甲", "PUBLISHED", CREATED_AT);
    }
}
