package com.techblog.platform.exception;

import com.techblog.platform.dto.ApiErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一的异常出口，保证前端永远拿到同一种 JSON 结构：
 * {@code {"code": "...", "message": "...", "fields": {"username": "..."}}}。
 *
 * <p>原则：不把数据库异常、堆栈、SQL 等内部细节返回给调用方。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", "请检查填写内容", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_BODY", "请求体格式不正确"));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", exception.getMessage()));
    }

    /**
     * 兜底：数据库唯一索引被并发写穿（两个请求同时通过了 exists 检查）。
     * 此时唯一的正确做法是把 500 转成 409，而不是把 SQL 错误抛给前端。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", "用户名或邮箱已被占用"));
    }

    /** 登录失败统一提示「用户名或密码错误」，避免被用来枚举系统里有哪些账号。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        boolean missingCredentials = exception instanceof AuthenticationCredentialsNotFoundException;
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(missingCredentials
                        ? ApiErrorResponse.of("UNAUTHENTICATED", "请先登录")
                        : ApiErrorResponse.of("INVALID_CREDENTIALS", "用户名或密码错误"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("FORBIDDEN", "没有权限执行该操作"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // Spring MVC 自己抛出的协议级异常（方法不支持 405、Content-Type 不支持 415 等）
        // 都实现了 ErrorResponse，必须按它们的真实状态码返回，
        // 否则会被兜底成 500，让调用方误以为服务端崩了。
        if (exception instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return ResponseEntity.status(status).body(switch (status) {
                case 405 -> ApiErrorResponse.of("METHOD_NOT_ALLOWED", "请求方法不支持");
                case 406 -> ApiErrorResponse.of("NOT_ACCEPTABLE", "无法返回请求的响应格式");
                case 415 -> ApiErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "请求的 Content-Type 不受支持");
                case 404 -> ApiErrorResponse.of("NOT_FOUND", "接口不存在");
                default -> ApiErrorResponse.of("REQUEST_ERROR", "请求无法处理");
            });
        }
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "服务器内部错误"));
    }
}
