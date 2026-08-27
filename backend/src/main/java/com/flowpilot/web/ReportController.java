package com.flowpilot.web;

import com.flowpilot.auth.RequireRole;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.model.User;
import com.flowpilot.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 报告接口（PRD 3.9）：周报/月报生成、列表、下载（Excel/PDF/HTML）。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final Path reportDir = Path.of("./data/reports");

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    public record GenerateRequest(String period, String from, String to) {
    }

    /** 生成报告，返回三种格式文件路径 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody GenerateRequest req) {
        LocalDateTime from = req.from() == null || req.from().isBlank()
                ? LocalDateTime.now().minusDays("月报".equals(req.period()) ? 30 : 7)
                : LocalDateTime.parse(req.from());
        LocalDateTime to = req.to() == null || req.to().isBlank()
                ? LocalDateTime.now() : LocalDateTime.parse(req.to());
        ReportService.ReportSummary summary = reportService.buildSummary(
                req.period() == null ? "周报" : req.period(), from, to);
        Map<String, String> files = reportService.generate(summary, reportDir);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("summary", summary.stats());
        resp.put("files", files);
        return ApiResponse.ok(resp);
    }

    /** 已生成报告列表 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() throws IOException {
        List<Map<String, Object>> files = new ArrayList<>();
        if (Files.exists(reportDir)) {
            try (Stream<Path> s = Files.list(reportDir)) {
                s.filter(p -> p.toString().endsWith(".xlsx") || p.toString().endsWith(".pdf")
                                || p.toString().endsWith(".html"))
                        .sorted(Comparator.comparing(p -> -p.toFile().lastModified()))
                        .forEach(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("name", p.getFileName().toString());
                            m.put("size", p.toFile().length());
                            m.put("modifiedAt", new java.util.Date(p.toFile().lastModified()));
                            files.add(m);
                        });
            }
        }
        return ApiResponse.ok(files);
    }

    /** 下载报告文件 */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path file = reportDir.resolve(fileName).normalize();
        if (!file.startsWith(reportDir.toAbsolutePath()) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String lower = fileName.toLowerCase();
        MediaType type = lower.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : lower.endsWith(".html") ? MediaType.TEXT_HTML
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(type)
                .body(new FileSystemResource(file));
    }
}
