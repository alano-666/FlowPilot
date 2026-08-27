package com.flowpilot.service;

import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.repository.MessageRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.common.BizException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告服务（PRD 3.9）：
 * 周报/月报聚合统计 + Excel/PDF/HTML 三种格式导出。
 * 核心统计维度：各流程平均执行耗时、高频卡点节点、干系人平均响应时长、项目超时率。
 */
@Service
public class ReportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ProjectRepository projectRepository;
    private final MessageRepository messageRepository;
    private final PdfExporter pdfExporter;

    public ReportService(ProjectRepository projectRepository, MessageRepository messageRepository,
                         PdfExporter pdfExporter) {
        this.projectRepository = projectRepository;
        this.messageRepository = messageRepository;
        this.pdfExporter = pdfExporter;
    }

    public record ReportSummary(String period, LocalDateTime from, LocalDateTime to,
                                Map<String, Object> stats, List<Map<String, Object>> projects) {
    }

    /** 生成统计摘要（周报/月报共用） */
    public ReportSummary buildSummary(String period, LocalDateTime from, LocalDateTime to) {
        List<Project> all = projectRepository.findByStatusNot(Project.Status.ARCHIVED,
                        org.springframework.data.domain.PageRequest.of(0, 10_000))
                .stream()
                // 报告区间内活跃过的项目：创建于区间结束前，且区间开始后有过更新
                .filter(p -> p.getCreatedAt().isBefore(to) && p.getUpdatedAt().isAfter(from))
                .toList();

        // 1. 各流程平均执行耗时（项目创建 → 最近活动）
        Map<String, List<Double>> flowDurations = new LinkedHashMap<>();
        for (Project p : all) {
            if (p.getTemplateName() == null) {
                continue;
            }
            double hours = Duration.between(p.getCreatedAt(), p.getLastActivityAt() == null
                    ? p.getCreatedAt() : p.getLastActivityAt()).toHours();
            flowDurations.computeIfAbsent(p.getTemplateName(), k -> new ArrayList<>()).add(hours);
        }
        List<Map<String, Object>> flowStats = new ArrayList<>();
        for (var e : flowDurations.entrySet()) {
            double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            flowStats.add(Map.of("flowName", e.getKey(), "projectCount", e.getValue().size(),
                    "avgHours", Math.round(avg * 10) / 10.0));
        }

        // 2. 高频卡点节点：各节点上滞留的进行中项目数
        Map<String, Integer> stuckCount = new LinkedHashMap<>();
        for (Project p : projectRepository.findByStatus(Project.Status.ACTIVE)) {
            if (p.getCurrentNodeKey() != null) {
                stuckCount.merge(p.getTemplateName() + "/" + p.getCurrentNodeKey(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> bottleneckNodes = new ArrayList<>();
        stuckCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("node", e.getKey());
                    m.put("stuckCount", e.getValue());
                    bottleneckNodes.add(m);
                });

        // 3. 干系人平均响应时长：同项目内不同发送者相邻消息的平均间隔（小时）
        List<Map<String, Object>> respStats = new ArrayList<>();
        for (Project p : all.stream().limit(200).toList()) {
            List<Message> messages = messageRepository.findByProjectIdOrderBySentAtAsc(p.getId());
            List<Double> gaps = new ArrayList<>();
            for (int i = 1; i < messages.size(); i++) {
                Message prev = messages.get(i - 1);
                Message cur = messages.get(i);
                if (!senderOf(prev).equals(senderOf(cur))) {
                    gaps.add(Duration.between(prev.getSentAt(), cur.getSentAt()).toSeconds() / 3600.0);
                }
            }
            if (!gaps.isEmpty()) {
                double avg = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                respStats.add(Map.of("project", p.getName(), "avgResponseHours", Math.round(avg * 10) / 10.0));
            }
        }
        respStats.sort(Comparator.comparingDouble(m -> (double) m.get("avgResponseHours")));

        // 4. 项目超时率
        long risky = all.stream().filter(p -> p.getRiskStatus() != Project.RiskStatus.NORMAL).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProjects", all.size());
        stats.put("activeProjects", projectRepository.findByStatus(Project.Status.ACTIVE).size());
        stats.put("riskProjectCount", risky);
        stats.put("riskRate", all.isEmpty() ? 0 : Math.round(risky * 1000.0 / all.size()) / 10.0);
        stats.put("flowStats", flowStats);
        stats.put("bottleneckNodes", bottleneckNodes);
        stats.put("responseStats", respStats);

        List<Map<String, Object>> projects = all.stream()
                .sorted(Comparator.comparing(Project::getUpdatedAt).reversed())
                .limit(500)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", p.getCode());
                    m.put("name", p.getName());
                    m.put("customerName", p.getCustomerName());
                    m.put("templateName", p.getTemplateName());
                    m.put("currentNodeKey", p.getCurrentNodeKey());
                    m.put("progress", Math.round(p.getProgress() * 1000) / 10.0);
                    m.put("riskStatus", p.getRiskStatus().name());
                    m.put("lastAnalyzedAt", p.getLastAnalyzedAt() == null ? "" : p.getLastAnalyzedAt().toString());
                    return m;
                })
                .toList();

        return new ReportSummary(period, from, to, stats, projects);
    }

    private String senderOf(Message m) {
        return m.getSenderName() == null || m.getSenderName().isBlank() ? m.getSenderId() : m.getSenderName();
    }

    /** 生成全部格式报告文件，返回 {excel, pdf, html} 绝对路径 */
    public Map<String, String> generate(ReportSummary summary, Path reportDir) {
        try {
            Files.createDirectories(reportDir);
            String base = reportDir.resolve(summary.period().toLowerCase()
                    + "-" + FILE_TS.format(LocalDateTime.now())).toString();
            Map<String, String> files = new LinkedHashMap<>();
            files.put("excel", base + ".xlsx");
            files.put("pdf", base + ".pdf");
            files.put("html", base + ".html");
            Files.write(Path.of(files.get("excel")), buildExcel(summary));
            Files.write(Path.of(files.get("pdf")), pdfExporter.buildPdf(summary));
            Files.writeString(Path.of(files.get("html")), buildHtml(summary), StandardCharsets.UTF_8);
            return files;
        } catch (IOException e) {
            throw new BizException(50012, "报告生成失败: " + e.getMessage());
        }
    }

    // ---------- Excel ----------

    private byte[] buildExcel(ReportSummary s) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet stat = wb.createSheet("概览统计");
            int r = 0;
            header(stat, r++, "统计项", "数值");
            row(stat, r++, "项目总数", String.valueOf(s.stats().get("totalProjects")));
            row(stat, r++, "进行中项目", String.valueOf(s.stats().get("activeProjects")));
            row(stat, r++, "风险项目数", String.valueOf(s.stats().get("riskProjectCount")));
            row(stat, r++, "项目超时率(%)", String.valueOf(s.stats().get("riskRate")));
            row(stat, r++, "", "");
            header(stat, r++, "流程名称", "项目数", "平均执行耗时(小时)");
            for (Map<String, Object> f : (List<Map<String, Object>>) s.stats().get("flowStats")) {
                row(stat, r++, String.valueOf(f.get("flowName")),
                        String.valueOf(f.get("projectCount")), String.valueOf(f.get("avgHours")));
            }
            row(stat, r++, "", "", "");
            header(stat, r++, "卡点节点", "滞留项目数");
            for (Map<String, Object> b : (List<Map<String, Object>>) s.stats().get("bottleneckNodes")) {
                row(stat, r++, String.valueOf(b.get("node")), String.valueOf(b.get("stuckCount")));
            }

            Sheet detail = wb.createSheet("项目明细");
            int d = 0;
            header(detail, d++, "项目编号", "项目名称", "客户名称", "流程模板", "当前节点", "进度(%)", "风险状态", "最近分析时间");
            for (Map<String, Object> p : s.projects()) {
                row(detail, d++, String.valueOf(p.get("code")), String.valueOf(p.get("name")),
                        String.valueOf(p.get("customerName") == null ? "" : p.get("customerName")),
                        String.valueOf(p.get("templateName")), String.valueOf(p.get("currentNodeKey") == null ? "" : p.get("currentNodeKey")),
                        String.valueOf(p.get("progress")), String.valueOf(p.get("riskStatus")),
                        String.valueOf(p.get("lastAnalyzedAt")));
            }
            for (int i = 0; i < 8; i++) {
                detail.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(50012, "Excel 导出失败: " + e.getMessage());
        }
    }

    private void header(Sheet sheet, int r, String... cells) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < cells.length; i++) {
            row.createCell(i).setCellValue(cells[i]);
        }
    }

    private void row(Sheet sheet, int r, String... cells) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < cells.length; i++) {
            row.createCell(i).setCellValue(cells[i] == null ? "" : cells[i]);
        }
    }

    // ---------- HTML ----------

    private String buildHtml(ReportSummary s) {
        StringBuilder sb = new StringBuilder("""
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
                <title>FlowPilot 报告</title>
                <style>body{font-family:PingFang SC,Microsoft YaHei,sans-serif;margin:32px;color:#1f2937}
                h1{font-size:22px}h2{font-size:17px;margin-top:28px;border-left:4px solid #4f46e5;padding-left:10px}
                table{border-collapse:collapse;width:100%;margin-top:10px}
                td,th{border:1px solid #e5e7eb;padding:8px 10px;font-size:13px;text-align:left}
                th{background:#f3f4f6}.risk{color:#dc2626;font-weight:600}</style></head><body>
                """);
        sb.append("<h1>FlowPilot ").append(s.period()).append("（")
                .append(s.from() == null ? "" : s.from().toLocalDate())
                .append(" ~ ").append(s.to() == null ? "" : s.to().toLocalDate()).append("）</h1>");
        sb.append("<h2>概览</h2><table>");
        sb.append("<tr><th>项目总数</th><th>进行中</th><th>风险项目</th><th>超时率</th></tr>");
        sb.append("<tr><td>").append(s.stats().get("totalProjects")).append("</td><td>")
                .append(s.stats().get("activeProjects")).append("</td><td>")
                .append(s.stats().get("riskProjectCount")).append("</td><td>")
                .append(s.stats().get("riskRate")).append("%</td></tr></table>");

        sb.append("<h2>高频卡点节点</h2><table><tr><th>节点</th><th>滞留项目数</th></tr>");
        for (Map<String, Object> b : (List<Map<String, Object>>) s.stats().get("bottleneckNodes")) {
            sb.append("<tr><td>").append(b.get("node")).append("</td><td class=\"risk\">")
                    .append(b.get("stuckCount")).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>项目明细</h2><table><tr><th>编号</th><th>项目</th><th>客户</th><th>流程</th>")
                .append("<th>当前节点</th><th>进度</th><th>风险</th></tr>");
        for (Map<String, Object> p : s.projects()) {
            sb.append("<tr><td>").append(p.get("code")).append("</td><td>").append(p.get("name"))
                    .append("</td><td>").append(p.get("customerName") == null ? "" : p.get("customerName"))
                    .append("</td><td>").append(p.get("templateName"))
                    .append("</td><td>").append(p.get("currentNodeKey") == null ? "" : p.get("currentNodeKey"))
                    .append("</td><td>").append(p.get("progress")).append("%</td><td>")
                    .append(p.get("riskStatus")).append("</td></tr>");
        }
        sb.append("</table><p style=\"color:#9ca3af;margin-top:24px\">由 FlowPilot 自动生成</p></body></html>");
        return sb.toString();
    }
}
