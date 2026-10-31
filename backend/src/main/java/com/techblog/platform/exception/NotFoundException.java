package com.techblog.platform.exception;

/**
 * 请求的资源不存在（如文章、用户等），统一返回 404。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
