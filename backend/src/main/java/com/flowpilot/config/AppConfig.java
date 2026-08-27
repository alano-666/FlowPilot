package com.flowpilot.config;

import com.flowpilot.auth.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 与应用基础设施装配。
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public AppConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/webhooks/**", "/api/v1/auth/login");
    }

    /**
     * AI 分析线程池：并发度可配（默认 8，PRD 要求支持 ≥50）。
     */
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor(FlowPilotProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getAi().getParallelism());
        executor.setMaxPoolSize(Math.max(props.getAi().getParallelism(), 50));
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.initialize();
        return executor;
    }
}
