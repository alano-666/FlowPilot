package com.flowpilot.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析器测试：TXT、Markdown、Word(docx)。
 */
class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    void parseTxt() {
        String text = parser.parse("流程.txt", "第一步：开通策略\r\n\r\n第二步：远程安装".getBytes(StandardCharsets.UTF_8));
        assertTrue(text.contains("第一步：开通策略"));
        assertFalse(text.contains("\n\n\n"));
    }

    @Test
    void parseDocxWithTable() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("远程安装流程");
            var table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("节点");
            table.getRow(0).getCell(1).setText("标准");
            table.getRow(1).getCell(0).setText("开通策略");
            table.getRow(1).getCell(1).setText("策略生效");
            doc.write(out);
            String text = parser.parse("流程.docx", out.toByteArray());
            assertTrue(text.contains("远程安装流程"));
            assertTrue(text.contains("开通策略"));
            assertTrue(text.contains("策略生效"));
        }
    }

    @Test
    void rejectsUnsupported() {
        var e = assertThrows(com.flowpilot.common.BizException.class,
                () -> parser.parse("a.zip", "x".getBytes()));
        assertTrue(e.getMessage().contains("不支持的文档格式"));
    }

    @Test
    void extractsTextBoxContent() {
        // 模拟 Word 正文放在文本框里的场景（POI 高层 API 提取不到，需底层 XML 兜底）
        String xml = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>标题：软件交付流程</w:t></w:r></w:p>
                    <w:p><w:r><w:pict>
                      <v:shape><v:textbox><w:txbxContent>
                        <w:p><w:r><w:t>1. 需求确认：产品经理负责</w:t></w:r></w:p>
                        <w:p><w:r><w:t>2. 开发实施：研发负责</w:t></w:r></w:p>
                      </w:txbxContent></v:textbox></v:shape>
                    </w:pict></w:r></w:p>
                  </w:body>
                </w:document>""";
        String boxText = DocumentParser.extractTextBoxText(xml);
        assertTrue(boxText.contains("需求确认：产品经理负责"));
        assertTrue(boxText.contains("开发实施：研发负责"));
    }

    @Test
    void emptyXmlSafe() {
        assertEquals("", DocumentParser.extractTextBoxText(null));
        assertEquals("", DocumentParser.extractTextBoxText(""));
        assertEquals("", DocumentParser.extractTextBoxText("<w:document/>"));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(com.flowpilot.common.BizException.class,
                () -> parser.parse("a.txt", new byte[0]));
    }
}
