package com.techblog.platform.repository;

import com.techblog.platform.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = com.techblog.platform.entity.UserRole.ADMIN and u.enabled = true order by u.id")
    java.util.List<User> lockAdmins();

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

