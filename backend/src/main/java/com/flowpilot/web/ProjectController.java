package com.flowpilot.web;

import com.flowpilot.auth.CurrentUser;
import com.flowpilot.auth.RequireRole;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.common.BizException;
import com.flowpilot.common.PageResult;
import com.flowpilot.model.AnalysisRun;
import com.flowpilot.model.CalibrationLog;
import com.flowpilot.model.Message;
import com.flowpilot.model.PendingSuggestion;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.model.User;
import com.flowpilot.repository.AiInsightRepository;
import com.flowpilot.repository.AnalysisRunRepository;
import com.flowpilot.service.AnalysisService;
import com.flowpilot.service.CalibrationService;
import com.flowpilot.service.DashboardService;
import com.flowpilot.service.MessageService;
import com.flowpilot.service.ProjectService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目（AI 事件）接口（PRD 3.7）：CRUD、渠道、分析、校准、时间线。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final AnalysisService analysisService;
    private final CalibrationService calibrationService;
    private final DashboardService dashboardService;
    private final MessageService messageService;
    private final AnalysisRunRepository runRepository;
    private final AiInsightRepository insightRepository;

    public ProjectController(ProjectService projectService, AnalysisService analysisService,
                             CalibrationService calibrationService, DashboardService dashboardService,
                             MessageService messageService, AnalysisRunRepository runRepository,
                             AiInsightRepository insightRepository) {
        this.projectService = projectService;
        this.analysisService = analysisService;
        this.calibrationService = calibrationService;
        this.dashboardService = dashboardService;
        this.messageService = messageService;
        this.runRepository = runRepository;
        this.insightRepository = insightRepository;
    }

    /** 项目分页列表（看板） */
    @GetMapping
    public ApiResponse<PageResult<Project>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String riskStatus,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<Project> p = projectService.page(status, riskStatus, keyword, page, size);
        return ApiResponse.ok(new PageResult<>(p.getContent(), p.getTotalElements(), page, size));
    }

    /** 项目详情 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("project", projectService.get(id));
        m.put("channels", projectService.channels(id));
        m.putAll(dashboardService.projectDetail(id));
        return ApiResponse.ok(m);
    }

    /** 创建项目（继承模板快照） */
    @RequireRole(User.Role.MANAGER)
    @PostMapping
    public ApiResponse<Project> create(@RequestBody ProjectService.CreateRequest req) {
        return ApiResponse.ok(projectService.create(req));
    }

    public record BatchImportRequest(String csvText) {
    }

    /** CSV 批量导入项目 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/batch-import")
    public ApiResponse<ProjectService.BatchResult> batchImport(@RequestBody BatchImportRequest req) {
        if (req.csvText() == null || req.csvText().isBlank()) {
            throw new BizException(40002, "CSV 内容为空");
        }
        return ApiResponse.ok(projectService.batchImport(req.csvText(), currentUsername()));
    }

    public record StatusRequest(String status) {
    }

    /** 暂停/归档/恢复 */
    @RequireRole(User.Role.MANAGER)
    @PutMapping("/{id}/status")
    public ApiResponse<Project> changeStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return ApiResponse.ok(projectService.changeStatus(id, req.status()));
    }

    public record BasicUpdateRequest(String name, String customerName, Long ownerId) {
    }

    @RequireRole(User.Role.MANAGER)
    @PutMapping("/{id}")
    public ApiResponse<Project> updateBasic(@PathVariable Long id, @RequestBody BasicUpdateRequest req) {
        return ApiResponse.ok(projectService.updateBasic(id, req.name(), req.customerName(), req.ownerId()));
    }

    // ---------- 渠道 ----------

    public record BindChannelRequest(String channelType, String channelId, String channelName) {
    }

    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/channels")
    public ApiResponse<ProjectChannel> bindChannel(@PathVariable Long id, @RequestBody BindChannelRequest req) {
        return ApiResponse.ok(projectService.bindChannel(id, req.channelType(), req.channelId(), req.channelName()));
    }

    @RequireRole(User.Role.MANAGER)
    @DeleteMapping("/{id}/channels/{channelId}")
    public ApiResponse<Void> unbindChannel(@PathVariable Long id, @PathVariable Long channelId) {
        projectService.unbindChannel(id, channelId);
        return ApiResponse.ok();
    }

    public record ToggleSyncRequest(boolean enabled) {
    }

    @RequireRole(User.Role.MANAGER)
    @PutMapping("/{id}/channels/{channelId}/sync")
    public ApiResponse<Void> toggleSync(@PathVariable Long id, @PathVariable Long channelId,
                                        @RequestBody ToggleSyncRequest req) {
        projectService.toggleChannelSync(channelId, req.enabled());
        return ApiResponse.ok();
    }

    // ---------- AI 分析 ----------

    /** 手动触发 AI 分析（同步，返回本次运行记录） */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/analyze")
    public ApiResponse<AnalysisRun> analyze(@PathVariable Long id) {
        return ApiResponse.ok(analysisService.analyze(id, AnalysisRun.TriggerType.MANUAL));
    }

    /** 分析历史 */
    @GetMapping("/{id}/analyses")
    public ApiResponse<List<AnalysisRun>> analyses(@PathVariable Long id) {
        return ApiResponse.ok(runRepository.findByProjectIdOrderByCreatedAtDesc(id));
    }

    /** 证据链 */
    @GetMapping("/{id}/insights")
    public ApiResponse<List<?>> insights(@PathVariable Long id) {
        return ApiResponse.ok(insightRepository.findByProjectIdOrderByCreatedAtDesc(id));
    }

    // ---------- 人工校准 ----------

    public record CorrectRequest(String field, String newValue, String note, Boolean lock) {
    }

    /** 人工修正（默认锁定项目，AI 不自动覆盖） */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/correction")
    public ApiResponse<Project> correct(@PathVariable Long id, @RequestBody CorrectRequest req) {
        var p = CurrentUser.get();
        return ApiResponse.ok(calibrationService.correct(id, req.field(), req.newValue(), req.note(),
                req.lock() == null || req.lock(), p == null ? null : p.id(), currentUsername()));
    }

    /** 解除锁定 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/unlock")
    public ApiResponse<Project> unlock(@PathVariable Long id) {
        return ApiResponse.ok(calibrationService.unlock(id));
    }

    /** 校准日志（审计） */
    @GetMapping("/{id}/calibrations")
    public ApiResponse<List<CalibrationLog>> calibrations(@PathVariable Long id) {
        return ApiResponse.ok(calibrationService.logs(id));
    }

    /** 待确认 AI 建议 */
    @GetMapping("/{id}/suggestions")
    public ApiResponse<List<PendingSuggestion>> suggestions(@PathVariable Long id) {
        return ApiResponse.ok(calibrationService.pendingSuggestions(id));
    }

    /** 确认建议（覆盖项目状态） */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/suggestions/{suggestionId}/confirm")
    public ApiResponse<Project> confirmSuggestion(@PathVariable Long suggestionId) {
        var p = CurrentUser.get();
        return ApiResponse.ok(calibrationService.confirmSuggestion(suggestionId,
                p == null ? null : p.id(), currentUsername()));
    }

    /** 驳回建议 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/suggestions/{suggestionId}/reject")
    public ApiResponse<PendingSuggestion> rejectSuggestion(@PathVariable Long suggestionId) {
        return ApiResponse.ok(calibrationService.rejectSuggestion(suggestionId, currentUsername()));
    }

    // ---------- 干系人 ----------

    public record StakeholderRequest(String nodeKey, String role, String name, String contactType,
                                     String contactId, String wechatId) {
    }

    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/stakeholders")
    public ApiResponse<Stakeholder> upsertStakeholder(@PathVariable Long id,
                                                      @RequestBody StakeholderRequest req) {
        var p = CurrentUser.get();
        return ApiResponse.ok(calibrationService.upsertStakeholder(id, null, req.nodeKey(), req.role(),
                req.name(), req.contactType(), req.contactId(), req.wechatId(),
                p == null ? null : p.id(), currentUsername()));
    }

    @RequireRole(User.Role.MANAGER)
    @PutMapping("/{id}/stakeholders/{stakeholderId}")
    public ApiResponse<Stakeholder> updateStakeholder(@PathVariable Long id, @PathVariable Long stakeholderId,
                                                      @RequestBody StakeholderRequest req) {
        var p = CurrentUser.get();
        return ApiResponse.ok(calibrationService.upsertStakeholder(id, stakeholderId, req.nodeKey(), req.role(),
                req.name(), req.contactType(), req.contactId(), req.wechatId(),
                p == null ? null : p.id(), currentUsername()));
    }

    @RequireRole(User.Role.MANAGER)
    @DeleteMapping("/{id}/stakeholders/{stakeholderId}")
    public ApiResponse<Void> deleteStakeholder(@PathVariable Long id, @PathVariable Long stakeholderId) {
        calibrationService.deleteStakeholder(id, stakeholderId);
        return ApiResponse.ok();
    }

    // ---------- 消息与时间线 ----------

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResult<Message>> messages(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        Page<Message> p = messageService.pageByProject(id, page, size);
        return ApiResponse.ok(new PageResult<>(p.getContent(), p.getTotalElements(), page, size));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<Map<String, Object>>> timeline(@PathVariable Long id) {
        return ApiResponse.ok(dashboardService.timeline(id));
    }

    private String currentUsername() {
        var p = CurrentUser.get();
        return p == null ? "system" : p.username();
    }
}
