package com.techblog.platform.security;

import com.techblog.platform.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把数据库里的用户翻译成 Spring Security 能识别的 UserDetails。
 *
 * <p>实现这个接口后，DaoAuthenticationProvider 会自动用它查用户、比密码，
 * 我们不需要手写 {@code if (passwordEncoder.matches(...))}。
 * 注意异常必须抛 {@link UsernameNotFoundException}，Spring Security 会把它统一转成
 * BadCredentialsException，避免通过报错内容判断「用户是否存在」。</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String value = identifier == null ? "" : identifier.trim();
        return userRepository.findByUsernameOrEmail(value, value)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }
}
