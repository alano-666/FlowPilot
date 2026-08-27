package com.flowpilot.service;

import com.flowpilot.model.AnalysisRun;
import com.flowpilot.model.CalibrationLog;
import com.flowpilot.model.Message;
import com.flowpilot.model.NotificationJob;
import com.flowpilot.model.Project;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.repository.AnalysisRunRepository;
import com.flowpilot.repository.CalibrationLogRepository;
import com.flowpilot.repository.MessageRepository;
import com.flowpilot.repository.NotificationJobRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.StakeholderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板服务（PRD 3.4）：概览统计、风险列表、项目时间线。
 */
@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final StakeholderRepository stakeholderRepository;
    private final MessageRepository messageRepository;
    private final AnalysisRunRepository runRepository;
    private final CalibrationLogRepository calibrationLogRepository;
    private final NotificationJobRepository notificationJobRepository;

    public DashboardService(ProjectRepository projectRepository, StakeholderRepository stakeholderRepository,
                            MessageRepository messageRepository, AnalysisRunRepository runRepository,
                            CalibrationLogRepository calibrationLogRepository,
                            NotificationJobRepository notificationJobRepository) {
        this.projectRepository = projectRepository;
        this.stakeholderRepository = stakeholderRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.calibrationLogRepository = calibrationLogRepository;
        this.notificationJobRepository = notificationJobRepository;
    }

    /** 概览统计卡片 */
    public Map<String, Object> overview() {
        List<Project> active = projectRepository.findByStatus(Project.Status.ACTIVE);
        long warning = active.stream().filter(p -> p.getRiskStatus() == Project.RiskStatus.WARNING).count();
        long blocked = active.stream().filter(p -> p.getRiskStatus() == Project.RiskStatus.BLOCKED).count();
        long archived = projectRepository.findByStatus(Project.Status.ARCHIVED).size();
        double avgProgress = active.isEmpty() ? 0
                : active.stream().mapToDouble(Project::getProgress).average().orElse(0);
        long todayAnalyzed = projectRepository.findByStatusNot(Project.Status.ARCHIVED,
                        PageRequest.of(0, 1000)).stream()
                .filter(p -> p.getLastAnalyzedAt() != null
                        && p.getLastAnalyzedAt().toLocalDate().equals(LocalDate.now()))
                .count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeCount", active.size());
        m.put("warningCount", warning);
        m.put("blockedCount", blocked);
        m.put("archivedCount", archived);
        m.put("avgProgress", Math.round(avgProgress * 1000) / 1000.0);
        m.put("todayAnalyzed", todayAnalyzed);
        return m;
    }

    /** 风险项目列表（预警 + 卡顿） */
    public List<Project> riskProjects() {
        return projectRepository.findByStatusAndRiskStatusNot(Project.Status.ACTIVE, Project.RiskStatus.NORMAL)
                .stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    /**
     * 项目时间线：聊天动态、AI 分析、人工校准、通知预警 合并展示。
     */
    public List<Map<String, Object>> timeline(Long projectId) {
        List<Map<String, Object>> events = new ArrayList<>();

        List<Message> messages = messageRepository.findByProjectIdOrderBySentAtAsc(projectId);
        for (Message m : messages) {
            events.add(event(m.getSentAt(), "message", "💬 " + m.getSenderName() + ": " + snippet(m.getContent())));
        }
        for (AnalysisRun r : runRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            events.add(event(r.getCreatedAt(), "analysis",
                    r.getStatus() == AnalysisRun.Status.SUCCESS
                            ? "🤖 AI 分析完成（" + r.getProvider() + "，消费 " + r.getMessageCount() + " 条消息）"
                            : "⚠️ AI 分析失败: " + (r.getErrorMsg() == null ? "" : snippet(r.getErrorMsg()))));
        }
        for (CalibrationLog c : calibrationLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            events.add(event(c.getCreatedAt(), "calibration",
                    "✏️ " + (c.getUsername() == null ? "未知" : c.getUsername())
                            + " 修正 " + c.getField() + ": " + snippet(c.getOldValue())
                            + " → " + snippet(c.getNewValue())));
        }
        for (NotificationJob n : notificationJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            events.add(event(n.getCreatedAt(), "notification",
                    "🔔 " + n.getTitle() + "（" + n.getStatus() + "）"));
        }
        events.sort((a, b) -> ((LocalDateTime) b.get("time")).compareTo((LocalDateTime) a.get("time")));
        return events;
    }

    /** 干系人列表（看板卡片与详情页） */
    public List<Stakeholder> stakeholders(Long projectId) {
        return stakeholderRepository.findByProjectIdOrderByNodeKeyAsc(projectId);
    }

    /** 项目详情页聚合数据 */
    public Map<String, Object> projectDetail(Long projectId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stakeholders", stakeholders(projectId));
        m.put("timeline", timeline(projectId));
        m.put("runs", runRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
        return m;
    }

    private Map<String, Object> event(LocalDateTime time, String type, String text) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", time);
        e.put("type", type);
        e.put("text", text);
        return e;
    }

    private String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
