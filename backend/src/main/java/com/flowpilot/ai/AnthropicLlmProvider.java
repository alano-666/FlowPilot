package com.flowpilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Anthropic Claude Provider：官方 Java SDK，支持两种输出模式：
 *
 *  1. 严格模式（strict-schema=true，默认）：官方 Anthropic API 服务端 Schema 校验，
 *     类型化结构化输出（builder.outputConfig(SchemaClass)），零手工解析；
 *  2. 宽松模式（strict-schema=false）：适用于第三方 Anthropic 兼容网关（中转站），
 *     网关通常不执行 Schema 校验，改用「提示词约束 + JsonExtractor 本地修复」。
 *
 * 两种模式均默认开启自适应思考（adaptive thinking）与提示词缓存；
 * 429/5xx 自动重试一次。
 * 由 {@link LlmFactory} 按配置构造，仅在 provider=anthropic 时实例化。
 */
public class AnthropicLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnthropicClient client;
    private final String model;
    private final boolean strictSchema;

    public AnthropicLlmProvider(FlowPilotProperties props) {
        FlowPilotProperties.Anthropic cfg = props.getAi().getAnthropic();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new BizException(50020, "AI Provider=anthropic 但未配置 api-key");
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(cfg.getApiKey())
                .timeout(Duration.ofSeconds(props.getAi().getTimeoutSeconds()));
        if (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank()) {
            builder.baseUrl(cfg.getBaseUrl());
        }
        this.client = builder.build();
        this.model = cfg.getModel();
        this.strictSchema = cfg.isStrictSchema();
        log.info("AnthropicLlmProvider 初始化完成, model={}, strictSchema={}", model, strictSchema);
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public AiSchemas.AnalysisResult analyzeProject(AnalysisContext ctx) {
        String system = PromptBuilder.analysisSystem(ctx.template());
        String user = PromptBuilder.analysisUser(ctx.project(), ctx.messages(), 200);
        return invoke(system, user, AiSchemas.AnalysisResult.class, "项目状态识别");
    }

    @Override
    public AiSchemas.TemplateParseResult parseTemplate(TemplateParseContext ctx) {
        String system = PromptBuilder.templateParseSystem();
        String user = PromptBuilder.templateParseUser(ctx.docName(), ctx.sourceText());
        return invoke(system, user, AiSchemas.TemplateParseResult.class, "流程模板解析");
    }

    // ---------- 双模式入口 ----------

    private <T> T invoke(String system, String user, Class<T> schema, String taskName) {
        return strictSchema
                ? strictStructured(system, user, schema, taskName)
                : lenientJson(system, user, schema, taskName);
    }

    /** 严格模式：类型化结构化输出（官方 API，服务端 Schema 校验） */
    private <T> T strictStructured(String system, String user, Class<T> schema, String taskName) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .cacheControl(CacheControlEphemeral.builder().build())
                .outputConfig(schema)
                .system(system)
                .addUserMessage(user)
                .build();
        try {
            return parseStructured(client.messages().create(params), taskName);
        } catch (AnthropicServiceException e) {
            if (retryable(e.statusCode())) {
                sleepBeforeRetry();
                try {
                    return parseStructured(client.messages().create(params), taskName);
                } catch (AnthropicServiceException retryEx) {
                    throw new BizException(50010, "AI " + taskName + " 调用失败(重试后): " + retryEx.getMessage());
                }
            }
            throw new BizException(50010, "AI " + taskName + " 调用失败: " + e.getMessage());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic {} 调用异常", taskName, e);
            throw new BizException(50010, "AI " + taskName + " 调用异常: " + e.getMessage());
        }
    }

    private <T> T parseStructured(StructuredMessage<T> response, String taskName) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new BizException(50010, "AI " + taskName + " 返回内容为空"));
    }

    /** 宽松模式：普通消息 + Schema 说明 + 本地 JSON 修复（第三方网关） */
    private <T> T lenientJson(String system, String user, Class<T> schema, String taskName) {
        String schemaDesc = schemaDescription(schema);
        String fullSystem = system + "\n\n输出格式要求（JSON Schema，必须严格遵守字段名与结构）：\n" + schemaDesc;
        String fullUser = user + "\n\n请直接输出符合上述 Schema 的 JSON 对象，不要输出任何解释文字或代码块标记。";

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .cacheControl(CacheControlEphemeral.builder().build())
                .system(fullSystem)
                .addUserMessage(fullUser)
                .build();
        try {
            return parseLenient(client.messages().create(params), schema, taskName);
        } catch (AnthropicServiceException e) {
            if (retryable(e.statusCode())) {
                sleepBeforeRetry();
                try {
                    return parseLenient(client.messages().create(params), schema, taskName);
                } catch (AnthropicServiceException retryEx) {
                    throw new BizException(50010, "AI " + taskName + " 调用失败(重试后): " + retryEx.getMessage());
                }
            }
            throw new BizException(50010, "AI " + taskName + " 调用失败: " + e.getMessage());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic {} 调用异常", taskName, e);
            throw new BizException(50010, "AI " + taskName + " 调用异常: " + e.getMessage());
        }
    }

    private <T> T parseLenient(Message response, Class<T> schema, String taskName) {
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .reduce("", (a, b) -> a + b);
        return JsonExtractor.extract(text, schema, taskName);
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 用 Jackson 将 Schema 类序列化为 JSON Schema 描述（附到提示词约束输出） */
    private String schemaDescription(Class<?> schema) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.valueToTree(schema));
        } catch (Exception e) {
            return schema.getSimpleName();
        }
    }
}
