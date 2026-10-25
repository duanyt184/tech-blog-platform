package com.techblog.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入用户名或邮箱")
        @Size(max = 255, message = "用户名或邮箱过长")
        String identifier,

        @NotBlank(message = "请输入密码")
        @Size(max = 128, message = "密码过长")
        String password) {
}
