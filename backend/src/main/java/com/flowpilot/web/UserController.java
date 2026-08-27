package com.flowpilot.web;

import com.flowpilot.auth.RequireRole;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.model.User;
import com.flowpilot.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（管理员）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(authService.listUsers().stream().map(authService::toMap).toList());
    }

    public record CreateUserRequest(String username, String password, String displayName, String role) {
    }

    @RequireRole(User.Role.ADMIN)
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateUserRequest req) {
        return ApiResponse.ok(authService.toMap(
                authService.createUser(req.username(), req.password(), req.displayName(), req.role())));
    }

    public record UpdateUserRequest(String displayName, String role, String feishuOpenId,
                                    String wecomUserId, String phone, Boolean active) {
    }

    @RequireRole(User.Role.ADMIN)
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return ApiResponse.ok(authService.toMap(
                authService.updateUser(id, req.displayName(), req.role(), req.feishuOpenId(),
                        req.wecomUserId(), req.phone(), req.active())));
    }

    public record ResetPasswordRequest(String newPassword) {
    }

    @RequireRole(User.Role.ADMIN)
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(id, req.newPassword());
        return ApiResponse.ok();
    }
}
