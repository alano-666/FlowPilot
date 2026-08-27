package com.flowpilot.web;

import com.flowpilot.common.ApiResponse;
import com.flowpilot.common.PageResult;
import com.flowpilot.importer.ImportService;
import com.flowpilot.importer.ImportWatchService;
import com.flowpilot.model.ImportRecord;
import com.flowpilot.repository.ImportRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 微信记录导入接口（PRD 3.2）：上传导入 + 导入记录 + 监控目录状态。
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportRecordRepository importRecordRepository;
    private final ImportWatchService watchService;

    public ImportController(ImportService importService, ImportRecordRepository importRecordRepository,
                            ImportWatchService watchService) {
        this.importService = importService;
        this.importRecordRepository = importRecordRepository;
        this.watchService = watchService;
    }

    /**
     * 上传微信聊天记录文件（TXT/CSV）或截图（PNG/JPG）。
     * 截图在启用 OCR（flowpilot.wechat.ocr.provider=baidu）时自动识别文字。
     */
    @PostMapping("/wechat")
    public ApiResponse<ImportRecord> upload(@RequestParam Long projectId,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "record.txt" : file.getOriginalFilename();
        String lower = name.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ApiResponse.ok(importService.importImage(projectId, name, file.getBytes(),
                    ImportRecord.Source.API));
        }
        return ApiResponse.ok(importService.importTextFile(projectId, name, file.getBytes(),
                ImportRecord.Source.API));
    }

    /** 导入记录分页 */
    @GetMapping
    public ApiResponse<PageResult<ImportRecord>> list(@RequestParam(required = false) Long projectId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Page<ImportRecord> p = projectId == null
                ? importRecordRepository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(page - 1, size))
                : importRecordRepository.findByProjectIdOrderByCreatedAtDesc(projectId,
                        org.springframework.data.domain.PageRequest.of(page - 1, size));
        return ApiResponse.ok(new PageResult<>(p.getContent(), p.getTotalElements(), page, size));
    }

    /** 监控目录状态与使用说明 */
    @GetMapping("/watch-status")
    public ApiResponse<Map<String, Object>> watchStatus() {
        return ApiResponse.ok(Map.of(
                "watchDir", watchService.getWatchDir() == null ? "" : watchService.getWatchDir().toString(),
                "hint", "把微信聊天记录 TXT/CSV 或截图扔进该目录即自动导入并触发 AI 分析；"
                        + "文件名包含项目名或客户名可自动归属项目。"));
    }
}
