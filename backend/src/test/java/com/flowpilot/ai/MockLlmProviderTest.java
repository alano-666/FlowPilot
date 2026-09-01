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

    @Test
    void buildSenderMapExtractsRealOpenIds() {
        Message m1 = new Message();
        m1.setSenderId("ou_real_001");
        m1.setSenderName("陈总");
        m1.setSentAt(LocalDateTime.now());
        Message m2 = new Message();
        m2.setSenderId("ou_cast_wang");   // 剧本假 ID 不进入映射
        m2.setSenderName("王五");
        m2.setSentAt(LocalDateTime.now());
        Message m3 = new Message();
        m3.setSenderId(null);
        m3.setSenderName("无身份");
        m3.setSentAt(LocalDateTime.now());
        var map = com.flowpilot.service.AnalysisService.buildSenderMap(List.of(m1, m2, m3));
        assertEquals("ou_real_001", map.get("陈总"));
        assertNull(map.get("王五"));      // 假 ID 被排除
        assertNull(map.get("无身份"));    // 无 senderId 不映射
    }

    @Test
    void fuzzyFindSenderHandlesNicknameVariations() {
        var map = java.util.Map.of("星辰-陈总", "ou_real_001", "孙浩(研发)", "ou_real_002");
        assertEquals("ou_real_001",
                com.flowpilot.service.AnalysisService.fuzzyFindSender(map, "陈总"));
        assertEquals("ou_real_002",
                com.flowpilot.service.AnalysisService.fuzzyFindSender(map, "孙浩"));
        assertNull(com.flowpilot.service.AnalysisService.fuzzyFindSender(map, "林"));
        assertNull(com.flowpilot.service.AnalysisService.fuzzyFindSender(map, null));
    }

    @Test
    void buildRoleMapFromGroupAnnouncement() {
        Message m = new Message();
        m.setContent("角色分工：王敏·产品经理、陈强·研发负责人、周凯·运维，林总是甲方负责人");
        m.setSentAt(LocalDateTime.now());
        var map = com.flowpilot.service.AnalysisService.buildRoleMap(List.of(m));
        assertEquals("产品经理", map.get("王敏"));
        assertEquals("研发负责人", map.get("陈强"));
        assertEquals("运维", map.get("周凯"));
        assertEquals("甲方负责人", map.get("林总"));
    }

    @Test
    void extractPrefixedStakeholdersFromScriptLines() {
        Message m1 = new Message();
        m1.setContent("【林总】验收演示约明天下午三点");
        m1.setSenderId("ou_real_888");
        m1.setSentAt(LocalDateTime.now());
        Message m2 = new Message();
        m2.setContent("【王敏】收到，我马上安排");
        m2.setSenderId("ou_real_999");
        m2.setSentAt(LocalDateTime.now());
        Message announce = new Message();
        announce.setContent("王敏·产品经理，林总是甲方负责人");
        announce.setSentAt(LocalDateTime.now());
        var list = com.flowpilot.service.AnalysisService.extractPrefixedStakeholders(
                List.of(announce, m1, m2));
        assertEquals(2, list.size());
        assertEquals("林总", list.get(0).name());
        assertEquals("甲方负责人", list.get(0).role());
        assertEquals("ou_real_888", list.get(0).contact_id());
        assertEquals("王敏", list.get(1).name());
        assertEquals("产品经理", list.get(1).role());
    }

    @Test
    void extractPrefixedIgnoresSystemMarkers() {
        Message m = new Message();
        m.setContent("【风险】节点超时了");
        m.setSentAt(LocalDateTime.now());
        var list = com.flowpilot.service.AnalysisService.extractPrefixedStakeholders(List.of(m));
        assertTrue(list.isEmpty());
    }
}
