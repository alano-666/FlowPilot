package com.flowpilot.ai;

import com.flowpilot.model.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock Provider 测试：标记识别、节点推进、模板解析兜底。
 */
class MockLlmProviderTest {

    private final MockLlmProvider provider = new MockLlmProvider();

    private Message msg(String content, int hoursAgo) {
        Message m = new Message();
        m.setContent(content);
        m.setSenderName("张三");
        m.setSentAt(LocalDateTime.now().minusHours(hoursAgo));
        return m;
    }

    private com.flowpilot.model.FlowTemplate template() {
        com.flowpilot.model.FlowTemplate t = new com.flowpilot.model.FlowTemplate();
        t.setName("远程安装设备");
        t.setNodesJson("""
                [{"key":"open_policy","name":"开通策略"},
                 {"key":"enable_remote","name":"开启远程权限"},
                 {"key":"acceptance","name":"测试验收"}]
                """);
        t.setBranchesJson("[]");
        t.setGlossaryJson("[]");
        return t;
    }

    private com.flowpilot.model.Project project() {
        com.flowpilot.model.Project p = new com.flowpilot.model.Project();
        p.setName("测试项目");
        p.setCurrentNodeKey("open_policy");
        p.setProgress(0.0);
        return p;
    }

    @Test
    void recognizesCompletedNodesAndProgress() {
        var ctx = new LlmProvider.AnalysisContext(template(), project(), List.of(
                msg("【完成】开通策略 open_policy 策略已生效", 3),
                msg("【干系人】李四|我方技术支持|feishu|ou_lisi", 2),
                msg("【风险】客户未确认远程权限", 1)));
        var r = provider.analyzeProject(ctx);
        assertEquals(List.of("open_policy"), r.completed_nodes());
        assertEquals("enable_remote", r.current_node_key());
        assertEquals(1.0 / 3, r.progress(), 0.001);
        assertEquals(1, r.stakeholders_update().size());
        assertEquals(1, r.risks().size());
        assertEquals("warning", r.risk_status());
    }

    @Test
    void allCompletedKeepsLastNode() {
        var ctx = new LlmProvider.AnalysisContext(template(), project(), List.of(
                msg("【完成】open_policy", 5),
                msg("【完成】enable_remote", 4),
                msg("【完成】acceptance 验收通过", 3)));
        var r = provider.analyzeProject(ctx);
        assertEquals("acceptance", r.current_node_key());
        assertEquals(1.0, r.progress(), 0.001);
    }

    @Test
    void noEvidenceKeepsCurrentState() {
        var ctx = new LlmProvider.AnalysisContext(template(), project(),
                List.of(msg("大家早上好", 1)));
        var r = provider.analyzeProject(ctx);
        assertEquals("open_policy", r.current_node_key());
        assertEquals(0.0, r.progress());
        assertEquals("normal", r.risk_status());
    }

    @Test
    void parseTemplateFromNumberedLines() {
        var ctx = new LlmProvider.TemplateParseContext("流程.md",
                "1. 需求确认\n2. 执行落地\n3. 验收归档\n");
        var r = provider.parseTemplate(ctx);
        assertEquals("流程", r.flow_name());
        assertEquals(3, r.nodes().size());
        assertEquals("需求确认", r.nodes().get(0).name());
        assertEquals("start", r.nodes().get(0).type());
    }

    @Test
    void parseTemplateFallsBackToDefault() {
        var ctx = new LlmProvider.TemplateParseContext("说明.md", "这是一份没有步骤的文档");
        var r = provider.parseTemplate(ctx);
        assertFalse(r.nodes().isEmpty());
    }

    @Test
    void slicingKeepsOrderAndCap() {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(msg("m" + i, 10 - i));
        }
        assertEquals(3, PromptBuilder.sliceMessages(messages, 3).size());
        assertEquals("m7", PromptBuilder.sliceMessages(messages, 3).get(0).getContent());
        assertEquals(10, PromptBuilder.sliceMessages(messages, 100).size());
    }
}
