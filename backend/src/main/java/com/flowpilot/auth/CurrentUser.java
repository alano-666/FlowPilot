package com.flowpilot.auth;

/**
 * 当前请求用户上下文（ThreadLocal，由 AuthInterceptor 写入与清理）。
 */
public final class CurrentUser {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public record Principal(Long id, String username, String role) {
    }

    public static void set(Long id, String username, String role) {
        HOLDER.set(new Principal(id, username, role));
    }

    public static Principal get() {
        return HOLDER.get();
    }

    /** 当前用户 ID，未认证时返回 null */
    public static Long idOrNull() {
        Principal p = HOLDER.get();
        return p == null ? null : p.id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
