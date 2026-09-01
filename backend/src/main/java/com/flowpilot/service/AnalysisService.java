package com.flowpilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.ai.AiSchemas;
import com.flowpilot.ai.LlmFactory;
import com.flowpilot.ai.LlmProvider;
import com.flowpilot.ai.PromptBuilder;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.AiInsight;
import com.flowpilot.model.AnalysisRun;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Message;
import com.flowpilot.model.NotificationJob;
import com.flowpilot.model.PendingSuggestion;
import com.flowpilot.model.Project;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.repository.AiInsightRepository;
import com.flowpilot.repository.AnalysisRunRepository;
import com.flowpilot.repository.FlowTemplateRepository;
import com.flowpilot.repository.MessageRepository;
import com.flowpilot.repository.PendingSuggestionRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.StakeholderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 流程状态识别引擎（PRD 3.3，产品核心模块）。
 *
 * 处理链路：
 *   增量消息收集(水位线) → 上下文组装 → LLM 结构化识别 → 结果校验
 *   → 落库（未锁定直接更新 / 已锁定转待确认建议） → 证据链与干系人更新 → 联动通知
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmFactory llmFactory;
    private final FlowPilotProperties props;
    private final ProjectRepository projectRepository;
    private final FlowTemplateRepository templateRepository;
    private final MessageRepository messageRepository;
    private final AnalysisRunRepository runRepository;
    private final AiInsightRepository insightRepository;
    private final StakeholderRepository stakeholderRepository;
    private final PendingSuggestionRepository suggestionRepository;
    private final NotifyService notifyService;
    private final ThreadPoolTaskExecutor aiExecutor;

    /** 项目分析互斥：同一项目同时只允许一次分析 */
    private final Set<Long> runningProjects = ConcurrentHashMap.newKeySet();

    /** 分析排队：分析进行中收到的新触发，待当前完成后合并补跑一次 */
    private final Set<Long> queuedProjects = ConcurrentHashMap.newKeySet();

    public AnalysisService(LlmFactory llmFactory, FlowPilotProperties props,
                           ProjectRepository projectRepository, FlowTemplateRepository templateRepository,
                           MessageRepository messageRepository, AnalysisRunRepository runRepository,
                           AiInsightRepository insightRepository, StakeholderRepository stakeholderRepository,
                           PendingSuggestionRepository suggestionRepository, NotifyService notifyService,
                           @Qualifier("aiExecutor") ThreadPoolTaskExecutor aiExecutor) {
        this.llmFactory = llmFactory;
        this.props = props;
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.insightRepository = insightRepository;
        this.stakeholderRepository = stakeholderRepository;
        this.suggestionRepository = suggestionRepository;
        this.notifyService = notifyService;
        this.aiExecutor = aiExecutor;
    }

    /** 异步分析（事件回调/导入触发/定时任务用），同项目并发自动去重 */
    public void analyzeAsync(Long projectId, String trigger) {
        if (!runningProjects.add(projectId)) {
            // 已有分析进行中：合并排队，当前完成后补跑（避免连播场景丢分析）
            queuedProjects.add(projectId);
            log.info("项目 {} 分析进行中，新触发已合并排队", projectId);
            return;
        }
        aiExecutor.execute(() -> {
            try {
                analyze(projectId, "SCHEDULE".equals(trigger) ? AnalysisRun.TriggerType.SCHEDULE
                        : "EVENT".equals(trigger) ? AnalysisRun.TriggerType.EVENT
                        : AnalysisRun.TriggerType.MANUAL);
            } catch (BizException e) {
                if (e.getCode() == 40901) {
                    log.info("项目 {} 无新增消息，跳过分析", projectId);
                } else {
                    log.error("项目 {} 异步分析失败: {}", projectId, e.getMessage());
                }
            } catch (Exception e) {
                log.error("项目 {} 异步分析失败", projectId, e);
            } finally {
                runningProjects.remove(projectId);
                // 合并排队：分析期间有新消息到达，补跑一次覆盖全部增量
                if (queuedProjects.remove(projectId)) {
                    analyzeAsync(projectId, trigger);
                }
            }
        });
    }

    /** 同步分析（手动触发用），返回本次分析结果。
     *  注意：不加 @Transactional——AI 调用可能长达数十秒，
     *  长事务会持有数据库写锁导致消息写入/看板查询阻塞，各保存操作各自提交。 */
    public AnalysisRun analyze(Long projectId, AnalysisRun.TriggerType triggerType) {
        return analyzeWith(projectId, triggerType, llmFactory.get());
    }

    /**
     * 指定 Provider 的分析（演示数据初始化用：固定 Mock 引擎，快速免费且确定性，
     * 与线上 AI Provider 解耦）。
     */
    public AnalysisRun analyzeWith(Long projectId, AnalysisRun.TriggerType triggerType, LlmProvider llm) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        FlowTemplate template = templateRepository.findById(project.getTemplateId())
                .orElseThrow(() -> new BizException(40402, "项目关联的流程模板不存在，请检查模板状态"));

        // 1. 增量消息收集（水位线）：无新消息时把运行记录标记为 FAILED 后返回，
        //    避免留下永久的 RUNNING 僵尸记录
        List<Message> allNew = messageRepository.findByProjectIdAndSentAtAfterOrderBySentAtAsc(
                projectId, project.getLastAnalyzedAt() == null ? LocalDateTime.of(2000, 1, 1, 0, 0)
                        : project.getLastAnalyzedAt());
        if (allNew.isEmpty()) {
            AnalysisRun skipped = new AnalysisRun();
            skipped.setProjectId(projectId);
            skipped.setTriggerType(triggerType);
            skipped.setStatus(AnalysisRun.Status.FAILED);
            skipped.setErrorMsg("没有新增聊天记录，无需分析");
            skipped.setFinishedAt(LocalDateTime.now());
            runRepository.save(skipped);
            throw new BizException(40901, "没有新增聊天记录，无需分析");
        }

        // 2. 创建运行记录
        AnalysisRun run = new AnalysisRun();
        run.setProjectId(projectId);
        run.setTriggerType(triggerType);
        run.setStatus(AnalysisRun.Status.RUNNING);
        run.setCreatedAt(LocalDateTime.now());
        runRepository.save(run);

        List<Message> context = PromptBuilder.sliceMessages(allNew, props.getAi().getMaxMessagesPerAnalysis());
        run.setMessageCount(context.size());

        // 3. LLM 结构化识别
        run.setProvider(llm.name());
        AiSchemas.AnalysisResult result;
        try {
            result = llm.analyzeProject(new LlmProvider.AnalysisContext(template, project, context));
        } catch (Exception e) {
            run.setStatus(AnalysisRun.Status.FAILED);
            run.setErrorMsg(e.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            log.error("项目 {} AI 分析失败: {}", projectId, e.getMessage());
            throw new BizException(50010, "AI 分析失败: " + e.getMessage());
        }

        // 4. 结果校验与规范化
        Normalized normalized = normalize(template, project, result, context);
        run.setResultJson(toJson(normalized.result()));

        // 5. 落库规则：人工锁定 → 待确认建议；否则直接更新
        if (project.isManualLock()) {
            PendingSuggestion suggestion = new PendingSuggestion();
            suggestion.setProjectId(projectId);
            suggestion.setRunId(run.getId());
            suggestion.setSuggestionJson(toJson(result));
            suggestion.setStatus(PendingSuggestion.Status.PENDING);
            suggestionRepository.save(suggestion);
            log.info("项目 {} 处于人工锁定，AI 结果已转入待确认建议", projectId);
        } else {
            applyToProject(project, normalized);
        }

        // 6. 证据链落库（消息索引 → 真实消息映射）
        for (AiSchemas.AnalysisResult.Evidence ev : result.evidence() == null
                ? List.<AiSchemas.AnalysisResult.Evidence>of() : result.evidence()) {
            if (ev.message_index() < 0 || ev.message_index() >= context.size()) {
                continue;
            }
            AiInsight insight = new AiInsight();
            insight.setProjectId(projectId);
            insight.setRunId(run.getId());
            insight.setMessageId(context.get(ev.message_index()).getId());
            insight.setDetectedNodeKey(ev.node_key());
            insight.setSummary(ev.summary());
            insight.setConfidence(ev.confidence() == null ? 0.0 : ev.confidence());
            insightRepository.save(insight);
        }

        // 7. 干系人更新：AI 解析 + 【干系人】标记 + 台词前缀【名字】三路合并
        //    并用消息里的真实发送者 open_id 回填联系方式，保证「一键联系」真实可用
        if (!project.isManualLock()) {
            List<AiSchemas.AnalysisResult.StakeholderUpdate> marked = extractMarkedStakeholders(context);
            List<AiSchemas.AnalysisResult.StakeholderUpdate> prefixed = extractPrefixedStakeholders(context);
            List<AiSchemas.AnalysisResult.StakeholderUpdate> merged = new ArrayList<>();
            merged.addAll(result.stakeholders_update() == null ? List.of() : result.stakeholders_update());
            merged.addAll(marked);
            merged.addAll(prefixed);
            upsertStakeholders(projectId, merged, buildSenderMap(context));
        }

        // 8. 联动通知
        triggerNotifications(project, template, result);

        run.setStatus(AnalysisRun.Status.SUCCESS);
        run.setFinishedAt(LocalDateTime.now());
        runRepository.save(run);
        log.info("项目 {} AI 分析完成：当前节点={} 进度={} 风险={}", projectId,
                normalized.result().current_node_key(), normalized.result().progress(),
                normalized.result().risk_status());
        return run;
    }

    // ---------- 结果规范化 ----------

    private record Normalized(AiSchemas.AnalysisResult result, Set<String> completedBefore, String fallbackActivity) {
    }

    /** 校验 AI 结果：节点 key 必须存在于模板，progress 归一化，风险状态归一化 */
    private Normalized normalize(FlowTemplate template, Project project,
                                 AiSchemas.AnalysisResult result, List<Message> context) {
        Set<String> validKeys = nodeKeys(template);
        Set<String> completed = new LinkedHashSet<>();
        if (result.completed_nodes() != null) {
            for (String k : result.completed_nodes()) {
                if (validKeys.contains(k)) {
                    completed.add(k);
                }
            }
        }
        String current = result.current_node_key();
        if (current == null || !validKeys.contains(current)) {
            // 取第一个未完成的有效节点；全部完成则取最后一个节点
            current = validKeys.stream().filter(k -> !completed.contains(k)).findFirst()
                    .orElse(validKeys.stream().skip(Math.max(0, validKeys.size() - 1)).findFirst().orElse(null));
        }
        Double progress = result.progress();
        if (progress == null || progress < 0 || progress > 1) {
            progress = validKeys.isEmpty() ? 0.0 : (double) completed.size() / validKeys.size();
        }
        String risk = result.risk_status() == null ? "normal" : result.risk_status().toLowerCase();
        if (!risk.matches("normal|warning|blocked")) {
            risk = "normal";
        }
        // 活动摘要回退：取最近一条非标记消息
        String fallback = "";
        for (int i = context.size() - 1; i >= 0; i--) {
            Message last = context.get(i);
            String c = last.getContent() == null ? "" : last.getContent();
            if (c.startsWith("【干系人】") || c.startsWith("【风险】") || c.startsWith("【下一步】")) {
                continue;
            }
            fallback = (last.getSenderName() == null ? "未知" : last.getSenderName())
                    + ": " + (c.length() > 60 ? c.substring(0, 60) + "…" : c);
            break;
        }

        return new Normalized(new AiSchemas.AnalysisResult(
                current, new ArrayList<>(completed), progress, risk,
                result.evidence() == null ? List.of() : result.evidence(),
                result.stakeholders_update() == null ? List.of() : result.stakeholders_update(),
                result.risks() == null ? List.of() : result.risks(),
                result.suggested_next_action(),
                result.temp_nodes() == null ? List.of() : result.temp_nodes(),
                result.latest_activity()), new HashSet<>(), fallback);
    }

    /** 应用分析结果到项目 */
    private void applyToProject(Project project, Normalized normalized) {
        AiSchemas.AnalysisResult r = normalized.result();
        project.setCurrentNodeKey(r.current_node_key());
        project.setProgress(r.progress());
        project.setRiskStatus(Project.RiskStatus.valueOf(r.risk_status().toUpperCase()));
        // 最近动态：AI 未给出时回退取最近一条消息摘要
        String activity = r.latest_activity();
        if (activity == null || activity.isBlank()) {
            activity = normalized.fallbackActivity();
        }
        project.setLatestActivity(activity);
        project.setLastActivityAt(LocalDateTime.now());
        project.setLastAnalyzedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    /** 消息内【干系人】姓名|角色|平台|ID 标记的兜底解析（不依赖 AI 是否解析该格式） */
    private List<AiSchemas.AnalysisResult.StakeholderUpdate> extractMarkedStakeholders(List<Message> context) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "【干系人】([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|\\s]*)");
        List<AiSchemas.AnalysisResult.StakeholderUpdate> out = new ArrayList<>();
        for (Message m : context) {
            if (m.getContent() == null) {
                continue;
            }
            java.util.regex.Matcher matcher = p.matcher(m.getContent());
            while (matcher.find()) {
                out.add(new AiSchemas.AnalysisResult.StakeholderUpdate(
                        null, matcher.group(2).trim(), matcher.group(1).trim(),
                        matcher.group(3).trim().toLowerCase(), matcher.group(4).trim()));
            }
        }
        return out;
    }

    /** 剧本台词前缀解析：消息以【名字】开头（如【林总】【王敏】）→ 识别为干系人 */
    public static List<AiSchemas.AnalysisResult.StakeholderUpdate> extractPrefixedStakeholders(List<Message> context) {
        java.util.regex.Pattern prefix = java.util.regex.Pattern.compile(
                "^【([^】【\\[\\]】]{2,8})】");
        java.util.regex.Pattern systemWord = java.util.regex.Pattern.compile(
                "干系人|风险|下一步|完成|临时节点");
        Map<String, String> roleMap = buildRoleMap(context);
        List<AiSchemas.AnalysisResult.StakeholderUpdate> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Message m : context) {
            if (m.getContent() == null) {
                continue;
            }
            java.util.regex.Matcher matcher = prefix.matcher(m.getContent().trim());
            if (!matcher.find() || systemWord.matcher(matcher.group(1)).find()) {
                continue;
            }
            String name = matcher.group(1).trim();
            String role = roleMap.getOrDefault(name, "");
            String contactId = m.getSenderId() != null && m.getSenderId().startsWith("ou_")
                    && !m.getSenderId().startsWith("ou_cast_") ? m.getSenderId() : "";
            if (!seen.add(name + "|" + role)) {
                continue; // 同一人名+角色去重
            }
            out.add(new AiSchemas.AnalysisResult.StakeholderUpdate(
                    null, role, name, "feishu", contactId));
        }
        return out;
    }

    /**
     * 从群公告式消息解析 姓名→角色 映射：
     * 支持「王敏·产品经理、陈强·研发负责人」「王敏-产品经理」「林总是甲方负责人」等写法。
     */
    public static Map<String, String> buildRoleMap(List<Message> context) {
        Map<String, String> map = new LinkedHashMap<>();
        // 模式1：名字·角色 / 名字-角色（顿号、逗号、空格分隔多个）
        java.util.regex.Pattern dotRole = java.util.regex.Pattern.compile(
                "([\\u4e00-\\u9fa5]{2,4})[·\\-]([\\u4e00-\\u9fa5]{2,12})");
        // 模式2：名字+是/担任+角色（如「林总是甲方负责人」）
        java.util.regex.Pattern isRole = java.util.regex.Pattern.compile(
                "([\\u4e00-\\u9fa5]{2,4})(?:是|担任)([\\u4e00-\\u9fa5]{2,14}(?:负责人|经理|工程师|总监|运维|测试|主管|客户))");
        for (Message m : context) {
            if (m.getContent() == null) {
                continue;
            }
            String text = m.getContent();
            java.util.regex.Matcher d = dotRole.matcher(text);
            while (d.find()) {
                map.putIfAbsent(d.group(1).trim(), d.group(2).trim());
            }
            java.util.regex.Matcher i = isRole.matcher(text);
            while (i.find()) {
                map.putIfAbsent(i.group(1).trim(), i.group(2).trim());
            }
        }
        return map;
    }

    private void upsertStakeholders(Long projectId, List<AiSchemas.AnalysisResult.StakeholderUpdate> updates,
                                    Map<String, String> senderMap) {
        if (updates == null) {
            return;
        }
        for (AiSchemas.AnalysisResult.StakeholderUpdate u : updates) {
            if (u.name() == null || u.name().isBlank()) {
                continue;
            }
            // 真实身份回填：消息发送者的真实 open_id 优先于 AI/剧本推测的假 ID
            String realOpenId = senderMap.get(u.name().trim());
            if (realOpenId == null) {
                realOpenId = fuzzyFindSender(senderMap, u.name().trim());
            }
            String contactId = u.contact_id();
            Stakeholder.ContactType contactType = parseContactType(u.contact_type());
            if (realOpenId != null && !realOpenId.startsWith("ou_cast_")
                    && (contactId == null || contactId.isBlank() || contactId.startsWith("ou_cast_"))) {
                contactId = realOpenId;
                contactType = Stakeholder.ContactType.FEISHU;
            }
            Stakeholder existing = stakeholderRepository.findByProjectIdAndNodeKey(projectId, u.node_key())
                    .stream()
                    .filter(s -> s.getRole() != null && s.getRole().equals(u.role())
                            && s.getName() != null && s.getName().equals(u.name()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setContactType(contactType);
                existing.setContactId(contactId);
                existing.setUpdatedAt(LocalDateTime.now());
                stakeholderRepository.save(existing);
                continue;
            }
            Stakeholder s = new Stakeholder();
            s.setProjectId(projectId);
            s.setNodeKey(u.node_key());
            s.setRole(u.role());
            s.setName(u.name());
            s.setContactType(contactType);
            s.setContactId(contactId);
            if (s.getContactType() == Stakeholder.ContactType.WECHAT) {
                s.setWechatId(contactId);
            }
            stakeholderRepository.save(s);
        }
    }

    /** 从消息构建 姓名→真实飞书 open_id 映射（一键联系的真实性来源；排除剧本假 ID） */
    public static Map<String, String> buildSenderMap(List<Message> messages) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Message m : messages) {
            if (m.getSenderId() != null && m.getSenderId().startsWith("ou_")
                    && !m.getSenderId().startsWith("ou_cast_")     // 群演剧本的假 ID 不算真实身份
                    && m.getSenderName() != null && !m.getSenderName().isBlank()) {
                map.putIfAbsent(m.getSenderName().trim(), m.getSenderId());
            }
        }
        return map;
    }

    /**
     * 模糊匹配：飞书昵称（如「星辰-陈总」「陈总（客户）」）与 AI 识别姓名（「陈总」）
     * 经常不完全一致，用包含关系兜底匹配。
     */
    public static String fuzzyFindSender(Map<String, String> senderMap, String name) {
        if (name == null || name.length() < 2) {
            return null;
        }
        for (Map.Entry<String, String> e : senderMap.entrySet()) {
            if (e.getKey().length() >= 2 && (e.getKey().contains(name) || name.contains(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    private Stakeholder.ContactType parseContactType(String type) {
        if (type == null) {
            return Stakeholder.ContactType.WECHAT;
        }
        return switch (type.toLowerCase()) {
            case "feishu" -> Stakeholder.ContactType.FEISHU;
            case "wecom" -> Stakeholder.ContactType.WECOM;
            default -> Stakeholder.ContactType.WECHAT;
        };
    }

    // ---------- 联动通知 ----------

    private void triggerNotifications(Project project, FlowTemplate template, AiSchemas.AnalysisResult result) {
        List<String> completed = result.completed_nodes() == null ? List.of() : result.completed_nodes();
        List<Stakeholder> nextOwners = stakeholderRepository.findByProjectIdAndNodeKey(
                project.getId(), result.current_node_key());
        if (!completed.isEmpty() && result.current_node_key() != null
                && !result.current_node_key().equals(project.getCurrentNodeKey())) {
            notifyService.notify(project, NotificationJob.Type.NODE_COMPLETED,
                    "节点完成，推进「" + result.current_node_key() + "」",
                    "已完成节点：" + String.join("、", completed) + "，请下一节点责任人启动工作。",
                    nextOwners);
        }
        if (result.risks() != null && !result.risks().isEmpty()) {
            notifyService.notify(project, NotificationJob.Type.RISK_ALERT,
                    "项目风险预警", "识别到以下风险：\n- " + String.join("\n- ", result.risks()), nextOwners);
        }
    }

    // ---------- 工具 ----------

    private Set<String> nodeKeys(FlowTemplate template) {
        Set<String> keys = new LinkedHashSet<>();
        try {
            List<Map<String, Object>> nodes = MAPPER.readValue(
                    template.getNodesJson() == null ? "[]" : template.getNodesJson(), new TypeReference<>() {
                    });
            for (Map<String, Object> n : nodes) {
                if (n.get("key") != null) {
                    keys.add(String.valueOf(n.get("key")));
                }
            }
        } catch (Exception e) {
            log.warn("模板节点解析失败: {}", e.getMessage());
        }
        return keys;
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
