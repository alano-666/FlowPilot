package com.flowpilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;

import java.util.List;

/**
 * Prompt 构建器：将模板、项目状态、聊天记录渲染为大模型上下文。
 * 原则：指令与模板结构放 system（稳定前缀），易变的消息与状态放 user。
 */
public final class PromptBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromptBuilder() {
    }

    public static String analysisSystem(FlowTemplate template) {
        return """
                你是「FlowPilot 流程领航员」的项目进度识别引擎，服务于企业业务流程跟踪场景。
                你的任务：根据给定的业务流程模板和项目近期聊天记录，识别项目实时状态。

                判断规则：
                1. 严格基于聊天记录中的事实证据判断，禁止臆测；证据不足时保持原状态并给出较低置信度。
                2. 节点完成标准（completion_criteria）是判断节点是否完成的唯一依据，注意同义词匹配。
                3. 聊天措辞往往是口语化、不严谨的（如「应该没问题了」「差不多完成了」「全部用例通过了」），
                   只要消息内容实质性满足了节点的完成标准，就应判定该节点完成，不要因措辞随意而拒绝确认。
                4. current_node_key 取第一个尚未完成节点的 key；全部完成则取最后一个节点 key。
                5. progress 按完成节点数/总节点数估算，并结合分支情况微调。
                6. 干系人抽取：识别聊天中真实出现的人名与角色、平台（feishu/wecom/wechat）；
                   若消息中出现「【干系人】姓名|角色|平台|ID」格式的标记，必须将其解析为干系人更新
                   （name=姓名, role=角色, contact_type=平台, contact_id=ID）。
                7. 风险识别：SLA 超时、关键干系人未回复、节点卡顿、结论含糊但流程已先行推进等，用中文描述。
                8. 聊天中出现了模板未定义的流程节点时，放入 temp_nodes 供人工确认。
                9. 输出必须为 JSON，字段与要求完全一致。
                """ + "\n\n业务流程模板（JSON）：\n" + compactTemplate(template);
    }

    public static String analysisUser(Project project, List<Message> messages, int maxMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("项目当前状态：\n");
        sb.append("- 项目名称：").append(project.getName()).append('\n');
        sb.append("- 当前节点：").append(project.getCurrentNodeKey() == null ? "未知" : project.getCurrentNodeKey()).append('\n');
        sb.append("- 当前进度：").append(project.getProgress()).append('\n');
        sb.append("- 上次分析时间：").append(project.getLastAnalyzedAt() == null ? "从未分析" : project.getLastAnalyzedAt()).append('\n');
        sb.append("\n近期聊天记录（按时间排序，序号从 0 开始，证据引用请使用 message_index）：\n");
        List<Message> sliced = sliceMessages(messages, maxMessages);
        int index = 0;
        for (Message m : sliced) {
            sb.append(index++).append(". [").append(m.getSentAt()).append("] ")
                    .append(m.getSenderName() == null || m.getSenderName().isBlank() ? "未知" : m.getSenderName())
                    .append(": ").append(truncate(m.getContent(), 200)).append('\n');
        }
        return sb.toString();
    }

    public static String templateParseSystem() {
        return """
                你是「FlowPilot 流程领航员」的业务流程建模引擎。
                你的任务：解析用户上传的业务流程文档，抽取结构化的流程模型。

                抽取要求：
                1. 节点按执行顺序排列，key 使用英文小写下划线风格（如 open_policy）。
                2. completion_criteria 用可验证的客观描述（如：客户后台显示策略已生效）。
                3. responsible_roles 使用流程中的角色名（如：客户IT、我方技术支持）。
                4. 分支条件完整抽取（如：如果客户已购买远程授权）。
                5. 专业词汇与同义词完整收录，方便后续聊天记录同义匹配。
                6. 时效要求必须转换为 sla_hours 数值：如「24 小时内完成」→ sla_hours=24，
                   「48 小时内完成」→ sla_hours=48；文档未提及时效时可为 null。
                7. 文档未提及的其他字段可为空，但不要编造。
                8. 输出必须为 JSON，字段与要求完全一致。
                """;
    }

    public static String templateParseUser(String docName, String sourceText) {
        return "文档名称：" + docName + "\n\n文档内容：\n" + truncate(sourceText, 60000);
    }

    /** 模板精简序列化：只保留分析所需字段 */
    private static String compactTemplate(FlowTemplate t) {
        try {
            // 直接透传结构化 JSON（nodes/branches/glossary 本身已是规范 JSON）
            return MAPPER.writeValueAsString(java.util.Map.of(
                    "flow_name", t.getName(),
                    "description", t.getDescription() == null ? "" : t.getDescription(),
                    "nodes", MAPPER.readTree(t.getNodesJson() == null ? "[]" : t.getNodesJson()),
                    "branches", MAPPER.readTree(t.getBranchesJson() == null ? "[]" : t.getBranchesJson()),
                    "glossary", MAPPER.readTree(t.getGlossaryJson() == null ? "[]" : t.getGlossaryJson())));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** 消息截断策略（证据 message_index 与 PromptBuilder 渲染保持一致，勿单独修改） */
    public static List<Message> sliceMessages(List<Message> messages, int maxMessages) {
        return messages.size() > maxMessages
                ? messages.subList(messages.size() - maxMessages, messages.size())
                : messages;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(截断)";
    }
}
