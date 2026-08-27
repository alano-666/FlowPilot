package com.flowpilot.service;

import com.flowpilot.common.BizException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 流程文档解析器（PRD 3.1.2 文档结构化解析第一步）：
 * 提取文档全文并清洗（去除空行、统一换行、限制长度），供 LLM 建模。
 * 支持：TXT / Markdown / Word(docx，含表格) / PDF（文本型）。
 * 注意：扫描版 PDF 需先 OCR，本解析器不处理（文档中已说明）。
 */
@Component
public class DocumentParser {

    public static final long MAX_TEXT_LENGTH = 500_000;

    public String parse(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BizException(40002, "文件内容为空");
        }
        String ext = extOf(fileName);
        String text = switch (ext) {
            case "txt", "md", "markdown", "text" -> new String(content, StandardCharsets.UTF_8);
            case "docx" -> parseDocx(content);
            case "pdf" -> parsePdf(content);
            case "doc" -> throw new BizException(40003, "暂不支持老版 .doc 格式，请另存为 .docx 后上传");
            default -> throw new BizException(40003, "不支持的文档格式: ." + ext + "（支持 txt/md/docx/pdf）");
        };
        return clean(text);
    }

    /** Word 解析：段落 + 表格（表格以 | 分隔行输出） */
    private String parseDocx(byte[] content) {
        try (InputStream in = new ByteArrayInputStream(content);
             XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (var item : doc.getBodyElements()) {
                if (item instanceof XWPFParagraph p) {
                    sb.append(p.getText()).append('\n');
                } else if (item instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder rowSb = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            rowSb.append(cell.getText().replace('\n', ' ')).append(" | ");
                        }
                        sb.append(rowSb).append('\n');
                    }
                }
            }
            return sb.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40004, "Word 文档解析失败: " + e.getMessage());
        }
    }

    /** PDF 解析（文本型 PDF；扫描件返回提示） */
    private String parsePdf(byte[] content) {
        try (PDDocument doc = PDDocument.load(content)) {
            if (doc.isEncrypted()) {
                throw new BizException(40004, "PDF 已加密，无法解析");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text.isBlank()) {
                throw new BizException(40004, "PDF 未提取到文本（可能是扫描件），请先 OCR 或改用 Word/Markdown 上传");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40004, "PDF 解析失败: " + e.getMessage());
        }
    }

    /** 清洗：去空行、控制字符、限制长度 */
    private String clean(String text) {
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n')
                .replace("﻿", "")
                .replaceAll("\\u0000", "")
                .lines()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (cleaned.length() > MAX_TEXT_LENGTH) {
            cleaned = cleaned.substring(0, (int) MAX_TEXT_LENGTH) + "\n...(内容过长已截断)";
        }
        if (cleaned.isBlank()) {
            throw new BizException(40002, "文档解析后内容为空");
        }
        return cleaned;
    }

    private String extOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
