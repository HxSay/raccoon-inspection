package com.raccoon.cloud.system.sso.exception;

/**
 * SSO 业务异常，携带可读错误信息供上层转换为统一响应。
 */
public class SsoException extends RuntimeException {

    private final String errorCode;

    public SsoException(String message) {
        this("SSO_ERROR", message);
    }

    public SsoException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SsoException(String message, Throwable cause) {
        this("SSO_ERROR", message, cause);
    }

    public SsoException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
