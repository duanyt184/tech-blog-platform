package com.techblog.platform.dto;

import com.techblog.platform.entity.UserRole;

/**
 * 对外暴露的用户信息（注册返回、登录返回、当前用户查询共用）。
 * 刻意不包含 passwordHash，保证任何接口都不会把密码哈希序列化到响应里。
 */
public record AuthUserResponse(
        Long id,
        String username,
        String email,
        UserRole role) {
}
