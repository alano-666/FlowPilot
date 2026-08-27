package com.flowpilot.web;

import com.flowpilot.auth.RequireRole;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.common.PageResult;
import com.flowpilot.model.NotificationJob;
import com.flowpilot.model.User;
import com.flowpilot.repository.NotificationJobRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.NotifyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知接口（PRD 3.8）：通知任务列表、每日摘要手动推送。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotifyController {

    private final NotificationJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final NotifyService notifyService;

    public NotifyController(NotificationJobRepository jobRepository, ProjectRepository projectRepository,
                            NotifyService notifyService) {
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
        this.notifyService = notifyService;
    }

    /** 通知任务分页 */
    @GetMapping
    public ApiResponse<PageResult<NotificationJob>> list(@RequestParam(required = false) Long projectId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        Page<NotificationJob> p = projectId == null
                ? jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, size))
                : jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId,
                        PageRequest.of(page - 1, size));
        return ApiResponse.ok(new PageResult<>(p.getContent(), p.getTotalElements(), page, size));
    }

    /** 手动触发每日进度摘要推送 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/digest")
    public ApiResponse<Map<String, Object>> sendDigest() {
        List<com.flowpilot.model.Project> active = projectRepository
                .findByStatus(com.flowpilot.model.Project.Status.ACTIVE);
        notifyService.sendDailyDigest(active);
        return ApiResponse.ok(Map.of("pushedProjects", active.size()));
    }
}
