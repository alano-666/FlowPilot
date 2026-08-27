package com.flowpilot.importer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微信聊天记录解析器测试：覆盖复制格式、CSV、多行内容、时间变体。
 */
class WeChatRecordParserTest {

    private final WeChatRecordParser parser = new WeChatRecordParser();

    @Test
    void parseCopyFormat() {
        String text = """
                2026-08-27 10:30:15 张三
                策略已经生效了
                后台可以看到
                2026-08-27 10:31:02 李四: 收到，我马上发起远程邀请
                """;
        var result = parser.parse("测试群.txt", text);
        assertEquals(2, result.messages().size());
        assertEquals("张三", result.messages().get(0).sender());
        assertEquals("策略已经生效了\n后台可以看到", result.messages().get(0).content());
        assertEquals("李四", result.messages().get(1).sender());
        assertEquals("收到，我马上发起远程邀请", result.messages().get(1).content());
        assertEquals(2026, result.messages().get(0).sentAt().getYear());
    }

    @Test
    void parseCsvWithHeader() {
        String csv = """
                时间,发送者,内容
                2026-08-27 10:30:15,张三,策略已生效
                2026-08-27 10:31:02,李四,收到
                """;
        var result = parser.parse("记录.csv", csv);
        assertEquals("CSV", result.format());
        assertEquals(2, result.messages().size());
        assertEquals("张三", result.messages().get(0).sender());
        assertEquals("策略已生效", result.messages().get(0).content());
    }

    @Test
    void parseCsvHeaderInAnyOrder() {
        String csv = """
                内容,昵称,日期
                你好,"李四",2026/8/27 10:31
                """;
        var result = parser.parse("记录.csv", csv);
        assertEquals(1, result.messages().size());
        assertEquals("李四", result.messages().get(0).sender());
        assertEquals("你好", result.messages().get(0).content());
    }

    @Test
    void parseChineseDateAndTimeOnly() {
        String text = "2026年8月27日 10:30 张三: 开工\n10:31:02 李四: 好的";
        var result = parser.parse("x.txt", text);
        assertEquals(2, result.messages().size());
        assertNotNull(result.messages().get(1).sentAt());
    }

    @Test
    void emptyInputGivesWarning() {
        var result = parser.parse("x.txt", "这不是聊天记录\n只是说明文字");
        assertTrue(result.messages().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void csvSplitterHandlesQuotes() {
        var rows = WeChatRecordParser.splitCsv("a,\"b,c\",d\ne,f,g");
        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).length);
        assertEquals("b,c", rows.get(0)[1]);
    }

    @Test
    void parseTimeVariants() {
        assertNotNull(WeChatRecordParser.parseTime("2026-08-27 10:30:15"));
        assertNotNull(WeChatRecordParser.parseTime("2026/8/27 10:30"));
        assertNotNull(WeChatRecordParser.parseTime("08-27 10:30:15"));
        assertNotNull(WeChatRecordParser.parseTime("10:30:15"));
        assertNull(WeChatRecordParser.parseTime("不是时间"));
    }
}
