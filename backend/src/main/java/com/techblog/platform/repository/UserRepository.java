package com.techblog.platform.repository;

import com.techblog.platform.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * 登录时允许用「用户名或邮箱」中的任意一个作为账号。
     * 两次传入同一个 identifier 即可（MySQL 的 *_ai_ci 排序规则本身不区分大小写）。
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

