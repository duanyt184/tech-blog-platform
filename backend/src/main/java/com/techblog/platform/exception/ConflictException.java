package com.techblog.platform.exception;

/** 与现有数据冲突（用户名/邮箱已被占用），对应 HTTP 409。 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
