package com.flowpilot.common;

/**
 * 统一 API 响应结构。所有接口返回 {code, message, data} 信封。
 * code=0 表示成功，其余见 GlobalExceptionHandler 中的错误码表。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
