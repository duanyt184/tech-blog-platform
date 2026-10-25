package com.techblog.platform.controller;

import com.techblog.platform.dto.AuthUserResponse;
import com.techblog.platform.dto.LoginRequest;
import com.techblog.platform.dto.RegisterRequest;
import com.techblog.platform.security.AuthSessionManager;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（Issue #2）。
 *
 * <pre>
 * POST /api/auth/register  201 {"id":1,"username":"...","email":"...","role":"USER"}   注册（不自动登录）
 * POST /api/auth/login     200 {"id":1,...}   成功后写入 JSESSIONID，浏览器自动携带
 * POST /api/auth/logout    204                服务端 Session 失效
 * GET  /api/auth/me        200 {...} / 401    刷新页面时恢复登录态
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionManager authSessionManager;

    public AuthController(AuthService authService, AuthSessionManager authSessionManager) {
        this.authService = authService;
        this.authSessionManager = authSessionManager;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authService.authenticate(request);
        authSessionManager.establish(authentication, httpRequest, httpResponse);
        return authService.toAuthUserResponse((UserPrincipal) authentication.getPrincipal());
    }

    /**
     * 退出登录：让服务端 Session 立即失效。
     * 该接口对未登录请求也放行（幂等），前端不必判断当前是否已登录。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authSessionManager.clear(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    /** 前端刷新后用它判断「我现在是谁」；未登录时由安全过滤器直接返回 401。 */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        // 正常情况下安全过滤器已经拦掉了未登录请求，这里是防御性兜底
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("请先登录");
        }
        return authService.toAuthUserResponse(principal);
    }
}
