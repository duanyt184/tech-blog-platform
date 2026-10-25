package com.techblog.platform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techblog.platform.dto.AuthUserResponse;
import com.techblog.platform.dto.LoginRequest;
import com.techblog.platform.dto.RegisterRequest;
import com.techblog.platform.entity.UserRole;
import com.techblog.platform.exception.GlobalExceptionHandler;
import com.techblog.platform.security.AuthSessionManager;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    /** 测试里用请求属性代替 SecurityContext 给 @AuthenticationPrincipal 喂数据 */
    private static final String TEST_PRINCIPAL_ATTRIBUTE = "testPrincipal";

    private static final AuthUserResponse ALICE = new AuthUserResponse(1L, "alice", "alice@example.com", UserRole.USER);

    @Mock
    private AuthService authService;

    @Mock
    private AuthSessionManager authSessionManager;

    private MockMvc mockMvc;

    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new UserPrincipal(1L, "alice", "alice@example.com", "hash", UserRole.USER);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, authSessionManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PrincipalArgumentResolver())
                .build();
    }

    /**
     * standalone MockMvc 不会装 @AuthenticationPrincipal 的解析器，
     * 这里用一个最小实现：从请求属性里取 UserPrincipal，行为与真实解析器一致（取不到就是 null）。
     */
    private static final class PrincipalArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return UserPrincipal.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return webRequest.getAttribute(TEST_PRINCIPAL_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    @Test
    @DisplayName("注册成功返回 201 和用户档案，且不返回密码字段")
    void shouldReturn201WhenRegisterSucceeds() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(ALICE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com",
                                 "password":"secret123","confirmPassword":"secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("注册参数不合法时返回 400，并把错误定位到具体字段")
    void shouldReturnFieldErrorsWhenRegisterPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","email":"not-an-email",
                                 "password":"123","confirmPassword":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    @DisplayName("登录成功返回 200，并把认证结果写入 Session")
    void shouldEstablishSessionWhenLoginSucceeds() throws Exception {
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authService.authenticate(any(LoginRequest.class))).thenReturn(authenticated);
        when(authService.toAuthUserResponse(principal)).thenReturn(ALICE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"alice","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));

        verify(authSessionManager).establish(any(Authentication.class), any(), any());
    }

    @Test
    @DisplayName("账号或密码错误返回 401，且不泄漏是账号错还是密码错")
    void shouldReturn401WhenCredentialsAreInvalid() throws Exception {
        when(authService.authenticate(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"alice","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("退出登录返回 204 并清除服务端登录态")
    void shouldReturn204WhenLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authSessionManager).clear(any(), any());
    }

    @Test
    @DisplayName("已登录时 /me 返回当前用户")
    void shouldReturnCurrentUser() throws Exception {
        when(authService.toAuthUserResponse(principal)).thenReturn(ALICE);

        mockMvc.perform(get("/api/auth/me").requestAttr(TEST_PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @DisplayName("未登录时 /me 返回 401")
    void shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}
