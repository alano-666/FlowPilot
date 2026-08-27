package com.flowpilot.importer;

import com.flowpilot.common.BizException;
import com.flowpilot.model.ImportRecord;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.repository.ImportRecordRepository;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 微信记录导入服务：TXT/CSV 解析 + 截图 OCR，统一落库与导入记录。
 * 供前端上传（POST /api/v1/imports/wechat）与文件夹监控共用。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final WeChatRecordParser parser;
    private final OcrService ocrService;
    private final MessageService messageService;
    private final ProjectRepository projectRepository;
    private final ProjectChannelRepository channelRepository;
    private final ImportRecordRepository importRecordRepository;

    public ImportService(WeChatRecordParser parser, OcrService ocrService, MessageService messageService,
                         ProjectRepository projectRepository, ProjectChannelRepository channelRepository,
                         ImportRecordRepository importRecordRepository) {
        this.parser = parser;
        this.ocrService = ocrService;
        this.messageService = messageService;
        this.projectRepository = projectRepository;
        this.channelRepository = channelRepository;
        this.importRecordRepository = importRecordRepository;
    }

    /**
     * 导入文本文件（TXT/CSV）。
     * @return 导入记录
     */
    @Transactional
    public ImportRecord importTextFile(Long projectId, String fileName, byte[] content, ImportRecord.Source source) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        String text = new String(content, StandardCharsets.UTF_8);
        WeChatRecordParser.ParseResult result = parser.parse(fileName, text);

        ImportRecord record = new ImportRecord();
        record.setProjectId(projectId);
        record.setFileName(fileName);
        record.setFormat(fileName.toLowerCase().endsWith(".csv")
                ? ImportRecord.Format.CSV : ImportRecord.Format.TXT);
        record.setSource(source);
        record.setMessageCount(result.messages().size());
        record.setStatus(result.messages().isEmpty() ? ImportRecord.Status.FAILED : ImportRecord.Status.SUCCESS);
        record.setNote(result.warnings().isEmpty()
                ? "格式: " + result.format()
                : "格式: " + result.format() + "；警告: " + String.join("；", result.warnings()));

        if (!result.messages().isEmpty()) {
            persist(project, fileName, result.messages());
        }
        importRecordRepository.save(record);
        return record;
    }

    /**
     * 导入截图：OCR 识别（未启用时按图片消息归档）。
     */
    @Transactional
    public ImportRecord importImage(Long projectId, String fileName, byte[] content, ImportRecord.Source source) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        String ocrText = ocrService.recognize(content);

        ImportRecord record = new ImportRecord();
        record.setProjectId(projectId);
        record.setFileName(fileName);
        record.setFormat(ImportRecord.Format.IMAGE);
        record.setSource(source);

        if (ocrText.isBlank()) {
            // 未启用 OCR：截图归档为图片消息，内容留待人工补录
            List<WeChatRecordParser.ParsedMessage> one = List.of(new WeChatRecordParser.ParsedMessage(
                    LocalDateTime.now(), "微信截图", "[截图待识别] 文件名: " + fileName, true));
            persist(project, fileName, one);
            record.setMessageCount(1);
            record.setStatus(ImportRecord.Status.PARTIAL);
            record.setNote("OCR 未启用，截图已归档待人工补录（配置 flowpilot.wechat.ocr.provider=baidu 可自动识别）");
        } else {
            // OCR 文本中可能包含多条消息，尝试按复制格式再解析
            WeChatRecordParser.ParseResult result = parser.parse(fileName, ocrText);
            if (result.messages().isEmpty()) {
                List<WeChatRecordParser.ParsedMessage> one = List.of(new WeChatRecordParser.ParsedMessage(
                        LocalDateTime.now(), "微信截图", ocrText, true));
                persist(project, fileName, one);
                record.setMessageCount(1);
            } else {
                persist(project, fileName, result.messages());
                record.setMessageCount(result.messages().size());
            }
            record.setStatus(ImportRecord.Status.SUCCESS);
            record.setNote("OCR 自动识别完成");
        }
        importRecordRepository.save(record);
        return record;
    }

    /** 文件 → 项目归属匹配：文件名模糊匹配项目名/客户名 > 唯一进行中项目 > 失败 */
    public Long matchProject(File file) {
        String name = file.getName();
        List<Project> candidates = projectRepository.findByStatus(Project.Status.ACTIVE)
                .stream()
                .filter(p -> com.flowpilot.common.ProjectMatcher.matchesAny(
                        name, p.getName(), p.getCustomerName()))
                .toList();
        if (candidates.size() == 1) {
            return candidates.get(0).getId();
        }
        if (candidates.isEmpty()) {
            List<Project> active = projectRepository.findByStatus(Project.Status.ACTIVE);
            if (active.size() == 1) {
                return active.get(0).getId();
            }
            return null;
        }
        return candidates.get(0).getId();
    }

    /** 写入失败记录（监控到无法归属的文件时） */
    public ImportRecord recordFailed(String fileName, ImportRecord.Source source, String note) {
        ImportRecord record = new ImportRecord();
        record.setProjectId(0L);
        record.setFileName(fileName);
        record.setFormat(ImportRecord.Format.TXT);
        record.setSource(source);
        record.setStatus(ImportRecord.Status.FAILED);
        record.setNote(note);
        importRecordRepository.save(record);
        return record;
    }

    private void persist(Project project, String fileName, List<WeChatRecordParser.ParsedMessage> parsed) {
        // 渠道归属：微信导入文件作为一个渠道
        if (!channelRepository.existsByProjectIdAndChannelTypeAndChannelId(
                project.getId(), ProjectChannel.ChannelType.WECHAT_IMPORT, fileName)) {
            ProjectChannel pc = new ProjectChannel();
            pc.setProjectId(project.getId());
            pc.setChannelType(ProjectChannel.ChannelType.WECHAT_IMPORT);
            pc.setChannelId(fileName);
            pc.setChannelName("微信导入: " + fileName);
            pc.setSyncEnabled(true);
            channelRepository.save(pc);
        }
        List<Message> messages = new ArrayList<>();
        for (WeChatRecordParser.ParsedMessage pm : parsed) {
            Message m = new Message();
            m.setProjectId(project.getId());
            m.setChannelType(Message.ChannelType.WECHAT_IMPORT);
            m.setChannelId(fileName);
            m.setMsgId("wx_" + fileName.hashCode() + "_" + pm.sentAt().toString() + "_" + pm.sender().hashCode()
                    + "_" + pm.content().hashCode());
            m.setSenderName(pm.sender());
            m.setContent(pm.content());
            m.setMsgType(pm.isImage() ? Message.MsgType.IMAGE : Message.MsgType.TEXT);
            m.setSentAt(pm.sentAt());
            m.setSource("IMPORT");
            messages.add(m);
        }
        messageService.saveAll(messages);
    }

    /** 移动已处理文件到 processed 目录 */
    public File moveToProcessed(File file) {
        try {
            File processedDir = new File(file.getParentFile(), "processed");
            if (!processedDir.exists() && !processedDir.mkdirs()) {
                log.warn("创建 processed 目录失败: {}", processedDir);
                return file;
            }
            File target = new File(processedDir, file.getName() + "." + UUID.randomUUID().toString().substring(0, 8));
            Files.move(file.toPath(), target.toPath());
            return target;
        } catch (IOException e) {
            log.warn("文件归档失败: {}", file.getName(), e);
            return file;
        }
    }
}
