package com.flowpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.repository.FlowTemplateRepository;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 项目实例服务（PRD 3.7 AI事件/项目实例管理）：
 * 创建（继承模板快照）、CSV 批量导入、状态流转、渠道绑定、筛选查询。
 */
@Service
public class ProjectService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectRepository projectRepository;
    private final FlowTemplateRepository templateRepository;
    private final ProjectChannelRepository channelRepository;

    public ProjectService(ProjectRepository projectRepository, FlowTemplateRepository templateRepository,
                          ProjectChannelRepository channelRepository) {
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.channelRepository = channelRepository;
    }

    public record CreateRequest(String name, Long templateId, String customerName,
                                List<Map<String, Object>> channels, String createdBy) {
    }

    @Transactional
    public Project create(CreateRequest req) {
        FlowTemplate template = templateRepository.findById(req.templateId())
                .orElseThrow(() -> new BizException(40402, "流程模板不存在: " + req.templateId()));
        if (template.getStatus() != FlowTemplate.Status.ACTIVE) {
            throw new BizException(40906, "模板未发布，不能用于创建项目（请先在模板管理页发布）");
        }
        Project p = new Project();
        p.setCode(generateCode());
        p.setName(req.name());
        p.setTemplateId(template.getId());
        p.setTemplateName(template.getName());
        p.setTemplateSnapshotJson(buildSnapshot(template));
        p.setCustomerName(req.customerName());
        p.setStatus(Project.Status.ACTIVE);
        p.setRiskStatus(Project.RiskStatus.NORMAL);
        p.setStartedAt(LocalDateTime.now());
        p.setOwnerId(1L);
        p.setCreatedBy(req.createdBy());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p = projectRepository.save(p);

        if (req.channels() != null) {
            for (Map<String, Object> c : req.channels()) {
                bindChannel(p.getId(), channelTypeOf(String.valueOf(c.get("channelType"))),
                        String.valueOf(c.get("channelId")), String.valueOf(c.getOrDefault("channelName", "")));
            }
        }
        return p;
    }

    /** 项目编号：P + 日期 + 3 位序号 */
    private String generateCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        AtomicInteger seq = new AtomicInteger(1);
        String code;
        do {
            code = "P" + date + String.format("%03d", seq.getAndIncrement());
        } while (projectRepository.existsByCode(code));
        return code;
    }

    private String buildSnapshot(FlowTemplate t) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("name", t.getName());
        snap.put("nodes", safeRead(t.getNodesJson()));
        snap.put("branches", safeRead(t.getBranchesJson()));
        snap.put("glossary", safeRead(t.getGlossaryJson()));
        try {
            return MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Object safeRead(String json) {
        try {
            return MAPPER.readTree(json == null ? "[]" : json);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Project get(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + id));
    }

    /** 看板列表：状态/风险/关键词筛选 + 分页排序 */
    public Page<Project> page(String status, String riskStatus, String keyword, int page, int size) {
        Specification<Project> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), Project.Status.valueOf(status.toUpperCase())));
            }
            if (riskStatus != null && !riskStatus.isBlank()) {
                ps.add(cb.equal(root.get("riskStatus"), Project.RiskStatus.valueOf(riskStatus.toUpperCase())));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("name"), like),
                        cb.like(root.get("customerName"), like),
                        cb.like(root.get("templateName"), like)));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return projectRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    /** 状态流转：暂停 / 归档 / 恢复 */
    @Transactional
    public Project changeStatus(Long id, String targetStatus) {
        Project p = get(id);
        p.setStatus(Project.Status.valueOf(targetStatus.toUpperCase()));
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    /** 基本信息修改（不涉及进度等校准字段，校准走 CalibrationService） */
    @Transactional
    public Project updateBasic(Long id, String name, String customerName, Long ownerId) {
        Project p = get(id);
        if (name != null && !name.isBlank()) {
            p.setName(name);
        }
        if (customerName != null) {
            p.setCustomerName(customerName);
        }
        if (ownerId != null) {
            p.setOwnerId(ownerId);
        }
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    // ---------- 渠道绑定 ----------

    /** 渠道绑定（String 类型入口，供控制器使用；tenantCode 为飞书组织租户，缺省主组织） */
    @Transactional
    public ProjectChannel bindChannel(Long projectId, String channelType, String channelId,
                                      String channelName, String tenantCode) {
        ProjectChannel pc = bindChannel(projectId, channelTypeOf(channelType), channelId, channelName);
        if (tenantCode != null && !tenantCode.isBlank()) {
            pc.setTenantCode(tenantCode);
            channelRepository.save(pc);
        }
        return pc;
    }

    /** 渠道绑定（String 类型入口，供控制器使用） */
    @Transactional
    public ProjectChannel bindChannel(Long projectId, String channelType, String channelId, String channelName) {
        return bindChannel(projectId, channelType, channelId, channelName, null);
    }

    @Transactional
    public ProjectChannel bindChannel(Long projectId, ProjectChannel.ChannelType type,
                                      String channelId, String channelName) {
        Project p = get(projectId);
        if (channelId == null || channelId.isBlank()) {
            throw new BizException(40006, "渠道 ID 不能为空");
        }
        if (channelRepository.existsByProjectIdAndChannelTypeAndChannelId(projectId, type, channelId)) {
            throw new BizException(40907, "该渠道已绑定本项目");
        }
        ProjectChannel pc = new ProjectChannel();
        pc.setProjectId(projectId);
        pc.setChannelType(type);
        pc.setChannelId(channelId);
        pc.setChannelName(channelName == null || channelName.isBlank() ? channelId : channelName);
        pc.setSyncEnabled(true);
        channelRepository.save(pc);
        p.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(p);
        return pc;
    }

    @Transactional
    public void unbindChannel(Long projectId, Long channelId) {
        channelRepository.deleteById(channelId);
    }

    @Transactional
    public void toggleChannelSync(Long channelId, boolean enabled) {
        ProjectChannel pc = channelRepository.findById(channelId)
                .orElseThrow(() -> new BizException(40403, "渠道绑定不存在: " + channelId));
        pc.setSyncEnabled(enabled);
        channelRepository.save(pc);
    }

    public List<ProjectChannel> channels(Long projectId) {
        return channelRepository.findByProjectId(projectId);
    }

    private ProjectChannel.ChannelType channelTypeOf(String s) {
        try {
            return ProjectChannel.ChannelType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            throw new BizException(40006, "未知渠道类型: " + s + "（可选 FEISHU/WECOM/WECHAT_IMPORT/MOCK）");
        }
    }

    // ---------- CSV 批量导入 ----------

    public record BatchResult(int success, int failed, List<String> errors) {
    }

    /** CSV 批量创建项目：列序 项目名称,流程模板ID,客户名称 */
    @Transactional
    public BatchResult batchImport(String csvText, String createdBy) {
        String[] lines = csvText.replace("\r\n", "\n").split("\n");
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("项目名称")) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 2) {
                failed++;
                errors.add("第 " + (i + 1) + " 行格式错误（需至少：项目名称,模板ID）");
                continue;
            }
            try {
                CreateRequest req = new CreateRequest(cols[0].trim(), Long.parseLong(cols[1].trim()),
                        cols.length > 2 ? cols[2].trim() : null, null, createdBy);
                create(req);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add("第 " + (i + 1) + " 行: " + e.getMessage());
            }
        }
        return new BatchResult(success, failed, errors);
    }
}
