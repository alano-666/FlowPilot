package com.flowpilot.service;

import com.flowpilot.common.BizException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * PDF 报告导出（OpenPDF）。
 * 中文字体使用 itext-asian 内置的 STSong-Light CID 字体，
 * 无需外部字体文件，跨平台可用。
 */
@Component
public class PdfExporter {

    private static final String CN_FONT = "STSong-Light";
    private static final String CN_ENCODING = "UniGB-UCS2-H";

    private Font titleFont() {
        return new Font(loadCnBaseFont(), 18, Font.BOLD);
    }

    private Font headFont() {
        return new Font(loadCnBaseFont(), 11, Font.BOLD);
    }

    private Font bodyFont() {
        return new Font(loadCnBaseFont(), 10, Font.NORMAL);
    }

    /** 加载中文字体；失败时回退内置 Helvetica（会损失中文显示，但不阻断导出） */
    private BaseFont loadCnBaseFont() {
        try {
            return BaseFont.createFont(CN_FONT, CN_ENCODING, BaseFont.NOT_EMBEDDED);
        } catch (java.io.IOException e) {
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (java.io.IOException ex) {
                throw new BizException(50012, "PDF 字体加载失败: " + ex.getMessage());
            }
        }
    }

    public byte[] buildPdf(ReportService.ReportSummary s) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("FlowPilot " + s.period(), titleFont());
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            doc.add(title);
            Paragraph range = new Paragraph(
                    "统计区间：" + (s.from() == null ? "—" : s.from().toLocalDate())
                            + " ~ " + (s.to() == null ? "—" : s.to().toLocalDate()),
                    bodyFont());
            range.setAlignment(Element.ALIGN_CENTER);
            range.setSpacingAfter(16);
            doc.add(range);

            // 概览
            PdfPTable overview = new PdfPTable(4);
            overview.setWidthPercentage(100);
            String[] heads = {"项目总数", "进行中", "风险项目", "超时率"};
            for (String h : heads) {
                overview.addCell(cell(h, headFont()));
            }
            overview.addCell(cell(String.valueOf(s.stats().get("totalProjects")), bodyFont()));
            overview.addCell(cell(String.valueOf(s.stats().get("activeProjects")), bodyFont()));
            overview.addCell(cell(String.valueOf(s.stats().get("riskProjectCount")), bodyFont()));
            overview.addCell(cell(s.stats().get("riskRate") + "%", bodyFont()));
            doc.add(overview);

            // 卡点节点
            doc.add(new Paragraph("\n高频卡点节点", headFont()));
            PdfPTable bottleneck = new PdfPTable(2);
            bottleneck.setWidthPercentage(100);
            bottleneck.addCell(cell("节点", headFont()));
            bottleneck.addCell(cell("滞留项目数", headFont()));
            for (Map<String, Object> b : (List<Map<String, Object>>) s.stats().get("bottleneckNodes")) {
                bottleneck.addCell(cell(String.valueOf(b.get("node")), bodyFont()));
                bottleneck.addCell(cell(String.valueOf(b.get("stuckCount")), bodyFont()));
            }
            doc.add(bottleneck);

            // 流程耗时
            doc.add(new Paragraph("\n各流程平均执行耗时", headFont()));
            PdfPTable flow = new PdfPTable(3);
            flow.setWidthPercentage(100);
            flow.addCell(cell("流程名称", headFont()));
            flow.addCell(cell("项目数", headFont()));
            flow.addCell(cell("平均耗时(小时)", headFont()));
            for (Map<String, Object> f : (List<Map<String, Object>>) s.stats().get("flowStats")) {
                flow.addCell(cell(String.valueOf(f.get("flowName")), bodyFont()));
                flow.addCell(cell(String.valueOf(f.get("projectCount")), bodyFont()));
                flow.addCell(cell(String.valueOf(f.get("avgHours")), bodyFont()));
            }
            doc.add(flow);

            // 项目明细
            doc.add(new Paragraph("\n项目明细", headFont()));
            PdfPTable detail = new PdfPTable(6);
            detail.setWidthPercentage(100);
            String[] cols = {"编号", "项目", "客户", "流程", "当前节点", "进度"};
            for (String c : cols) {
                detail.addCell(cell(c, headFont()));
            }
            for (Map<String, Object> p : s.projects()) {
                detail.addCell(cell(String.valueOf(p.get("code")), bodyFont()));
                detail.addCell(cell(String.valueOf(p.get("name")), bodyFont()));
                detail.addCell(cell(p.get("customerName") == null ? "" : String.valueOf(p.get("customerName")), bodyFont()));
                detail.addCell(cell(String.valueOf(p.get("templateName")), bodyFont()));
                detail.addCell(cell(p.get("currentNodeKey") == null ? "" : String.valueOf(p.get("currentNodeKey")), bodyFont()));
                detail.addCell(cell(p.get("progress") + "%", bodyFont()));
            }
            doc.add(detail);

            Paragraph footer = new Paragraph("由 FlowPilot 自动生成", bodyFont());
            footer.setSpacingBefore(24);
            doc.add(footer);
            doc.close();
            return out.toByteArray();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(50012, "PDF 导出失败: " + e.getMessage());
        }
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(5);
        return cell;
    }
}
