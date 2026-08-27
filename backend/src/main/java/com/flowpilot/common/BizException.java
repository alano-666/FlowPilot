package com.flowpilot.common;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(40000, message);
    }

    public int getCode() {
        return code;
    }
}
