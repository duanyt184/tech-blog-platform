package com.techblog.platform.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportResolveRequest(
        @NotBlank(message = "请选择处理方式")
        String action) {
}
