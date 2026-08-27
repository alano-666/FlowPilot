package com.flowpilot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退：前端路由（/projects/1、/templates 等）刷新时返回 index.html，
 * 由 Vue Router 接管；/api 与静态资源请求不受影响。
 */
@Controller
public class SpaController {

    @GetMapping(value = {"/", "/login", "/projects/**", "/templates/**", "/channels",
            "/reports", "/notifications", "/settings"})
    public String forward() {
        return "forward:/index.html";
    }
}
