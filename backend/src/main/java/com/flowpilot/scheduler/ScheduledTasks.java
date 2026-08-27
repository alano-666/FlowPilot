package com.flowpilot.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.NotificationJob;
import com.flowpilot.model.Project;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.repository.MessageRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.StakeholderRepository;
import com.flowpilot.service.AnalysisService;
import com.flowpilot.service.ChannelSyncService;
import com.flowpilot.service.NotifyService;
import com.flowpilot.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务调度（PRD 6.2 每日自动进度同步更新流程 / 3.8 通知与预警 / 3.9 报告）：
 *  - 渠道增量同步（可配，默认每 30 分钟）
 *  - 增量 AI 分析（可配，默认每 30 分钟，与新消息解耦）
 *  - SLA 巡检（默认每小时，@责任人 + 风险升级）
 *  - 每日进度摘要推送（默认 9:00）
 *  - 周报/月报自动生成
 *  - 原始消息留存清理（默认 90 天）
 */
@Component
@EnableScheduling
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowPilotProperties props;
    private final ChannelSyncService channelSyncService;
    private final com.flowpilot.channel.EmailChannelService emailChannelService;
    private final AnalysisService analysisService;
    private final NotifyService notifyService;
    private final ReportService reportService;
    private final ProjectRepository projectRepository;
    private final StakeholderRepository stakeholderRepository;
    private final MessageRepository messageRepository;

    public ScheduledTasks(FlowPilotProperties props, ChannelSyncService channelSyncService,
                          com.flowpilot.channel.EmailChannelService emailChannelService,
                          AnalysisService analysisService, NotifyService notifyService,
                          ReportService reportService, ProjectRepository projectRepository,
                          StakeholderRepository stakeholderRepository, MessageRepository messageRepository) {
        this.props = props;
        this.channelSyncService = channelSyncService;
        this.emailChannelService = emailChannelService;
        this.analysisService = analysisService;
        this.notifyService = notifyService;
        this.reportService = reportService;
        this.projectRepository = projectRepository;
        this.stakeholderRepository = stakeholderRepository;
        this.messageRepository = messageRepository;
    }

    /** 邮件轮询同步（每 15 分钟，可配 flowpilot.email.poll-minutes 与 enabled） */
    @Scheduled(cron = "${flowpilot.email.poll-cron:0 */15 * * * ?}")
    public void syncEmails() {
        if (!emailChannelService.enabled()) {
            return;
        }
        try {
            emailChannelService.sync();
        } catch (Exception e) {
            log.warn("邮件同步失败: {}", e.getMessage());
        }
    }

    /** 渠道增量同步（PRD 6.2 步骤 2） */
    @Scheduled(cron = "${flowpilot.notify.sync-cron:0 */30 * * * ?}")
    public void syncChannels() {
        try {
            int n = channelSyncService.syncAll();
            if (n > 0) {
                log.info("定时渠道同步完成，新增 {} 条消息", n);
            }
        } catch (Exception e) {
            log.warn("定时渠道同步失败: {}", e.getMessage());
        }
    }

    /** 对有新消息的项目触发增量分析 */
    @Scheduled(cron = "${flowpilot.notify.sync-cron:0 */30 * * * ?}")
    public void analyzePending() {
        List<Project> active = projectRepository.findByStatus(Project.Status.ACTIVE);
        int triggered = 0;
        for (Project p : active) {
            long newCount = messageRepository.countByProjectId(p.getId());
            if (newCount == 0) {
                continue;
            }
            try {
                analysisService.analyzeAsync(p.getId(), "SCHEDULE");
                triggered++;
            } catch (Exception e) {
                log.warn("项目 {} 定时分析触发失败: {}", p.getId(), e.getMessage());
            }
        }
        if (triggered > 0) {
            log.info("定时分析已触发 {} 个项目", triggered);
        }
    }

    /** SLA 巡检：当前节点超时 → 预警 + @责任人（PRD 3.8） */
    @Scheduled(cron = "${flowpilot.notify.sla-check-cron:0 5 * * * ?}")
    public void checkSla() {
        for (Project p : projectRepository.findByStatus(Project.Status.ACTIVE)) {
            try {
                List<Map<String, Object>> nodes = readNodes(p.getTemplateSnapshotJson());
                Map<String, Object> currentNode = nodes.stream()
                        .filter(n -> p.getCurrentNodeKey() != null
                                && p.getCurrentNodeKey().equals(String.valueOf(n.get("key"))))
                        .findFirst().orElse(null);
                if (currentNode == null) {
                    continue;
                }
                Object slaObj = currentNode.get("sla_hours");
                if (slaObj == null) {
                    continue;
                }
                double slaHours = Double.parseDouble(String.valueOf(slaObj));
                LocalDateTime since = p.getLastAnalyzedAt() == null ? p.getStartedAt() : p.getLastAnalyzedAt();
                if (since == null) {
                    continue;
                }
                double elapsed = java.time.Duration.between(since, LocalDateTime.now()).toHours();
                if (elapsed > slaHours && p.getRiskStatus() == Project.RiskStatus.NORMAL) {
                    p.setRiskStatus(Project.RiskStatus.WARNING);
                    p.setUpdatedAt(LocalDateTime.now());
                    projectRepository.save(p);
                    List<Stakeholder> owners = stakeholderRepository.findByProjectIdAndNodeKey(
                            p.getId(), p.getCurrentNodeKey());
                    notifyService.notify(p, NotificationJob.Type.SLA_OVERDUE,
                            "节点超时预警：「" + currentNode.get("name") + "」",
                            "节点「" + currentNode.get("name") + "」SLA 要求 " + slaHours
                                    + " 小时，已超时 " + Math.round(elapsed - slaHours) + " 小时，请及时跟进。",
                            owners);
                }
            } catch (Exception e) {
                log.warn("项目 {} SLA 巡检失败: {}", p.getId(), e.getMessage());
            }
        }
    }

    /** 每日进度摘要（PRD 3.8.1） */
    @Scheduled(cron = "${flowpilot.notify.digest-cron:0 0 9 * * ?}")
    public void dailyDigest() {
        List<Project> active = projectRepository.findByStatus(Project.Status.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        notifyService.sendDailyDigest(active);
        log.info("每日进度摘要已推送，{} 个进行中项目", active.size());
    }

    /** 周报自动生成 */
    @Scheduled(cron = "${flowpilot.notify.weekly-report-cron:0 0 8 ? * MON}")
    public void weeklyReport() {
        generateReport("周报", LocalDateTime.now().minusDays(7), LocalDateTime.now());
    }

    /** 月报自动生成 */
    @Scheduled(cron = "${flowpilot.notify.monthly-report-cron:0 0 8 1 * ?}")
    public void monthlyReport() {
        generateReport("月报", LocalDateTime.now().minusDays(30), LocalDateTime.now());
    }

    private void generateReport(String period, LocalDateTime from, LocalDateTime to) {
        try {
            ReportService.ReportSummary summary = reportService.buildSummary(period, from, to);
            reportService.generate(summary, Path.of("./data/reports"));
            log.info("{}自动生成完成", period);
        } catch (Exception e) {
            log.warn("{}自动生成失败: {}", period, e.getMessage());
        }
    }

    /** 原始消息留存清理（PRD 7.2 数据最小化，默认 90 天） */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupMessages() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(props.getData().getRetentionDays());
            long deleted = messageRepository.deleteBySentAtBefore(before);
            if (deleted > 0) {
                log.info("数据留存清理完成，删除 {} 条超期原始消息", deleted);
            }
        } catch (Exception e) {
            log.warn("数据留存清理失败: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> readNodes(String snapshotJson) {
        try {
            Map<String, Object> snap = MAPPER.readValue(snapshotJson == null ? "{}" : snapshotJson,
                    new TypeReference<>() {
                    });
            Object nodes = snap.get("nodes");
            return MAPPER.convertValue(nodes == null ? List.of() : nodes, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
