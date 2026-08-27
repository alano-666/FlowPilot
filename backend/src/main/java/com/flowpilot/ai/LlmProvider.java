package com.flowpilot.ai;

import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;

import java.util.List;

/**
 * 大模型 Provider 抽象。三种实现行为一致：
 *  - {@link AnthropicLlmProvider}：Anthropic 官方 SDK，类型化结构化输出；
 *  - {@link OpenAiLlmProvider}：OpenAI 兼容协议（DeepSeek/通义/Ollama 等）；
 *  - {@link MockLlmProvider}：无密钥演示模式，确定性模拟。
 */
public interface LlmProvider {

    String name();

    /** 项目流程状态识别（PRD 3.3） */
    AiSchemas.AnalysisResult analyzeProject(AnalysisContext ctx);

    /** 流程文档解析建模（PRD 3.1） */
    AiSchemas.TemplateParseResult parseTemplate(TemplateParseContext ctx);

    /** 项目状态识别上下文 */
    record AnalysisContext(FlowTemplate template, Project project, List<Message> messages) {
    }

    /** 流程文档解析上下文 */
    record TemplateParseContext(String docName, String sourceText) {
    }
}
