package com.flowpilot.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 中文导出金丝雀测试：验证 OpenPDF + itext-asian 字体链路可用。
 * 若本测试失败，说明中文字体依赖有误（不影响其他功能，但 PDF 导出会乱码）。
 */
class PdfExporterTest {

    @Test
    void buildChinesePdf() {
        PdfExporter exporter = new PdfExporter();
        ReportService.ReportSummary summary = new ReportService.ReportSummary(
                "周报", LocalDateTime.now().minusDays(7), LocalDateTime.now(),
                Map.of(
                        "totalProjects", 3, "activeProjects", 2, "riskProjectCount", 1, "riskRate", 33.3,
                        "flowStats", List.of(Map.of("flowName", "远程安装设备", "projectCount", 2, "avgHours", 12.5)),
                        "bottleneckNodes", List.of(Map.of("node", "远程安装设备/开启远程权限", "stuckCount", 2)),
                        "responseStats", List.of()),
                List.of(Map.of("code", "P20260827001", "name", "上海某某科技远程安装",
                        "customerName", "上海某某科技", "templateName", "远程安装设备",
                        "currentNodeKey", "enable_remote", "progress", 40.0,
                        "riskStatus", "WARNING", "lastAnalyzedAt", "2026-08-27T10:00:00")));
        byte[] pdf = exporter.buildPdf(summary);
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "PDF 应包含内容");
        // PDF 头部魔数
        assertEquals('%', pdf[0]);
        assertEquals("PDF", new String(pdf, 1, 3));
    }
}
