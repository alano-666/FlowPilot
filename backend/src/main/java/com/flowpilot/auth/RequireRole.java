package com.flowpilot.auth;

import com.flowpilot.model.User;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色权限标注。角色层级：ADMIN > MANAGER > MEMBER。
 * 标注 {@link User.Role#MANAGER} 时 MANAGER 与 ADMIN 均可访问，依此类推。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    User.Role value();
}
