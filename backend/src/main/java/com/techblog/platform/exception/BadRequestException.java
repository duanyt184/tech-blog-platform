package com.techblog.platform.exception;

/** 请求本身不合法（如两次密码不一致），对应 HTTP 400。 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
