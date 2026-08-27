package com.flowpilot.web;

import com.flowpilot.auth.CurrentUser;
import com.flowpilot.auth.PublicApi;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    /** 登录：返回 JWT 与用户信息（失败 5 次/5 分钟触发防爆破） */
    @PublicApi
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.ok(authService.login(req.username(), req.password(), clientIp(request)));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long id = CurrentUser.idOrNull();
        return ApiResponse.ok(authService.toMap(authService.getById(id)));
    }
}
