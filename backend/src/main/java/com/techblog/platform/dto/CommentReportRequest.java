package com.techblog.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentReportRequest(
        @NotBlank(message = "请选择举报原因")
        @Size(max = 200, message = "举报原因过长")
        String reason) {
}
