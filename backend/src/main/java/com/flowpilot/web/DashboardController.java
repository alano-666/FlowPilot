package com.flowpilot.web;

import com.flowpilot.common.ApiResponse;
import com.flowpilot.model.Project;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 看板接口（PRD 3.4）：概览统计、风险列表。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 概览统计卡片 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(dashboardService.overview());
    }

    /** 风险项目列表 */
    @GetMapping("/risks")
    public ApiResponse<List<Project>> risks() {
        return ApiResponse.ok(dashboardService.riskProjects());
    }

    /** 项目干系人（含一键沟通深链） */
    @GetMapping("/projects/{id}/stakeholders")
    public ApiResponse<List<Stakeholder>> stakeholders(@PathVariable Long id) {
        return ApiResponse.ok(dashboardService.stakeholders(id));
    }
}
