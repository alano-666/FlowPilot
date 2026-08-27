package com.flowpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 Provider：适用于 DeepSeek、通义千问、Kimi、本地 Ollama 等
 * 所有实现了 /chat/completions 协议的服务，配置 base-url 即可切换。
 *
 * 结构化输出采用 response_format=json_schema + strict 校验。
 */
public class OpenAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;

    public OpenAiLlmProvider(FlowPilotProperties props) {
        FlowPilotProperties.OpenAi cfg = props.getAi().getOpenai();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new BizException(50020, "AI Provider=openai 但未配置 api-key");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.getAi().getTimeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .build();
        this.model = cfg.getModel();
        log.info("OpenAiLlmProvider 初始化完成, baseUrl={} model={}", cfg.getBaseUrl(), model);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public AiSchemas.AnalysisResult analyzeProject(AnalysisContext ctx) {
        String system = PromptBuilder.analysisSystem(ctx.template());
        String user = PromptBuilder.analysisUser(ctx.project(), ctx.messages(), 200);
        return chatJson(system, user, AiSchemas.AnalysisResult.class);
    }

    @Override
    public AiSchemas.TemplateParseResult parseTemplate(TemplateParseContext ctx) {
        String system = PromptBuilder.templateParseSystem();
        String user = PromptBuilder.templateParseUser(ctx.docName(), ctx.sourceText());
        return chatJson(system, user, AiSchemas.TemplateParseResult.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T chatJson(String system, String user, Class<T> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 8000);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", schema.getSimpleName().toLowerCase() + "_output",
                        "schema", mapper.convertValue(schema, JsonNode.class),
                        "strict", true)));

        try {
            JsonNode resp = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = resp.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) {
                throw new BizException(50010, "AI 返回内容为空");
            }
            return mapper.readValue(stripCodeFence(content), schema);
        } catch (BizException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("OpenAI 兼容接口调用失败: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(50010, "AI 调用失败(" + e.getStatusCode() + "): " + e.getMessage());
        } catch (Exception e) {
            log.error("OpenAI 兼容接口调用异常", e);
            throw new BizException(50010, "AI 调用异常: " + e.getMessage());
        }
    }

    /** 兼容部分模型返回 ```json 包裹的情况 */
    private String stripCodeFence(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return s;
    }
}
