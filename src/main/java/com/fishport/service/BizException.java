package com.fishport.service;

import org.springframework.http.HttpStatus;

/** 业务异常：携带 HTTP 状态与业务码 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BizException notFound(String what) {
        return new BizException(HttpStatus.NOT_FOUND, "NOT_FOUND", what);
    }

    public static BizException conflict(String code, String message) {
        return new BizException(HttpStatus.CONFLICT, code, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static BizException unprocessable(String code, String message) {
        return new BizException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
