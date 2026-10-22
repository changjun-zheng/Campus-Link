package com.campuslink.module.notification.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.notification.application.NotificationApplicationService;
import com.campuslink.module.notification.application.cmd.NotificationResults.MarkAllReadResult;
import com.campuslink.module.notification.application.cmd.NotificationResults.UnreadCountResult;
import com.campuslink.module.notification.application.stream.NotificationStreamService;
import com.campuslink.module.notification.application.stream.NotificationSubscription;
import com.campuslink.module.notification.domain.gateway.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知中心 4 个受保护端点（CR-050 / CR-078，F-SOC-001）的授权三态：全部是**本人私有数据**，匿名一律 4001
 * 且不触达用例；已登录则 userId 取自 principal 透传，不接受任何入参代传（防越权读写他人通知）。
 * 为什么框架已按注解拦截仍需这组断言，见 PostControllerAuthTest。
 */
class NotificationControllerAuthTest {

        private static final long NOTIFICATION_ID = 5L;

        private final NotificationApplicationService notificationApplicationService = mock(
                        NotificationApplicationService.class);
        private final NotificationStreamService notificationStreamService = mock(NotificationStreamService.class);
        private final NotificationController controller = new NotificationController(notificationApplicationService,
                        notificationStreamService);

        @Test
        @DisplayName("匿名查我的通知 → 4001，且不触达查询")
        void anonymousListIsUnauthorized() {
                assertThatThrownBy(() -> controller.list(null, null, 1, 20))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
                verify(notificationApplicationService, never()).list(anyLong(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("匿名查未读数 → 4001")
        void anonymousUnreadCountIsUnauthorized() {
                assertThatThrownBy(() -> controller.unreadCount(null))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
                verify(notificationApplicationService, never()).unreadCount(anyLong());
        }

        @Test
        @DisplayName("匿名全部已读 → 4001，不得把别人标成已读")
        void anonymousMarkAllReadIsUnauthorized() {
                assertThatThrownBy(() -> controller.markAllRead(null))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
                verify(notificationApplicationService, never()).markAllRead(anyLong());
        }

        @Test
        @DisplayName("匿名单条已读 → 4001，不得把别人的通知标成已读")
        void anonymousMarkReadIsUnauthorized() {
                assertThatThrownBy(() -> controller.markRead(NOTIFICATION_ID, null))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
                verify(notificationApplicationService, never()).markRead(anyLong(), anyLong());
        }

        @Test
        @DisplayName("匿名订阅未读流 → 4001，且不注册任何连接")
        void anonymousStreamIsUnauthorized() {
                assertThatThrownBy(() -> controller.stream(null))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
                verify(notificationStreamService, never()).subscribe(anyLong(), any(NotificationSubscription.class));
        }

        @Test
        @DisplayName("已登录订阅未读流 → 注册到 principal 用户并立即下发首帧未读数")
        void authenticatedStreamRegistersSubscription() {
                when(notificationApplicationService.unreadCount(42L)).thenReturn(new UnreadCountResult(3L));
                when(notificationStreamService.subscribe(eq(42L), any(NotificationSubscription.class)))
                                .thenReturn(() -> {
                                });

                assertThat(controller.stream(user())).isNotNull();

                verify(notificationStreamService).subscribe(eq(42L), any(NotificationSubscription.class));
                verify(notificationApplicationService).unreadCount(42L);
        }

        @Test
        @DisplayName("principal 不是用户 ID → 4001（匿名令牌的字符串主体不得被当成已登录）")
        void nonUserIdPrincipalIsUnauthorized() {
                Authentication anonymousToken = new UsernamePasswordAuthenticationToken("anonymousUser", null,
                                List.of());

                assertThatThrownBy(() -> controller.unreadCount(anonymousToken))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        }

        @Test
        @DisplayName("已登录 → 放行，userId 取自 principal、unread 条件原样下推")
        void authenticatedRequestsPassThrough() {
                when(notificationApplicationService.list(42L, true, 1, 20))
                                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));
                when(notificationApplicationService.unreadCount(42L)).thenReturn(new UnreadCountResult(3L));
                when(notificationApplicationService.markAllRead(42L)).thenReturn(new MarkAllReadResult(3));

                assertThat(controller.list(user(), true, 1, 20).code()).isEqualTo(0);
                assertThat(controller.unreadCount(user()).data().unreadCount()).isEqualTo(3L);
                assertThat(controller.markAllRead(user()).data().updated()).isEqualTo(3);

                verify(notificationApplicationService).list(eq(42L), eq(true), anyInt(), anyInt());
        }

        @Test
        @DisplayName("已登录单条已读 → code=0 且 data 为空，通知 id 原样下推、userId 取自 principal")
        void authenticatedMarkReadPassesThrough() {
                var response = controller.markRead(NOTIFICATION_ID, user());

                assertThat(response.code()).isEqualTo(0);
                assertThat(response.data()).isNull();
                verify(notificationApplicationService).markRead(NOTIFICATION_ID, 42L);
        }

        private static Authentication user() {
                return new UsernamePasswordAuthenticationToken(
                                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        }
}
