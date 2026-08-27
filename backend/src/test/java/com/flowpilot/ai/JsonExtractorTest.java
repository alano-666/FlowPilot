package com.flowpilot.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 宽松 JSON 提取器测试：代码块包裹、前后缀文字、尾部逗号、单引号、未知字段。
 */
class JsonExtractorTest {

    record Sample(String flow_name, List<SampleNode> nodes) {
    }

    record SampleNode(String key, String name) {
    }

    @Test
    void extractsFromCodeFence() {
        String raw = "```json\n{\"flow_name\":\"测试流程\",\"nodes\":[{\"key\":\"a\",\"name\":\"步骤A\"}]}\n```";
        Sample s = JsonExtractor.extract(raw, Sample.class, "测试");
        assertEquals("测试流程", s.flow_name());
        assertEquals(1, s.nodes().size());
        assertEquals("a", s.nodes().get(0).key());
    }

    @Test
    void extractsWithSurroundingText() {
        String raw = "好的，以下是结果：\n{\"flow_name\":\"流程X\",\"nodes\":[]}\n希望有帮助";
        Sample s = JsonExtractor.extract(raw, Sample.class, "测试");
        assertEquals("流程X", s.flow_name());
    }

    @Test
    void repairsTrailingCommasAndSingleQuotes() {
        String raw = "{'flow_name':'流程Y',\n'nodes':[{'key':'b','name':'步骤B',},],}";
        Sample s = JsonExtractor.extract(raw, Sample.class, "测试");
        assertEquals("流程Y", s.flow_name());
        assertEquals("b", s.nodes().get(0).key());
    }

    @Test
    void ignoresUnknownFields() {
        String raw = "{\"flow_name\":\"流程Z\",\"nodes\":[{\"id\":\"1\",\"name\":\"步骤\"}],\"extra\":1}";
        Sample s = JsonExtractor.extract(raw, Sample.class, "测试");
        assertEquals("流程Z", s.flow_name());
        // 未知字段 id 被忽略，key 缺失为 null（由上层校验兜底）
        assertNull(s.nodes().get(0).key());
    }

    @Test
    void invalidJsonThrowsBizException() {
        assertThrows(com.flowpilot.common.BizException.class,
                () -> JsonExtractor.extract("这不是JSON", Sample.class, "测试"));
    }

    @Test
    void isValidObjectDetection() {
        assertTrue(JsonExtractor.isValidObject("{\"a\":1}"));
        assertTrue(JsonExtractor.isValidObject("```json\n[1,2]\n```"));
        assertFalse(JsonExtractor.isValidObject("纯文字"));
    }
}
