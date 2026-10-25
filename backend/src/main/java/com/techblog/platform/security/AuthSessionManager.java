package com.techblog.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

/**
 * 负责「把认证结果写进 HTTP Session」和「把 Session 里的登录态清掉」。
 *
 * <p>Spring Security 6 默认 <strong>不会</strong> 自动把 SecurityContext 保存到 Session
 * （SecurityContextHolderFilter 采用 requireExplicitSave），所以手工调用 AuthenticationManager
 * 完成登录后必须显式 saveContext，否则下一个请求又变回未登录——这是 Spring Security 6
 * 相比 5.x 最容易踩的坑之一。</p>
 */
@Component
public class AuthSessionManager {

    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthSessionManager(
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /**
     * 建立登录态：先做会话固定攻击防护（换 SessionId），再把上下文写入 Session。
     */
    public void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * 退出登录：真正让服务端 Session 失效（而不是只在前端删个 token），
     * 这样旧 Cookie 即使被保存下来也无法继续访问受保护接口；
     * 同时下发一个过期的 JSESSIONID，避免浏览器里残留一张已经无用的 Cookie。
     */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
    }
}
