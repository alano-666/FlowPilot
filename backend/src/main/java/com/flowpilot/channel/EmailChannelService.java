package com.flowpilot.channel;

import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.MessageService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 邮件渠道服务（IMAP）：定时拉取邮箱中与项目相关的邮件，
 * 转为聊天消息并入 AI 分析数据源，让 AI 结合「群聊 + 邮件」理解完整流程。
 *
 * 安全说明：
 *  - 密码使用「邮箱授权码/应用专用密码」，经环境变量注入（不落代码仓库）；
 *  - 只拉取最近 N 天邮件（默认 14 天），只采集能归属到项目的邮件（数据最小化，PRD 7.2）；
 *  - 邮件按 Message-ID 去重，重复拉取幂等。
 */
@Service
public class EmailChannelService {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelService.class);

    private final FlowPilotProperties props;
    private final ProjectRepository projectRepository;
    private final MessageService messageService;

    public EmailChannelService(FlowPilotProperties props, ProjectRepository projectRepository,
                               MessageService messageService) {
        this.props = props;
        this.projectRepository = projectRepository;
        this.messageService = messageService;
    }

    public boolean enabled() {
        FlowPilotProperties.Email e = props.getEmail();
        return e.isEnabled() && !e.getImapHost().isBlank() && !e.getUsername().isBlank();
    }

    /** 拉取并导入邮件，返回新增消息数 */
    public int sync() {
        FlowPilotProperties.Email cfg = props.getEmail();
        if (!enabled()) {
            log.info("邮件渠道未启用，跳过同步");
            return 0;
        }
        Store store = null;
        try {
            Properties p = new Properties();
            p.put("mail.store.protocol", "imaps");
            p.put("mail.imaps.host", cfg.getImapHost());
            p.put("mail.imaps.port", String.valueOf(cfg.getImapPort()));
            p.put("mail.imaps.ssl.enable", "true");
            p.put("mail.imaps.connectiontimeout", "15000");
            p.put("mail.imaps.timeout", "30000");
            Session session = Session.getInstance(p);
            store = session.getStore("imaps");
            store.connect(cfg.getImapHost(), cfg.getImapPort(), cfg.getUsername(), cfg.getPassword());
            // 网易(163/126/188)对未声明身份的客户端触发 Unsafe Login 风控：
            // 登录后立即发送 IMAP ID 命令声明客户端身份（反射调用，兼容 com.sun.mail 与 angus 实现）
            try {
                java.lang.reflect.Method idMethod = store.getClass().getMethod("id", java.util.Map.class);
                idMethod.invoke(store, java.util.Map.of(
                        "name", "FlowPilot",
                        "version", "1.0",
                        "vendor", "flowpilot",
                        "support-email", cfg.getUsername()));
            } catch (Exception idEx) {
                log.debug("IMAP ID 声明失败（不影响后续）: {}", idEx.getMessage());
            }

            Folder folder = store.getFolder(cfg.getFolder());
            folder.open(Folder.READ_ONLY);

            Date since = Date.from(LocalDateTime.now().minusDays(cfg.getLookbackDays())
                    .atZone(ZoneId.systemDefault()).toInstant());
            jakarta.mail.Message[] found = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, since));
            if (found.length == 0) {
                folder.close(false);
                return 0;
            }

            int saved = 0;
            int limit = Math.min(found.length, cfg.getMaxPerPoll());
            // 从最新到最旧处理
            for (int i = found.length - 1; i >= found.length - limit; i--) {
                try {
                    if (importOne((MimeMessage) found[i])) {
                        saved++;
                    }
                } catch (Exception e) {
                    log.debug("单封邮件导入失败: {}", e.getMessage());
                }
            }
            folder.close(false);
            if (saved > 0) {
                log.info("邮件同步完成：新增 {} 封项目相关邮件", saved);
            }
            return saved;
        } catch (Exception e) {
            log.warn("邮件同步失败（检查 IMAP 配置与授权码）: {}", e.getMessage());
            return 0;
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 单封邮件 → 消息（按 Message-ID 去重） */
    private boolean importOne(MimeMessage mail) throws Exception {
        String messageId = mail.getMessageID();
        if (messageId == null || messageId.isBlank()) {
            messageId = "mail_" + mail.getSentDate() + "_" + mail.hashCode();
        }
        String sender = senderName(mail);
        String subject = mail.getSubject() == null ? "(无主题)" : mail.getSubject();
        String body = extractBody(mail);

        // 归属项目：主题+发件人匹配项目名/客户名；否则唯一进行中项目；否则跳过
        Long projectId = matchProject(subject + " " + sender);
        if (projectId == null) {
            return false;
        }

        Message m = new Message();
        m.setProjectId(projectId);
        m.setChannelType(Message.ChannelType.EMAIL);
        m.setChannelId(props.getEmail().getUsername());
        m.setMsgId("email_" + messageId);
        m.setSenderId(sender);
        m.setSenderName(sender);
        m.setContent("📧【邮件】主题：" + subject + "\n" + truncate(body, 1500));
        m.setMsgType(Message.MsgType.TEXT);
        m.setSentAt(mail.getSentDate() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(mail.getSentDate().toInstant(), ZoneId.systemDefault()));
        m.setSource("SYNC");
        return messageService.save(m);
    }

    /** 项目匹配规则（与微信导入一致）：唯一匹配 > 唯一进行中项目 > 跳过 */
    public Long matchProject(String haystack) {
        String h = haystack == null ? "" : haystack;
        List<Project> matches = projectRepository.findByStatus(Project.Status.ACTIVE).stream()
                .filter(p -> com.flowpilot.common.ProjectMatcher.matchesAny(h, p.getName(), p.getCustomerName()))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }
        if (matches.isEmpty()) {
            List<Project> active = projectRepository.findByStatus(Project.Status.ACTIVE);
            return active.size() == 1 ? active.get(0).getId() : null;
        }
        return matches.get(0).getId();
    }

    private String senderName(MimeMessage mail) throws Exception {
        Address[] from = mail.getFrom();
        if (from == null || from.length == 0) {
            return "未知发件人";
        }
        if (from[0] instanceof InternetAddress ia) {
            if (ia.getPersonal() != null && !ia.getPersonal().isBlank()) {
                return ia.getPersonal();
            }
            return ia.getAddress() == null ? "未知发件人" : ia.getAddress();
        }
        return from[0].toString();
    }

    /** 正文提取：text/plain 优先，其次 text/html 去标签；多部分递归 */
    private String extractBody(MimeMessage mail) throws Exception {
        Object content = mail.getContent();
        if (content instanceof String s) {
            return stripHtml(s);
        }
        if (content instanceof jakarta.mail.Multipart multi) {
            return extractMultipart(multi);
        }
        return "";
    }

    private String extractMultipart(jakarta.mail.Multipart multi) throws Exception {
        String html = null;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multi.getCount(); i++) {
            jakarta.mail.BodyPart part = multi.getBodyPart(i);
            if (part.isMimeType("text/plain")) {
                Object c = part.getContent();
                if (c instanceof String s) {
                    text.append(s);
                }
            } else if (part.isMimeType("text/html") && html == null) {
                Object c = part.getContent();
                if (c instanceof String s) {
                    html = s;
                }
            } else if (part.isMimeType("multipart/*")) {
                Object c = part.getContent();
                if (c instanceof jakarta.mail.Multipart sub) {
                    text.append(extractMultipart(sub));
                }
            }
        }
        if (text.length() > 0) {
            return text.toString();
        }
        return html == null ? "" : stripHtml(html);
    }

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private String stripHtml(String html) {
        return HTML_TAG.matcher(html.replace("&nbsp;", " ").replace("<br>", "\n").replace("<br/>", "\n"))
                .replaceAll("").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len) + "…(截断)";
    }
}
