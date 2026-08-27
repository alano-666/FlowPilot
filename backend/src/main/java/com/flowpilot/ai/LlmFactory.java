package com.flowpilot.ai;

import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM Provider 工厂：按 flowpilot.ai.provider 配置实例化，
 * 未配置密钥时始终可用 mock（保证开箱即跑）。
 */
@Component
public class LlmFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmFactory.class);

    private final FlowPilotProperties props;
    private volatile LlmProvider provider;

    public LlmFactory(FlowPilotProperties props) {
        this.props = props;
    }

    public LlmProvider get() {
        if (provider != null) {
            return provider;
        }
        synchronized (this) {
            if (provider != null) {
                return provider;
            }
            String name = props.getAi().getProvider() == null ? "mock" : props.getAi().getProvider().trim().toLowerCase();
            provider = switch (name) {
                case "anthropic" -> new AnthropicLlmProvider(props);
                case "openai" -> new OpenAiLlmProvider(props);
                case "mock" -> new MockLlmProvider();
                default -> throw new BizException(50020, "未知的 AI Provider: " + name + "（可选 mock/anthropic/openai）");
            };
            log.info("LLM Provider 已启用: {}", provider.name());
            return provider;
        }
    }
}
