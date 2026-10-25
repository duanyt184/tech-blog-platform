package com.techblog.platform.security;

import com.techblog.platform.entity.User;
import com.techblog.platform.entity.UserRole;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 认证主体（Principal）。
 *
 * <p>为什么不直接把 {@link User} 当 Principal：JPA 实体带上懒加载关系后放进 Session 容易踩坑，
 * 而且实体字段一旦变化就会影响认证逻辑。这里只保留认证和授权真正需要的字段。</p>
 */
public class UserPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final UserRole role;

    public UserPrincipal(Long id, String username, String email, String passwordHash, UserRole role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security 约定：角色权限名以 ROLE_ 前缀表示，配合 hasRole('EDITOR') 使用
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
