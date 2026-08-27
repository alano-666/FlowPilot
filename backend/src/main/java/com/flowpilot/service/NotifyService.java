package com.flowpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.channel.WeComClient;
import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.NotificationJob;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.model.Stakeholder;
import com.flowpilot.repository.NotificationJobRepository;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.StakeholderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通知预警服务（PRD 3.8）：生成通知任务并即时推送。
 * 推送通道优先级：飞书群机器人 webhook > 企微群机器人 webhook > 应用内日志。
 * 单项目绑定飞书群时，SLA/节点通知会带 @责任人 发送到项目群。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotificationJobRepository jobRepository;
    private final ProjectChannelRepository channelRepository;
    private final StakeholderRepository stakeholderRepository;
    private final FeishuClient feishuClient;
    private final WeComClient weComClient;
    private final FlowPilotProperties props;

    public NotifyService(NotificationJobRepository jobRepository, ProjectChannelRepository channelRepository,
                         StakeholderRepository stakeholderRepository, FeishuClient feishuClient,
                         WeComClient weComClient, FlowPilotProperties props) {
        this.jobRepository = jobRepository;
        this.channelRepository = channelRepository;
        this.stakeholderRepository = stakeholderRepository;
        this.feishuClient = feishuClient;
        this.weComClient = weComClient;
        this.props = props;
    }

    /** 创建通知任务并即时推送 */
    public NotificationJob notify(Project project, NotificationJob.Type type, String title, String content,
                                  List<Stakeholder> targets) {
        NotificationJob job = new NotificationJob();
        job.setProjectId(project.getId());
        job.setType(type);
        job.setTitle(title);
        job.setContent(content);
        try {
            job.setTargetsJson(MAPPER.writeValueAsString(targets == null ? List.of()
                    : targets.stream().map(s -> Map.of(
                            "name", s.getName(), "role", s.getRole(),
                            "contact_type", s.getContactType().name().toLowerCase(),
                            "contact_id", s.getContactId())).toList()));
        } catch (Exception e) {
            job.setTargetsJson("[]");
        }
        job.setCreatedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            deliver(project, content, targets);
            job.setStatus(NotificationJob.Status.SENT);
        } catch (Exception e) {
            job.setStatus(NotificationJob.Status.FAILED);
            job.setErrorMsg(e.getMessage());
            log.warn("通知推送失败: {}", e.getMessage());
        }
        job.setExecutedAt(LocalDateTime.now());
        jobRepository.save(job);
        return job;
    }

    private void deliver(Project project, String content, List<Stakeholder> targets) {
        String full = "【FlowPilot】" + project.getName() + "\n" + content;
        String fsWebhook = props.getNotify().getFeishuWebhook();
        String wcWebhook = props.getNotify().getWecomWebhook();

        // 优先发项目绑定的飞书群（带@责任人）
        List<ProjectChannel> feishuChannels = channelRepository.findByProjectId(project.getId()).stream()
                .filter(c -> c.getChannelType() == ProjectChannel.ChannelType.FEISHU && c.isSyncEnabled())
                .toList();
        if (!feishuChannels.isEmpty() && feishuClient.configured()) {
            StringBuilder atContent = new StringBuilder(content);
            if (targets != null) {
                for (Stakeholder s : targets) {
                    if (s.getContactType() == Stakeholder.ContactType.FEISHU && s.getContactId() != null) {
                        atContent.append(" <at user_id=\"").append(s.getContactId()).append("\"></at>");
                    } else if (s.getName() != null) {
                        atContent.append(" @").append(s.getName());
                    }
                }
            }
            feishuClient.sendTextToChat(feishuChannels.get(0).getChannelId(), atContent.toString());
            return;
        }
        if (fsWebhook != null && !fsWebhook.isBlank()) {
            feishuClient.sendWebhook(fsWebhook, full);
            return;
        }
        if (wcWebhook != null && !wcWebhook.isBlank()) {
            weComClient.sendWebhook(wcWebhook, full);
            return;
        }
        log.info("[通知] project={} {} | {}", project.getId(), project.getName(), content);
    }

    /** 每日进度摘要（PRD 3.8.1 示例） */
    public void sendDailyDigest(List<Project> activeProjects) {
        StringBuilder sb = new StringBuilder("【FlowPilot 每日进度】\n今日共 ")
                .append(activeProjects.size()).append(" 个进行中项目\n");
        int i = 1;
        for (Project p : activeProjects) {
            sb.append(i++).append(". ").append(p.getCustomerName() == null ? p.getName() : p.getCustomerName())
                    .append(" - ").append(p.getTemplateName() == null ? "未绑定模板" : p.getTemplateName())
                    .append(" - ").append(Math.round(p.getProgress() * 100)).append("%")
                    .append(" - 当前节点：").append(p.getCurrentNodeKey() == null ? "待启动" : p.getCurrentNodeKey());
            if (p.getRiskStatus() != Project.RiskStatus.NORMAL) {
                sb.append(" - ⚠️").append(p.getRiskStatus() == Project.RiskStatus.BLOCKED ? "已卡顿" : "预警");
            }
            sb.append('\n');
        }
        sb.append("点击查看完整看板");
        String fsWebhook = props.getNotify().getFeishuWebhook();
        String wcWebhook = props.getNotify().getWecomWebhook();
        try {
            if (fsWebhook != null && !fsWebhook.isBlank()) {
                feishuClient.sendWebhook(fsWebhook, sb.toString());
            } else if (wcWebhook != null && !wcWebhook.isBlank()) {
                weComClient.sendWebhook(wcWebhook, sb.toString());
            } else {
                log.info("[每日摘要]\n{}", sb);
            }
        } catch (Exception e) {
            log.warn("每日摘要推送失败: {}", e.getMessage());
        }
    }
}
