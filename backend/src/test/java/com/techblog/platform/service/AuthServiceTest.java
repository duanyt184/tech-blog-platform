package com.techblog.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techblog.platform.dto.AuthUserResponse;
import com.techblog.platform.dto.LoginRequest;
import com.techblog.platform.dto.RegisterRequest;
import com.techblog.platform.entity.User;
import com.techblog.platform.entity.UserRole;
import com.techblog.platform.exception.BadRequestException;
import com.techblog.platform.exception.ConflictException;
import com.techblog.platform.repository.UserRepository;
import com.techblog.platform.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager);
    }

    @Test
    @DisplayName("注册：落库的是 BCrypt 哈希，角色固定为 USER")
    void shouldStoreHashedPasswordWithUserRole() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("{bcrypt}hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = authService.register(
                new RegisterRequest("alice", "alice@example.com", "secret123", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}hashed-value");
        assertThat(saved.getPasswordHash()).doesNotContain("secret123");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("注册：邮箱统一转成小写并去掉首尾空格")
    void shouldNormalizeEmailAndUsername() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest("  bob  ", "  BOB@Example.COM ", "secret123", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("bob");
        assertThat(captor.getValue().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("注册：两次密码不一致时拒绝，且不写库")
    void shouldRejectMismatchedPasswords() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "secret123", "secret124")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("两次输入的密码不一致");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册：用户名重复返回 409 语义的冲突异常")
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "secret123", "secret123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("用户名已被占用");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册：邮箱重复返回 409 语义的冲突异常")
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "secret123", "secret123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("邮箱已被注册");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册：超过 BCrypt 72 字节上限的密码必须拒绝（否则多余部分会被静默截断）")
    void shouldRejectPasswordLongerThanBcryptLimit() {
        String longPassword = "密".repeat(25); // 25 * 3 = 75 字节

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", longPassword, longPassword)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 字节");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("登录：把账号密码交给 AuthenticationManager 校验")
    void shouldDelegateAuthentication() {
        UserPrincipal principal = new UserPrincipal(1L, "alice", "alice@example.com", "hash", UserRole.USER);
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);

        Authentication result = authService.authenticate(new LoginRequest("alice@example.com", "secret123"));

        assertThat(result.getPrincipal()).isEqualTo(principal);
        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("登录：密码错误时异常向上抛出，由全局异常处理器转成 401")
    void shouldPropagateBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.authenticate(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
