package com.flowpilot.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock Provider：无需任何 API Key 的演示模式。
 *
 * 识别逻辑为确定性的轻量规则（关键词+标记），用于在未配置大模型时
 * 完整演示「消息 → 分析 → 看板更新」闭环，不作为生产识别能力。
 * 与 MockChannelService 约定的标记格式配套：
 *   【完成】节点名   【干系人】张三|客户IT|wecom|zhangsan   【风险】xxx   【下一步】xxx
 */
public class MockLlmProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern STAKEHOLDER = Pattern.compile("【干系人】([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|\\s]+)");
    private static final Pattern RISK = Pattern.compile("【风险】(.+)");
    private static final Pattern NEXT = Pattern.compile("【下一步】(.+)");
    private static final Pattern TEMP_NODE = Pattern.compile("【临时节点】(.+)");
    private static final Pattern NUMBERED_LINE =
            Pattern.compile("^\\s*(?:第[一二三四五六七八九十\\d]+[步条项]|[一二三四五六七八九十]+、|\\d+[.、)]|[-*])\\s*(.+)$");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public AiSchemas.AnalysisResult analyzeProject(AnalysisContext ctx) {
        List<NodeInfo> nodes = loadNodes(ctx.template().getNodesJson());
        if (nodes.isEmpty()) {
            return keepCurrent(ctx.project(), "模板无节点，待人工编辑");
        }

        List<String> completed = new ArrayList<>();
        List<AiSchemas.AnalysisResult.Evidence> evidence = new ArrayList<>();
        List<AiSchemas.AnalysisResult.StakeholderUpdate> stakeholders = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<AiSchemas.AnalysisResult.TempNode> tempNodes = new ArrayList<>();
        String nextAction = null;
        String latestActivity = null;

        List<Message> messages = ctx.messages();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String content = m.getContent() == null ? "" : m.getContent();
            // 节点完成标记：消息中出现节点 key 或节点名+完成词
            for (NodeInfo node : nodes) {
                if (!completed.contains(node.key) && isCompletedMarker(content, node)) {
                    completed.add(node.key);
                    evidence.add(new AiSchemas.AnalysisResult.Evidence(
                            i, node.key, "「" + node.name + "」完成：" + snippet(content), 0.9));
                }
            }
            Matcher sm = STAKEHOLDER.matcher(content);
            while (sm.find()) {
                String contactType = sm.group(3).toLowerCase();
                if (!contactType.matches("feishu|wecom|wechat")) {
                    contactType = "wechat";
                }
                stakeholders.add(new AiSchemas.AnalysisResult.StakeholderUpdate(
                        null, sm.group(2), sm.group(1), contactType, sm.group(4)));
            }
            Matcher rm = RISK.matcher(content);
            while (rm.find()) {
                risks.add(rm.group(1));
            }
            Matcher nm = NEXT.matcher(content);
            if (nm.find()) {
                nextAction = nm.group(1);
            }
            Matcher tm = TEMP_NODE.matcher(content);
            while (tm.find()) {
                tempNodes.add(new AiSchemas.AnalysisResult.TempNode(tm.group(1), "聊天记录中出现，模板未定义"));
            }
            latestActivity = snippet(content);
        }

        // 计算当前节点与进度
        String currentKey = null;
        for (NodeInfo node : nodes) {
            if (!completed.contains(node.key)) {
                currentKey = node.key;
                break;
            }
        }
        if (currentKey == null) {
            currentKey = nodes.get(nodes.size() - 1).key;
        }
        double progress = (double) completed.size() / nodes.size();

        String riskStatus = risks.isEmpty() ? "normal" : (risks.size() > 2 ? "blocked" : "warning");

        if (nextAction == null && !completed.isEmpty()) {
            NodeInfo next = nodes.stream().filter(n -> !completed.contains(n.key)).findFirst().orElse(null);
            nextAction = next == null ? "全部节点已完成，可以归档项目" : "推进节点「" + next.name + "」";
        }

        return new AiSchemas.AnalysisResult(
                currentKey, completed, progress, riskStatus, evidence, stakeholders,
                risks, nextAction, tempNodes, latestActivity);
    }

    private AiSchemas.AnalysisResult keepCurrent(Project p, String activity) {
        return new AiSchemas.AnalysisResult(
                p.getCurrentNodeKey(), new ArrayList<>(), p.getProgress(),
                "normal", new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), "等待更多聊天记录", new ArrayList<>(), activity);
    }

    private boolean isCompletedMarker(String content, NodeInfo node) {
        if (content.contains("【完成】")) {
            return content.contains(node.key) || content.contains(node.name);
        }
        if (!content.contains(node.name)) {
            return false;
        }
        return content.contains("完成") || content.contains("已") || content.contains("确认")
                || content.contains("生效") || content.contains("成功") || content.contains("OK")
                || content.contains("通过") || content.contains("ok");
    }

    @Override
    public AiSchemas.TemplateParseResult parseTemplate(TemplateParseContext ctx) {
        String text = ctx.sourceText();
        List<AiSchemas.TemplateParseResult.Node> nodes = new ArrayList<>();
        Matcher m = NUMBERED_LINE.matcher(text);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.length() > 40) {
                name = name.substring(0, 40);
            }
            String key = "node_" + (nodes.size() + 1);
            nodes.add(new AiSchemas.TemplateParseResult.Node(
                    key, name, nodes.isEmpty() ? "start" : "normal",
                    "聊天记录中确认完成「" + name + "」", List.of(), null));
        }
        if (nodes.isEmpty()) {
            // 文档无明显步骤结构时给出通用三节点模板
            nodes.add(new AiSchemas.TemplateParseResult.Node(
                    "node_1", "需求确认", "start", "需求已与干系人确认", List.of("项目负责人"), null));
            nodes.add(new AiSchemas.TemplateParseResult.Node(
                    "node_2", "执行落地", "normal", "执行结果已反馈", List.of("执行人员"), null));
            nodes.add(new AiSchemas.TemplateParseResult.Node(
                    "node_3", "验收归档", "end", "验收通过", List.of("项目负责人"), null));
        }
        String flowName = ctx.docName() == null ? "未命名流程" : ctx.docName().replaceAll("\\.[^.]+$", "");
        return new AiSchemas.TemplateParseResult(flowName, "由文档解析生成（Mock 模式）",
                nodes, List.of(), List.of());
    }

    private List<NodeInfo> loadNodes(String nodesJson) {
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(
                    nodesJson == null ? "[]" : nodesJson, new TypeReference<>() {
                    });
            List<NodeInfo> nodes = new ArrayList<>();
            for (Map<String, Object> n : raw) {
                nodes.add(new NodeInfo(
                        String.valueOf(n.getOrDefault("key", "node_" + n.get("name"))),
                        String.valueOf(n.getOrDefault("name", n.get("key")))));
            }
            return nodes;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String snippet(String content) {
        return content == null ? "" : (content.length() > 60 ? content.substring(0, 60) + "…" : content);
    }

    private record NodeInfo(String key, String name) {
    }
}
