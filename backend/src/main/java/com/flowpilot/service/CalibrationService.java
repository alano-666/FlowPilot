package com.flowpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.ai.AiSchemas;
import com.flowpilot.common.BizException;
import com.flowpilot.model.CalibrationLog;
import com.flowpilot.model.PendingSuggestion;
import com.flowpilot.model.Project;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.repository.CalibrationLogRepository;
import com.flowpilot.repository.PendingSuggestionRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.StakeholderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工校准服务（PRD 3.6）：
 *  - 人工修正优先级最高，修正后项目进入锁定态，AI 不自动覆盖；
 *  - 锁定期间 AI 结果转入待确认建议，用户确认后才生效；
 *  - 每次修正生成校准日志（修改人/时间/前后值对比），支持审计追溯。
 */
@Service
public class CalibrationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectRepository projectRepository;
    private final CalibrationLogRepository logRepository;
    private final PendingSuggestionRepository suggestionRepository;
    private final StakeholderRepository stakeholderRepository;

    public CalibrationService(ProjectRepository projectRepository, CalibrationLogRepository logRepository,
                              PendingSuggestionRepository suggestionRepository,
                              StakeholderRepository stakeholderRepository) {
        this.projectRepository = projectRepository;
        this.logRepository = logRepository;
        this.suggestionRepository = suggestionRepository;
        this.stakeholderRepository = stakeholderRepository;
    }

    /**
     * 人工修正项目状态（当前节点/进度/风险）。
     * 修正后自动锁定项目，除非 lock=false。
     */
    @Transactional
    public Project correct(Long projectId, String field, String newValue, String note,
                           boolean lock, Long operatorId, String operatorName) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));

        CalibrationLog log = new CalibrationLog();
        log.setProjectId(projectId);
        log.setUserId(operatorId == null ? 0L : operatorId);
        log.setUsername(operatorName);
        log.setField(field);
        log.setNewValue(newValue);
        log.setNote(note);

        switch (field) {
            case "current_node" -> {
                log.setOldValue(p.getCurrentNodeKey());
                p.setCurrentNodeKey(newValue);
            }
            case "progress" -> {
                log.setOldValue(String.valueOf(p.getProgress()));
                double v = Double.parseDouble(newValue);
                if (v < 0 || v > 1) {
                    throw new BizException(40007, "进度需在 0~1 之间");
                }
                p.setProgress(v);
            }
            case "risk_status" -> {
                log.setOldValue(p.getRiskStatus().name());
                p.setRiskStatus(Project.RiskStatus.valueOf(newValue.toUpperCase()));
            }
            default -> throw new BizException(40007, "不支持的修正字段: " + field
                    + "（可选 current_node/progress/risk_status）");
        }
        p.setManualLock(lock);
        p.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(p);
        logRepository.save(log);
        return p;
    }

    /** 解除人工锁定 */
    @Transactional
    public Project unlock(Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        p.setManualLock(false);
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    /** 干系人增删改 */
    @Transactional
    public Stakeholder upsertStakeholder(Long projectId, Long id, String nodeKey, String role,
                                         String name, String contactType, String contactId,
                                         String wechatId, Long operatorId, String operatorName) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        Stakeholder s = id == null ? new Stakeholder() : stakeholderRepository.findById(id)
                .orElseThrow(() -> new BizException(40404, "干系人不存在: " + id));
        s.setProjectId(projectId);
        if (nodeKey != null) {
            s.setNodeKey(nodeKey);
        }
        if (role != null) {
            s.setRole(role);
        }
        if (name != null) {
            s.setName(name);
        }
        if (contactType != null) {
            s.setContactType(Stakeholder.ContactType.valueOf(contactType.toUpperCase()));
        }
        if (contactId != null) {
            s.setContactId(contactId);
        }
        if (wechatId != null) {
            s.setWechatId(wechatId);
        }
        s.setUpdatedAt(LocalDateTime.now());
        Stakeholder saved = stakeholderRepository.save(s);

        CalibrationLog log = new CalibrationLog();
        log.setProjectId(projectId);
        log.setUserId(operatorId == null ? 0L : operatorId);
        log.setUsername(operatorName);
        log.setField("stakeholder");
        log.setOldValue(id == null ? "(新增)" : String.valueOf(id));
        log.setNewValue((name == null ? "" : name) + "(" + (role == null ? "" : role) + ")");
        logRepository.save(log);
        return saved;
    }

    @Transactional
    public void deleteStakeholder(Long projectId, Long id) {
        Stakeholder s = stakeholderRepository.findById(id)
                .orElseThrow(() -> new BizException(40404, "干系人不存在: " + id));
        if (!s.getProjectId().equals(projectId)) {
            throw new BizException(40301, "干系人不属于该项目");
        }
        stakeholderRepository.delete(s);
    }

    // ---------- AI 建议确认/驳回 ----------

    public List<PendingSuggestion> pendingSuggestions(Long projectId) {
        return suggestionRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, PendingSuggestion.Status.PENDING);
    }

    /** 确认 AI 建议：应用建议内容到项目（此时视为用户授权覆盖） */
    @Transactional
    public Project confirmSuggestion(Long suggestionId, Long operatorId, String operatorName) {
        PendingSuggestion s = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new BizException(40405, "建议不存在: " + suggestionId));
        if (s.getStatus() != PendingSuggestion.Status.PENDING) {
            throw new BizException(40908, "该建议已处理");
        }
        Project p = projectRepository.findById(s.getProjectId())
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + s.getProjectId()));
        try {
            AiSchemas.AnalysisResult r = MAPPER.readValue(s.getSuggestionJson(), AiSchemas.AnalysisResult.class);
            p.setCurrentNodeKey(r.current_node_key());
            if (r.progress() != null && r.progress() >= 0 && r.progress() <= 1) {
                p.setProgress(r.progress());
            }
            if (r.risk_status() != null && r.risk_status().matches("normal|warning|blocked")) {
                p.setRiskStatus(Project.RiskStatus.valueOf(r.risk_status().toUpperCase()));
            }
            if (r.latest_activity() != null) {
                p.setLatestActivity(r.latest_activity());
            }
            p.setLastAnalyzedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());
            projectRepository.save(p);

            CalibrationLog log = new CalibrationLog();
            log.setProjectId(p.getId());
            log.setUserId(operatorId == null ? 0L : operatorId);
            log.setUsername(operatorName);
            log.setField("ai_suggestion_confirm");
            log.setOldValue("(AI 建议 " + suggestionId + ")");
            log.setNewValue(s.getSuggestionJson());
            logRepository.save(log);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(50011, "建议内容解析失败: " + e.getMessage());
        }
        s.setStatus(PendingSuggestion.Status.CONFIRMED);
        s.setHandledBy(operatorName);
        s.setHandledAt(LocalDateTime.now());
        suggestionRepository.save(s);
        return p;
    }

    @Transactional
    public PendingSuggestion rejectSuggestion(Long suggestionId, String operatorName) {
        PendingSuggestion s = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new BizException(40405, "建议不存在: " + suggestionId));
        s.setStatus(PendingSuggestion.Status.REJECTED);
        s.setHandledBy(operatorName);
        s.setHandledAt(LocalDateTime.now());
        return suggestionRepository.save(s);
    }

    public List<CalibrationLog> logs(Long projectId) {
        return logRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
