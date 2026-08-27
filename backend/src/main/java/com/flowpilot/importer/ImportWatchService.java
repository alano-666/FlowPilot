package com.flowpilot.importer;

import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.ImportRecord;
import com.flowpilot.service.AnalysisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信记录导入文件夹监控（自动化导入核心）：
 *
 * 把微信聊天记录文件（TXT/CSV）或截图（PNG/JPG）扔进监控目录 data/watch/，
 * 系统自动完成：解析 → 归属项目 → 入库 → 触发一次 AI 增量分析 → 归档到 processed/。
 *
 * 注意：个人微信无官方数据接口，本监控只处理「你主动导出到文件夹」的文件，
 * 绝不触碰微信客户端进程（合规红线，见 docs/06）。
 */
@Component
public class ImportWatchService {

    private static final Logger log = LoggerFactory.getLogger(ImportWatchService.class);
    private static final Set<String> TEXT_EXTS = Set.of("txt", "csv");
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg");

    private final FlowPilotProperties props;
    private final ImportService importService;
    private final AnalysisService analysisService;

    private WatchService watchService;
    private Thread watchThread;
    private final Set<String> processing = ConcurrentHashMap.newKeySet();
    private Path watchDir;

    public ImportWatchService(FlowPilotProperties props, ImportService importService,
                              AnalysisService analysisService) {
        this.props = props;
        this.importService = importService;
        this.analysisService = analysisService;
    }

    @PostConstruct
    public void start() {
        if (!props.getWechat().isWatchEnabled()) {
            log.info("微信导入文件夹监控未启用");
            return;
        }
        try {
            watchDir = Path.of(props.getWechat().getWatchDir()).toAbsolutePath();
            Files.createDirectories(watchDir);
            Files.createDirectories(watchDir.resolve("processed"));
            watchService = FileSystems.getDefault().newWatchService();
            watchDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchThread = new Thread(this::loop, "wechat-import-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("微信导入文件夹监控已启动，监听目录: {}（TXT/CSV/PNG/JPG 扔进来即自动导入）", watchDir);
        } catch (IOException e) {
            log.error("微信导入文件夹监控启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log.error("监控循环异常", e);
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Object ctx = event.context();
                if (!(ctx instanceof Path p)) {
                    continue;
                }
                String fileName = p.getFileName().toString();
                if (processing.add(fileName)) {
                    // 独立线程处理，避免阻塞监控循环；延迟 1.5s 等待文件写完
                    new Thread(() -> handle(watchDir.resolve(p).toFile()), "import-" + fileName).start();
                }
            }
            if (!key.reset()) {
                break;
            }
        }
    }

    private void handle(File file) {
        try {
            Thread.sleep(1500);
            String name = file.getName();
            if (!file.exists() || file.length() == 0) {
                return;
            }
            String ext = ext(name);
            if (!TEXT_EXTS.contains(ext) && !IMAGE_EXTS.contains(ext)) {
                return;
            }
            Long projectId = importService.matchProject(file);
            if (projectId == null) {
                importService.recordFailed(name, ImportRecord.Source.WATCH,
                        "未匹配到项目：文件名需包含项目名/客户名，或系统仅存在一个进行中项目");
                importService.moveToProcessed(file);
                log.warn("导入文件无法归属项目，已归档: {}", name);
                return;
            }
            ImportRecord record;
            if (TEXT_EXTS.contains(ext)) {
                record = importService.importTextFile(projectId, name,
                        Files.readAllBytes(file.toPath()), ImportRecord.Source.WATCH);
            } else {
                record = importService.importImage(projectId, name,
                        Files.readAllBytes(file.toPath()), ImportRecord.Source.WATCH);
            }
            importService.moveToProcessed(file);
            log.info("监控自动导入完成: {} → 项目 {}，消息 {} 条，状态 {}", name, projectId,
                    record.getMessageCount(), record.getStatus());
            // 自动触发一次 AI 增量分析
            if (record.getMessageCount() > 0) {
                try {
                    analysisService.analyzeAsync(projectId, "IMPORT_WATCH");
                } catch (Exception e) {
                    log.warn("导入后自动分析触发失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("监控文件处理失败: {}", file.getName(), e);
        } finally {
            processing.remove(file.getName());
        }
    }

    private String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public Path getWatchDir() {
        return watchDir;
    }
}
