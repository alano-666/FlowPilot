package com.flowpilot;

import com.flowpilot.config.FlowPilotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * FlowPilot 流程领航员 - 飞书 AI 流程跟踪助手后端服务。
 *
 * 启动方式：
 *   cd backend && mvn spring-boot:run
 *   或 java -jar target/flowpilot.jar
 * 前端页面：http://localhost:8080/（首次登录 admin/admin123）
 */
@SpringBootApplication
@EnableConfigurationProperties(FlowPilotProperties.class)
public class FlowPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowPilotApplication.class, args);
    }
}
