package com.flowpilot.auth;

import com.flowpilot.common.BizException;
import com.flowpilot.model.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证与授权拦截器：
 *  1. 除白名单接口外，校验 Authorization: Bearer <token>；
 *  2. 解析用户信息写入 CurrentUser；
 *  3. 校验方法上的 @RequireRole 角色层级。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 白名单：标注 @PublicApi 的接口（登录、Webhook 回调等）
        if (handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(40100, "未登录或缺少凭证");
        }
        Claims claims;
        try {
            claims = jwtService.parse(header.substring(7));
        } catch (Exception e) {
            throw new BizException(40101, "凭证无效或已过期，请重新登录");
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        CurrentUser.set(userId, username, role);

        // 角色校验
        RequireRole rr = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (rr == null) {
            rr = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (rr != null && !hasRole(role, rr.value())) {
            throw new BizException(40300, "权限不足，需要角色: " + rr.value());
        }
        return true;
    }

    private boolean hasRole(String roleStr, User.Role required) {
        try {
            User.Role actual = User.Role.valueOf(roleStr);
            return actual.ordinal() >= required.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 写操作审计日志：谁在什么时间改了什么接口（配合 calibration_logs 构成完整审计链）
        String method = request.getMethod();
        if (("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && request.getRequestURI().startsWith("/api/")) {
            var principal = CurrentUser.get();
            log.info("[AUDIT] user={} method={} uri={} status={}",
                    principal == null ? "anonymous" : principal.username(),
                    method, request.getRequestURI(), response.getStatus());
        }
        CurrentUser.clear();
    }
}
