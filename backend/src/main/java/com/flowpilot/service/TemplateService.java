package com.flowpilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.ai.AiSchemas;
import com.flowpilot.ai.LlmFactory;
import com.flowpilot.ai.LlmProvider;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.common.BizException;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.TemplateVersion;
import com.flowpilot.repository.FlowTemplateRepository;
import com.flowpilot.repository.TemplateVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模板服务（PRD 3.1 流程知识库管理）：
 * 文档解析 → AI 建模 → 草稿确认 → 版本发布 → 项目引用。
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowTemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final DocumentParser documentParser;
    private final LlmFactory llmFactory;
    private final FeishuClient feishuClient;

    public TemplateService(FlowTemplateRepository templateRepository,
                           TemplateVersionRepository versionRepository,
                           DocumentParser documentParser, LlmFactory llmFactory,
                           FeishuClient feishuClient) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.documentParser = documentParser;
        this.llmFactory = llmFactory;
        this.feishuClient = feishuClient;
    }

    public Page<FlowTemplate> page(String keyword, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size);
        if (keyword == null || keyword.isBlank()) {
            return templateRepository.findByStatusNotOrderByUpdatedAtDesc(FlowTemplate.Status.ARCHIVED, pr);
        }
        return templateRepository.findByNameContainingIgnoreCaseAndStatusNotOrderByUpdatedAtDesc(
                keyword.trim(), FlowTemplate.Status.ARCHIVED, pr);
    }

    public FlowTemplate get(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BizException(40402, "流程模板不存在: " + id));
    }

    /** AI 解析建模：生成草稿模板（PRD 6.1 时序） */
    public FlowTemplate parseAndCreate(String docName, String text, String createdBy) {
        AiSchemas.TemplateParseResult parsed = llmFactory.get()
                .parseTemplate(new LlmProvider.TemplateParseContext(docName, text));
        FlowTemplate t = fromParseResult(docName, parsed, createdBy, null);
        // 附带提取文本预览（前端据此提示文件解析是否完整）
        t.setExtractedTextPreview(text.length() > 300 ? text.substring(0, 300) + "…" : text);
        return t;
    }

    /** 解析结果 → 草稿模板（AI 解析、Mock 解析共用） */
    @Transactional
    public FlowTemplate fromParseResult(String docName, AiSchemas.TemplateParseResult parsed,
                                        String createdBy, Long templateId) {
        FlowTemplate t = templateId == null ? new FlowTemplate() : get(templateId);
        // 模型未给出流程名时，回退用文档名（去扩展名）
        String fallbackName = docName == null || docName.isBlank() ? "未命名流程"
                : docName.replaceAll("\\.[^.]+$", "");
        t.setName(parsed.flow_name() == null || parsed.flow_name().isBlank()
                ? fallbackName : parsed.flow_name());
        t.setDescription(parsed.description());
        t.setNodesJson(toJson(parsed.nodes() == null ? List.of() : parsed.nodes()));
        t.setBranchesJson(toJson(parsed.branches() == null ? List.of() : parsed.branches()));
        t.setGlossaryJson(toJson(parsed.glossary() == null ? List.of() : parsed.glossary()));
        t.setSourceDocName(docName);
        t.setStatus(FlowTemplate.Status.DRAFT);
        if (templateId == null) {
            t.setCreatedBy(createdBy);
            t.setCreatedAt(LocalDateTime.now());
        }
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(t);
    }

    /** 更新模板（编辑节点/分支/词库）并生成版本快照 */
    @Transactional
    public FlowTemplate update(Long id, String name, String description, String nodesJson,
                               String branchesJson, String glossaryJson, String note, String operator) {
        FlowTemplate t = get(id);
        if (!t.getStatus().equals(FlowTemplate.Status.DRAFT) && !t.getStatus().equals(FlowTemplate.Status.ACTIVE)) {
            throw new BizException(40903, "模板已停用，不可编辑");
        }
        // 校验 JSON 合法且节点 key 唯一
        validateNodes(nodesJson);
        snapshot(t, note, operator);

        if (name != null && !name.isBlank()) {
            t.setName(name);
        }
        if (description != null) {
            t.setDescription(description);
        }
        if (nodesJson != null) {
            t.setNodesJson(nodesJson);
        }
        if (branchesJson != null) {
            t.setBranchesJson(branchesJson);
        }
        if (glossaryJson != null) {
            t.setGlossaryJson(glossaryJson);
        }
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(t);
    }

    /** 发布（PRD 3.1.2 用户可视化编辑确认后保存为正式模板） */
    @Transactional
    public FlowTemplate publish(Long id, String operator) {
        FlowTemplate t = get(id);
        if (t.getNodesJson() == null || t.getNodesJson().isBlank() || "[]".equals(t.getNodesJson())) {
            throw new BizException(40904, "模板没有流程节点，无法发布");
        }
        snapshot(t, "发布", operator);
        t.setStatus(FlowTemplate.Status.ACTIVE);
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(t);
    }

    @Transactional
    public FlowTemplate duplicate(Long id, String operator) {
        FlowTemplate src = get(id);
        FlowTemplate copy = new FlowTemplate();
        copy.setName(src.getName() + "-副本");
        copy.setDescription(src.getDescription());
        copy.setNodesJson(src.getNodesJson());
        copy.setBranchesJson(src.getBranchesJson());
        copy.setGlossaryJson(src.getGlossaryJson());
        copy.setSourceDocName(src.getSourceDocName());
        copy.setStatus(FlowTemplate.Status.DRAFT);
        copy.setCreatedBy(operator);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(copy);
    }

    @Transactional
    public FlowTemplate archive(Long id) {
        FlowTemplate t = get(id);
        t.setStatus(FlowTemplate.Status.ARCHIVED);
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(t);
    }

    public List<TemplateVersion> versions(Long id) {
        return versionRepository.findByTemplateIdOrderByVersionDesc(id);
    }

    /** 从飞书文档链接解析（docToken 从 URL 提取） */
    public FlowTemplate parseFromFeishuDoc(String docUrl, String createdBy) {
        String token = extractDocToken(docUrl);
        if (token == null) {
            throw new BizException(40005, "无法从链接中识别飞书文档 ID，请复制文档链接的 token 部分");
        }
        String text = feishuClient.fetchDocRawContent(token);
        return parseAndCreate("飞书文档-" + token + ".md", text, createdBy);
    }

    // ---------- 工具 ----------

    private void validateNodes(String nodesJson) {
        try {
            List<Map<String, Object>> nodes = MAPPER.readValue(nodesJson, new TypeReference<>() {
            });
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (Map<String, Object> n : nodes) {
                Object key = n.get("key");
                if (key == null || String.valueOf(key).isBlank()) {
                    throw new BizException(40905, "节点缺少唯一 key 字段");
                }
                if (!keys.add(String.valueOf(key))) {
                    throw new BizException(40905, "节点 key 重复: " + key);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40905, "节点 JSON 格式错误: " + e.getMessage());
        }
    }

    private void snapshot(FlowTemplate t, String note, String operator) {
        TemplateVersion v = new TemplateVersion();
        v.setTemplateId(t.getId());
        v.setVersion(t.getVersion());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("name", t.getName());
        snap.put("description", t.getDescription());
        snap.put("nodes", safeReadTree(t.getNodesJson()));
        snap.put("branches", safeReadTree(t.getBranchesJson()));
        snap.put("glossary", safeReadTree(t.getGlossaryJson()));
        v.setSnapshotJson(toJson(snap));
        v.setNote(note);
        v.setCreatedBy(operator);
        versionRepository.save(v);
    }

    private Object safeReadTree(String json) {
        try {
            return MAPPER.readTree(json == null ? "[]" : json);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 从飞书文档 URL 提取 token：/docx/xxxx / docs/xxxx 或 ?docToken= */
    private String extractDocToken(String url) {
        if (url == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(/docx/|/docs/|docToken=)([A-Za-z0-9]+)")
                .matcher(url);
        return m.find() ? m.group(2) : null;
    }
}
