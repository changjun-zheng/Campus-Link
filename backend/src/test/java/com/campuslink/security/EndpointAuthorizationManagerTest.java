package com.campuslink.security;

import com.campuslink.module.account.web.UserController;
import com.campuslink.module.assistant.web.AssistantController;
import com.campuslink.module.assistant.web.vo.StartConversationRequest;
import com.campuslink.module.forum.web.BoardController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 路径级鉴权的裁决矩阵（CR-031）：公开放行、受保护要求登录、module 端点未声明即 fail-closed、
 * 非业务处理器（springdoc / 静态资源等）不拦。
 *
 * <p>为什么必须有这组断言：本类落地前 `SecurityConfig` 是 `anyRequest().permitAll()`，
 * 声明与拦截完全脱钩；现在"声明即拦截"由框架执行，一旦本矩阵被改错，
 * 受保护端点会静默回到公开状态——而真机 401 只在人工冒烟时才能发现。
 */
class EndpointAuthorizationManagerTest {

    private static final Method BOARD_LIST = methodOf(BoardController.class, "list");
    private static final Method USER_ME = methodOf(UserController.class, "me", Authentication.class);
    private static final Method ASSISTANT_CONFIG = methodOf(AssistantController.class, "config");
    private static final Method ASSISTANT_START = methodOf(AssistantController.class, "start",
            StartConversationRequest.class, Authentication.class);

    private final RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider = providerOf(handlerMapping);
    private final EndpointAuthorizationManager manager = new EndpointAuthorizationManager(handlerMappingProvider);

    @Test
    @DisplayName("公开端点（@PublicEndpoint）+ 匿名 → 放行")
    void publicEndpointAllowsAnonymous() throws Exception {
        mappedTo(BOARD_LIST, BoardController.class);

        assertThat(authorize(anonymous()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("受保护端点（@SecurityRequirement）+ 匿名 → 拒绝（链上产出 401 / 4001）")
    void protectedEndpointRejectsAnonymous() throws Exception {
        mappedTo(USER_ME, UserController.class);

        assertThat(authorize(anonymous()).isGranted()).isFalse();
    }

    @Test
    @DisplayName("受保护端点 + 已登录 → 放行（角色 / 资源级授权仍归业务代码）")
    void protectedEndpointAllowsSignedInUser() throws Exception {
        mappedTo(USER_ME, UserController.class);

        assertThat(authorize(user()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("问 AI：公开配置放行；开启会话受保护，匿名在链上拒绝")
    void assistantEndpointsFollowDeclarations() throws Exception {
        mappedTo(ASSISTANT_CONFIG, AssistantController.class);
        assertThat(authorize(anonymous()).isGranted()).isTrue();

        mappedTo(ASSISTANT_START, AssistantController.class);
        assertThat(authorize(anonymous()).isGranted()).isFalse();
        assertThat(authorize(user()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("非业务处理器（如 springdoc / 静态资源映射到的普通类）→ 放行")
    void nonBusinessHandlerIsAllowed() throws Exception {
        mappedTo(methodOf(UnannotatedFixture.class, "handle"), UnannotatedFixture.class);

        assertThat(authorize(anonymous()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("fail-closed：module 端点未显式声明公开 / 受保护 → 要求登录（前缀相邻者不受影响）")
    void undeclaredModuleEndpointIsFailClosed() {
        assertThat(EndpointAuthorizationManager.requiresAuthentication(
                "com.campuslink.module.forum.web.SomeController", false, false)).isTrue();
        assertThat(EndpointAuthorizationManager.requiresAuthentication(
                "com.campuslink.module2.NotBusiness", false, false)).isFalse();

        assertThat(EndpointAuthorizationManager.requiresAuthentication("com.campuslink.module.forum.web.C", true, false)).isFalse();
        assertThat(EndpointAuthorizationManager.requiresAuthentication("com.campuslink.module.forum.web.C", false, true)).isTrue();
        assertThat(EndpointAuthorizationManager.requiresAuthentication("com.campuslink.util.Helper", false, false)).isFalse();
    }

    @Test
    @DisplayName("MVC 的\"无匹配\"信号（方法不支持等）→ 不拦，交回 MVC 产出 405 / 404")
    void mappingMissIsNotBlocked() throws Exception {
        when(handlerMapping.getHandler(any()))
                .thenThrow(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(authorize(anonymous()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("未知解析异常 → fail-closed 拒绝：静默放行等于本层鉴权整体失效（真机首测即 Bean 歧义踩中）")
    void unexpectedResolutionFailureIsFailClosed() throws Exception {
        when(handlerMapping.getHandler(any())).thenThrow(new IllegalStateException(
                "NoUniqueBeanDefinitionException: requestMappingHandlerMapping,controllerEndpointHandlerMapping"));

        assertThat(authorize(anonymous()).isGranted()).isFalse();
    }

    @Test
    @DisplayName("非 HandlerMethod 处理器（静态资源）→ 不拦")
    void nonHandlerMethodHandlerIsNotBlocked() throws Exception {
        when(handlerMapping.getHandler(any())).thenReturn(new HandlerExecutionChain(new Object()));

        assertThat(authorize(anonymous()).isGranted()).isTrue();
    }

    @Test
    @DisplayName("无处理器（无此路由）→ 不拦，交回 MVC 产出 404")
    void noHandlerIsNotBlocked() throws Exception {
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThat(authorize(anonymous()).isGranted()).isTrue();
    }

    private void mappedTo(Method method, Class<?> beanType) throws Exception {
        Object bean = mock(beanType);
        when(handlerMapping.getHandler(any()))
                .thenReturn(new HandlerExecutionChain(new HandlerMethod(bean, method)));
    }

    private org.springframework.security.authorization.AuthorizationResult authorize(Authentication authentication) {
        return manager.authorize(() -> authentication, new RequestAuthorizationContext(new MockHttpServletRequest()));
    }

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RequestMappingHandlerMapping> providerOf(RequestMappingHandlerMapping handlerMapping) {
        ObjectProvider<RequestMappingHandlerMapping> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(handlerMapping);
        return provider;
    }

    private static Method methodOf(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试引用的方法已变更：" + type.getName() + "#" + name, e);
        }
    }

    /** 非业务包的无声明处理器样本（模拟框架自身注册的处理器） */
    static class UnannotatedFixture {

        public void handle() {
        }
    }
}
