package com.flowpilot.web;

import com.flowpilot.auth.CurrentUser;
import com.flowpilot.auth.RequireRole;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.common.PageResult;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.User;
import com.flowpilot.service.DocumentParser;
import com.flowpilot.service.TemplateService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流程模板接口（PRD 3.1 流程知识库管理）。
 */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final DocumentParser documentParser;

    public TemplateController(TemplateService templateService, DocumentParser documentParser) {
        this.templateService = templateService;
        this.documentParser = documentParser;
    }

    /** 模板分页列表 */
    @GetMapping
    public ApiResponse<PageResult<FlowTemplate>> list(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Page<FlowTemplate> p = templateService.page(keyword, page, size);
        return ApiResponse.ok(new PageResult<>(p.getContent(), p.getTotalElements(), page, size));
    }

    /** 模板详情 */
    @GetMapping("/{id}")
    public ApiResponse<FlowTemplate> detail(@PathVariable Long id) {
        return ApiResponse.ok(templateService.get(id));
    }

    /**
     * 上传流程文档 → 解析 + AI 建模（PRD 6.1）。
     * 支持 txt/md/docx/pdf，返回草稿模板。
     */
    @PostMapping(value = "/parse", consumes = "multipart/form-data")
    public ApiResponse<FlowTemplate> parseFile(@RequestParam("file") MultipartFile file) throws IOException {
        String text = documentParser.parse(file.getOriginalFilename(), file.getBytes());
        return ApiResponse.ok(templateService.parseAndCreate(file.getOriginalFilename(), text,
                currentUsername()));
    }

    public record ParseTextRequest(String docName, String text) {
    }

    /** 粘贴文本解析建模 */
    @PostMapping("/parse-text")
    public ApiResponse<FlowTemplate> parseText(@RequestBody ParseTextRequest req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new com.flowpilot.common.BizException(40002, "文本内容为空");
        }
        String docName = req.docName() == null || req.docName().isBlank() ? "粘贴文本.md" : req.docName();
        return ApiResponse.ok(templateService.parseAndCreate(docName, req.text(), currentUsername()));
    }

    public record ParseFeishuRequest(String docUrl) {
    }

    /** 飞书在线文档链接解析 */
    @PostMapping("/parse-feishu")
    public ApiResponse<FlowTemplate> parseFeishu(@RequestBody ParseFeishuRequest req) {
        return ApiResponse.ok(templateService.parseFromFeishuDoc(req.docUrl(), currentUsername()));
    }

    public record UpdateTemplateRequest(String name, String description, String nodesJson,
                                        String branchesJson, String glossaryJson, String note) {
    }

    /** 编辑模板（节点/分支/词库），自动生成版本快照 */
    @RequireRole(User.Role.MANAGER)
    @PutMapping("/{id}")
    public ApiResponse<FlowTemplate> update(@PathVariable Long id, @RequestBody UpdateTemplateRequest req) {
        return ApiResponse.ok(templateService.update(id, req.name(), req.description(), req.nodesJson(),
                req.branchesJson(), req.glossaryJson(), req.note(), currentUsername()));
    }

    /** 发布模板 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/publish")
    public ApiResponse<FlowTemplate> publish(@PathVariable Long id) {
        return ApiResponse.ok(templateService.publish(id, currentUsername()));
    }

    /** 停用模板 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/{id}/archive")
    public ApiResponse<FlowTemplate> archive(@PathVariable Long id) {
        return ApiResponse.ok(templateService.archive(id));
    }

    /** 复制模板 */
    @PostMapping("/{id}/duplicate")
    public ApiResponse<FlowTemplate> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(templateService.duplicate(id, currentUsername()));
    }

    /** 版本历史 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<?>> versions(@PathVariable Long id) {
        return ApiResponse.ok(templateService.versions(id));
    }

    private String currentUsername() {
        var p = CurrentUser.get();
        return p == null ? "system" : p.username();
    }
}
