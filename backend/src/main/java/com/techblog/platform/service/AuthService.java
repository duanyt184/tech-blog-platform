package com.techblog.platform.service;

import com.techblog.platform.dto.AuthUserResponse;
import com.techblog.platform.dto.LoginRequest;
import com.techblog.platform.dto.RegisterRequest;
import com.techblog.platform.entity.User;
import com.techblog.platform.exception.BadRequestException;
import com.techblog.platform.exception.ConflictException;
import com.techblog.platform.repository.UserRepository;
import com.techblog.platform.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册与登录的业务逻辑。这里只做「校验 + 计算 + 落库」，
 * 不碰 HttpServletRequest / Session，方便单元测试。
 */
@Service
public class AuthService {

    /** BCrypt 只处理前 72 个字节，超长部分会被静默丢弃，必须提前拒绝。 */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * 注册：用户名/邮箱唯一性校验 → 密码哈希 → 落库。
     * 角色固定为 USER，绝不接收前端传来的 role，避免有人直接注册成 EDITOR。
     */
    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("两次输入的密码不一致");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("密码过长，请控制在 72 字节以内");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("该用户名已被占用");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("该邮箱已被注册");
        }

        User saved = userRepository.save(
                User.create(username, email, passwordEncoder.encode(request.password())));
        return toAuthUserResponse(saved);
    }

    /**
     * 登录：交给 AuthenticationManager（内部是 DaoAuthenticationProvider）完成
     * 「查用户 → 比对 BCrypt 哈希」，失败会抛 AuthenticationException，
     * 由 GlobalExceptionHandler 统一转成 401。
     */
    public Authentication authenticate(LoginRequest request) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(
                request.identifier().trim(), request.password());
        return authenticationManager.authenticate(token);
    }

    public AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public AuthUserResponse toAuthUserResponse(UserPrincipal principal) {
        return new AuthUserResponse(
                principal.getId(), principal.getUsername(), principal.getEmail(), principal.getRole());
    }
}
