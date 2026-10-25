package com.techblog.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 统一错误响应体。前端只需读 message 就能提示用户，
 * 表单页额外用 fields 把错误定位到具体输入框。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Map<String, String> fields) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null);
    }
}
