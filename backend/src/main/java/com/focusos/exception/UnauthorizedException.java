package com.focusos.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message, 401);
    }

    public UnauthorizedException() {
        super("未授权访问，请先登录", 401);
    }
}
