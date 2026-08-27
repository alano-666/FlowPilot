package com.flowpilot.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * AI 结构化输出的数据 Schema。
 *
 * 设计说明：
 *  - Anthropic Provider 使用官方 SDK 的 outputConfig(Class) 类型化结构化输出，
 *    由 SDK 根据本类的 Jackson 注解自动推导 JSON Schema 并严格校验；
 *  - OpenAI 兼容 Provider 将同一 Schema 序列化为 response_format=json_schema；
 *  - Mock Provider 直接构造本类对象，保证三种 Provider 行为一致。
 */
public final class AiSchemas {

    private AiSchemas() {
    }

    /**
     * 项目流程状态识别结果（PRD 3.3.3 输出示例同构）。
     */
    public record AnalysisResult(
            @JsonPropertyDescription("项目当前所处流程节点的 key，必须是模板节点 key 之一；若流程已全部完成则为最后一个节点 key")
            String current_node_key,
            @JsonPropertyDescription("已确认完成的节点 key 列表")
            List<String> completed_nodes,
            @JsonPropertyDescription("项目整体进度百分比，取值 0~1 之间的小数，如 0.4 表示 40%")
            Double progress,
            @JsonPropertyDescription("风险状态：normal(正常)/warning(预警)/blocked(卡顿)")
            String risk_status,
            @JsonPropertyDescription("支撑本次判断的消息证据列表，每条证据必须引用真实存在的消息序号")
            List<Evidence> evidence,
            @JsonPropertyDescription("新识别出的干系人及其在节点中的角色，没有新发现则为空数组")
            List<StakeholderUpdate> stakeholders_update,
            @JsonPropertyDescription("项目当前风险点列表，用中文描述，没有则为空数组")
            List<String> risks,
            @JsonPropertyDescription("建议的下一步行动，用中文描述")
            String suggested_next_action,
            @JsonPropertyDescription("聊天中出现但模板未定义的临时节点，供人工确认后补充进模板")
            List<TempNode> temp_nodes,
            @JsonPropertyDescription("一句话概括项目最新动态，展示在看板卡片上")
            String latest_activity
    ) {
        public record Evidence(
                @JsonPropertyDescription("消息在本次分析上下文中的序号，从 0 开始")
                int message_index,
                @JsonPropertyDescription("该证据支撑的节点 key")
                String node_key,
                @JsonPropertyDescription("证据摘要，如：客户IT回复策略已生效")
                String summary,
                @JsonPropertyDescription("置信度 0~1")
                Double confidence
        ) {
        }

        public record StakeholderUpdate(
                @JsonPropertyDescription("干系人所属节点 key")
                String node_key,
                @JsonPropertyDescription("在流程中的角色，如：客户IT")
                String role,
                @JsonPropertyDescription("真实姓名")
                String name,
                @JsonPropertyDescription("联系方式类型：feishu/wecom/wechat")
                String contact_type,
                @JsonPropertyDescription("联系方式标识：飞书open_id/企微userid/微信号")
                String contact_id
        ) {
        }

        public record TempNode(
                @JsonPropertyDescription("临时节点名称")
                String name,
                @JsonPropertyDescription("判定为临时节点的依据")
                String reason
        ) {
        }
    }

    /**
     * 流程文档解析建模结果（PRD 3.1.3 示例同构）。
     */
    public record TemplateParseResult(
            @JsonPropertyDescription("流程名称，如：远程安装设备")
            String flow_name,
            @JsonPropertyDescription("流程简要说明")
            String description,
            @JsonPropertyDescription("流程节点列表，按执行顺序排列")
            List<Node> nodes,
            @JsonPropertyDescription("分支规则列表，可为空数组")
            List<Branch> branches,
            @JsonPropertyDescription("专业词汇表，可为空数组")
            List<GlossaryItem> glossary
    ) {
        public record Node(
                @JsonPropertyDescription("节点唯一 key，英文小写下划线风格，如 open_policy")
                String key,
                @JsonPropertyDescription("节点显示名称，如：开通策略")
                String name,
                @JsonPropertyDescription("节点类型：normal(普通节点)/decision(判断节点)/start(开始)/end(结束)")
                String type,
                @JsonPropertyDescription("节点完成标准，描述怎样算完成")
                String completion_criteria,
                @JsonPropertyDescription("该节点责任角色列表，如：客户IT")
                List<String> responsible_roles,
                @JsonPropertyDescription("SLA 时效要求（小时），无要求可为 null")
                Double sla_hours
        ) {
        }

        public record Branch(
                @JsonPropertyDescription("分支触发条件，如：如果客户已购买远程授权")
                String condition,
                @JsonPropertyDescription("分支起点节点 key")
                String from,
                @JsonPropertyDescription("分支目标节点 key")
                String to
        ) {
        }

        public record GlossaryItem(
                @JsonPropertyDescription("专业词汇")
                String term,
                @JsonPropertyDescription("同义词列表，如：policy、授权策略")
                List<String> synonyms,
                @JsonPropertyDescription("词汇解释")
                String explanation
        ) {
        }
    }
}
