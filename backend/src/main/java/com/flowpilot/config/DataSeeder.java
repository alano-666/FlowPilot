package com.flowpilot.config;

import com.flowpilot.ai.MockLlmProvider;
import com.flowpilot.channel.MockChannelService;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.model.User;
import com.flowpilot.repository.FlowTemplateRepository;
import com.flowpilot.repository.MessageRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.UserRepository;
import com.flowpilot.service.AnalysisService;
import com.flowpilot.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 演示数据初始化（flowpilot.seed-demo=true 时）：
 *  - 默认账号 admin/admin123（管理员）、manager/manager123（流程负责人）
 *  - 四套流程模板：远程安装设备 / 软件项目管理 / 软件需求更新 / 客户支持工单
 *  - 五个演示项目，各自带一段仿真群聊，并已用 Mock 引擎完成一次 AI 分析，
 *    看板呈现不同进度与风险状态的多样效果。
 *
 * 幂等设计：按模板名/项目名检查，重启不会重复创建，也不影响用户自建数据。
 * 演示分析固定使用 Mock 引擎（快速、免费、确定性），与线上 AI Provider 解耦。
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final FlowPilotProperties props;
    private final UserRepository userRepository;
    private final FlowTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;
    private final MessageRepository messageRepository;
    private final com.flowpilot.repository.NotificationJobRepository notificationJobRepository;
    private final com.flowpilot.service.ReportService reportService;
    private final MockChannelService mockChannelService;
    private final AnalysisService analysisService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataSeeder(FlowPilotProperties props, UserRepository userRepository,
                      FlowTemplateRepository templateRepository, ProjectRepository projectRepository,
                      MessageRepository messageRepository,
                      com.flowpilot.repository.NotificationJobRepository notificationJobRepository,
                      com.flowpilot.service.ReportService reportService,
                      MockChannelService mockChannelService,
                      AnalysisService analysisService) {
        this.props = props;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.projectRepository = projectRepository;
        this.messageRepository = messageRepository;
        this.notificationJobRepository = notificationJobRepository;
        this.reportService = reportService;
        this.mockChannelService = mockChannelService;
        this.analysisService = analysisService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        if (!props.isSeedDemo()) {
            return;
        }
        try {
            seedRemoteInstallScenario();
            seedProjectManagementScenario();
            seedRequirementUpdateScenario();
            seedSupportTicketScenario();
            seedDemoNotificationsAndReport();
            log.info("演示数据就绪：模板 {} 套，项目 {} 个（含模拟群聊与 AI 分析结果）",
                    templateRepository.count(), projectRepository.count());
        } catch (Exception e) {
            log.warn("演示数据初始化失败（不影响系统运行）: {}", e.getMessage());
        }
    }

    /** 演示通知 + 演示周报：让通知/报告中心开箱即有内容可看 */
    private void seedDemoNotificationsAndReport() {
        if (notificationJobRepository.count() == 0) {
            long pid = projectRepository.findByStatus(Project.Status.ACTIVE).stream()
                    .findFirst().map(Project::getId).orElse(1L);
            seedNotification(pid, com.flowpilot.model.NotificationJob.Type.RISK_ALERT,
                    "项目风险预警", "识别到以下风险：\n- 节点「开启远程权限」已超 SLA 3 小时\n- 客户侧联系人两天未回复消息",
                    "[{\"name\":\"李四\",\"role\":\"我方技术支持\",\"contact_type\":\"feishu\",\"contact_id\":\"ou_lisi\"}]");
            seedNotification(pid, com.flowpilot.model.NotificationJob.Type.SLA_OVERDUE,
                    "节点超时预警：「开启远程权限」", "节点「开启远程权限」SLA 要求 2 小时，已超时 3 小时，请及时跟进。",
                    "[{\"name\":\"张工\",\"role\":\"客户IT\",\"contact_type\":\"wecom\",\"contact_id\":\"zhanggong_it\"}]");
            seedNotification(pid, com.flowpilot.model.NotificationJob.Type.DAILY_DIGEST,
                    "每日进度摘要", "今日共 4 个进行中项目，其中 1 个卡顿、3 个预警，点击查看完整看板。", "[]");
            log.info("演示通知已生成 3 条");
        }
        java.nio.file.Path reportDir = java.nio.file.Path.of("./data/reports");
        try (var stream = java.nio.file.Files.list(reportDir)) {
            if (stream.findAny().isEmpty()) {
                ReportService.ReportSummary summary = reportService.buildSummary(
                        "周报", LocalDateTime.now().minusDays(7), LocalDateTime.now());
                reportService.generate(summary, reportDir);
                log.info("演示周报已生成到 data/reports");
            }
        } catch (java.nio.file.NoSuchFileException e) {
            ReportService.ReportSummary summary = reportService.buildSummary(
                    "周报", LocalDateTime.now().minusDays(7), LocalDateTime.now());
            reportService.generate(summary, reportDir);
        } catch (Exception ignored) {
        }
    }

    private void seedNotification(Long projectId, com.flowpilot.model.NotificationJob.Type type,
                                  String title, String content, String targets) {
        com.flowpilot.model.NotificationJob job = new com.flowpilot.model.NotificationJob();
        job.setProjectId(projectId);
        job.setType(type);
        job.setTitle(title);
        job.setContent(content);
        job.setTargetsJson(targets);
        job.setStatus(com.flowpilot.model.NotificationJob.Status.SENT);
        job.setExecutedAt(LocalDateTime.now().minusHours(2));
        notificationJobRepository.save(job);
    }

    private void seedUsers() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(encoder.encode("admin123"));
            admin.setDisplayName("企业管理员");
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
        }
        if (userRepository.findByUsername("manager").isEmpty()) {
            User manager = new User();
            manager.setUsername("manager");
            manager.setPasswordHash(encoder.encode("manager123"));
            manager.setDisplayName("流程负责人");
            manager.setRole(User.Role.MANAGER);
            userRepository.save(manager);
        }
    }

    // ---------- 场景一：远程安装设备（PRD 典型场景） ----------

    private void seedRemoteInstallScenario() {
        FlowTemplate t = template("远程安装设备",
                "客户远程设备安装标准流程：开通策略 → 开启远程权限 → 网络校验 → 远程安装 → 测试验收",
                """
                [
                  {"key":"open_policy","name":"开通策略","type":"start",
                   "completion_criteria":"客户后台显示策略已生效","responsible_roles":["客户IT","我方技术支持"],"sla_hours":4},
                  {"key":"enable_remote","name":"开启远程权限","type":"normal",
                   "completion_criteria":"远程桌面可连接","responsible_roles":["客户IT"],"sla_hours":2},
                  {"key":"network_check","name":"网络环境校验","type":"normal",
                   "completion_criteria":"网络连通性测试通过","responsible_roles":["我方技术支持"],"sla_hours":2},
                  {"key":"remote_install","name":"远程安装","type":"normal",
                   "completion_criteria":"设备安装完成并重启正常","responsible_roles":["我方技术支持"],"sla_hours":8},
                  {"key":"acceptance","name":"测试验收","type":"end",
                   "completion_criteria":"客户签字确认验收单","responsible_roles":["客户IT","销售"],"sla_hours":24}
                ]""",
                """
                [
                  {"condition":"如果客户已购买远程授权","from":"open_policy","to":"enable_remote"},
                  {"condition":"如果网络校验不通过","from":"network_check","to":"network_check"}
                ]""",
                """
                [
                  {"term":"策略","synonyms":["policy","授权策略"],"explanation":"设备远程管理的授权开通策略"},
                  {"term":"远程权限","synonyms":["remote","远程桌面"],"explanation":"客户设备远程访问权限"}
                ]""");
        project(t, "上海某某科技远程安装", "上海某某科技", "enable_remote", """
                客户IT张工确认了远程授权已购买，但暂时联系不上运维同事，远程权限迟迟没有开通。
                【风险】节点「开启远程权限」已超 SLA 3 小时
                【下一步】联系客户IT张工催促开启远程权限
                """);
    }

    // ---------- 场景二：软件项目管理 ----------

    private void seedProjectManagementScenario() {
        FlowTemplate t = template("软件项目管理流程",
                "软件交付项目的全生命周期管理：立项 → 需求确认 → 排期 → 开发 → 验收 → 上线 → 复盘",
                """
                [
                  {"key":"project_init","name":"项目立项","type":"start",
                   "completion_criteria":"立项评审通过，项目章程已发布","responsible_roles":["项目经理"],"sla_hours":24},
                  {"key":"requirement_confirm","name":"需求确认","type":"normal",
                   "completion_criteria":"需求规格说明书评审通过","responsible_roles":["产品经理","项目经理"],"sla_hours":48},
                  {"key":"schedule_plan","name":"排期计划","type":"normal",
                   "completion_criteria":"项目计划与里程碑已确认","responsible_roles":["项目经理"],"sla_hours":24},
                  {"key":"development","name":"开发实施","type":"normal",
                   "completion_criteria":"迭代功能开发完成并提测","responsible_roles":["研发团队"],"sla_hours":168},
                  {"key":"test_acceptance","name":"测试验收","type":"normal",
                   "completion_criteria":"测试通过，验收报告已签署","responsible_roles":["测试工程师","项目经理"],"sla_hours":72},
                  {"key":"release","name":"上线发布","type":"normal",
                   "completion_criteria":"生产环境发布完成","responsible_roles":["运维","研发负责人"],"sla_hours":24},
                  {"key":"retrospective","name":"项目复盘","type":"end",
                   "completion_criteria":"复盘会议纪要已归档","responsible_roles":["项目经理"],"sla_hours":72}
                ]""",
                """
                [
                  {"condition":"如果需求发生变更","from":"requirement_confirm","to":"requirement_confirm"},
                  {"condition":"如果测试不通过","from":"test_acceptance","to":"development"}
                ]""",
                """
                [
                  {"term":"里程碑","synonyms":["milestone"],"explanation":"项目关键时间节点"},
                  {"term":"迭代","synonyms":["sprint","版本"],"explanation":"按周期交付的版本"},
                  {"term":"验收报告","synonyms":["acceptance report"],"explanation":"项目验收签署文档"}
                ]""");
        project(t, "杭州云启-数据中台建设项目", "杭州云启科技", "schedule_plan");
    }

    // ---------- 场景三：软件需求更新 ----------

    private void seedRequirementUpdateScenario() {
        FlowTemplate t = template("软件需求更新流程",
                "软件产品需求从收集到客户验收的标准流程：收集 → 评审 → 设计 → 开发 → 回归 → 验收",
                """
                [
                  {"key":"requirement_collection","name":"需求收集","type":"start",
                   "completion_criteria":"需求单已录入系统","responsible_roles":["产品经理"],"sla_hours":24},
                  {"key":"requirement_review","name":"需求评审","type":"normal",
                   "completion_criteria":"评审会议纪要已确认","responsible_roles":["产品经理","研发负责人"],"sla_hours":48},
                  {"key":"solution_design","name":"方案设计","type":"normal",
                   "completion_criteria":"技术方案评审通过","responsible_roles":["研发负责人"],"sla_hours":48},
                  {"key":"development","name":"开发实现","type":"normal",
                   "completion_criteria":"代码合入主干并自测通过","responsible_roles":["研发工程师"],"sla_hours":96},
                  {"key":"regression_test","name":"回归测试","type":"normal",
                   "completion_criteria":"测试用例全部通过","responsible_roles":["测试工程师"],"sla_hours":48},
                  {"key":"customer_acceptance","name":"客户验收","type":"end",
                   "completion_criteria":"客户确认验收单","responsible_roles":["产品经理","客户"],"sla_hours":72}
                ]""",
                """
                [
                  {"condition":"如果需求影响范围大","from":"solution_design","to":"solution_design"}
                ]""",
                """
                [
                  {"term":"需求单","synonyms":["demand ticket","工单"],"explanation":"需求录入凭证"},
                  {"term":"验收单","synonyms":["acceptance form"],"explanation":"客户验收签署文档"}
                ]""");
        project(t, "深圳智联-ERP需求更新V2.3", "深圳智联软件", "regression_test");
    }

    // ---------- 场景四：客户支持工单（带卡顿风险演示） ----------

    private void seedSupportTicketScenario() {
        FlowTemplate t = template("客户支持工单流程",
                "客户问题工单处理流程：受理 → 诊断 → 方案确认 → 处理 → 客户验证 → 归档",
                """
                [
                  {"key":"ticket_accept","name":"工单受理","type":"start",
                   "completion_criteria":"工单已创建并指派","responsible_roles":["客服","技术支持"],"sla_hours":2},
                  {"key":"diagnose","name":"问题诊断","type":"normal",
                   "completion_criteria":"问题根因已定位","responsible_roles":["技术支持"],"sla_hours":4},
                  {"key":"solution_confirm","name":"方案确认","type":"normal",
                   "completion_criteria":"解决方案已获客户同意","responsible_roles":["技术支持","客户"],"sla_hours":8},
                  {"key":"fix_implement","name":"处理实施","type":"normal",
                   "completion_criteria":"问题已修复并验证","responsible_roles":["技术支持"],"sla_hours":24},
                  {"key":"customer_verify","name":"客户验证","type":"normal",
                   "completion_criteria":"客户确认问题解决","responsible_roles":["客户"],"sla_hours":24},
                  {"key":"ticket_close","name":"关闭归档","type":"end",
                   "completion_criteria":"工单已关闭并归档","responsible_roles":["客服"],"sla_hours":4}
                ]""",
                """
                [
                  {"condition":"如果问题复现","from":"customer_verify","to":"diagnose"}
                ]""",
                """
                [
                  {"term":"工单","synonyms":["ticket","case"],"explanation":"客户问题处理凭证"},
                  {"term":"SLA","synonyms":["时效"],"explanation":"服务等级协议"}
                ]""");
        Project p = project(t, "广州迅达-网络故障工单", "广州迅达物流", "solution_confirm", """
                【风险】客户反馈故障又复现了，远程排查无果
                【风险】客户侧联系人两天未回复消息，沟通停滞
                【风险】备用设备到货延迟，解决方案无法实施
                """);
    }

    // ---------- 构建工具 ----------

    /** 幂等创建模板：按名称查重 */
    private FlowTemplate template(String name, String description, String nodes,
                                  String branches, String glossary) {
        return templateRepository.findByNameContainingIgnoreCaseAndStatusNotOrderByUpdatedAtDesc(
                        name, FlowTemplate.Status.ARCHIVED, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().filter(t -> t.getName().equals(name)).findFirst()
                .orElseGet(() -> {
                    FlowTemplate t = new FlowTemplate();
                    t.setName(name);
                    t.setDescription(description);
                    t.setStatus(FlowTemplate.Status.ACTIVE);
                    t.setVersion(1);
                    t.setSourceDocName(name + "操作文档.docx（演示数据）");
                    t.setNodesJson(nodes);
                    t.setBranchesJson(branches);
                    t.setGlossaryJson(glossary);
                    t.setCreatedBy("system");
                    t = templateRepository.save(t);
                    log.info("演示模板已创建: {}", name);
                    return t;
                });
    }

    /** 幂等创建项目 + 模拟群聊 + Mock AI 分析（extraRiskMessages 在分析前入库，影响风险判定） */
    private Project project(FlowTemplate template, String projectName, String customerName,
                            String advanceTo, String extraRiskMessages) {
        if (projectRepository.existsByName(projectName)) {
            return null;
        }
        Project p = new Project();
        p.setCode("P" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%03d", projectRepository.count() + 1));
        p.setName(projectName);
        p.setTemplateId(template.getId());
        p.setTemplateName(template.getName());
        p.setTemplateSnapshotJson("{\"name\":\"" + template.getName() + "\",\"nodes\":" + template.getNodesJson()
                + ",\"branches\":" + template.getBranchesJson() + ",\"glossary\":" + template.getGlossaryJson() + "}");
        p.setCustomerName(customerName);
        p.setStatus(Project.Status.ACTIVE);
        p.setRiskStatus(Project.RiskStatus.NORMAL);
        p.setStartedAt(LocalDateTime.now().minusDays(2));
        p.setOwnerId(2L);
        p.setCreatedBy("system");
        p = projectRepository.save(p);

        // 生成一段仿真群聊并立即用 Mock 引擎分析（免费、确定性、不依赖外部 AI）
        try {
            mockChannelService.generate(p.getId(), advanceTo);
            if (extraRiskMessages != null) {
                extraMessages(p, extraRiskMessages);
            }
            analysisService.analyzeWith(p.getId(), com.flowpilot.model.AnalysisRun.TriggerType.SCHEDULE,
                    new MockLlmProvider());
            log.info("演示项目已创建并完成分析: {}", projectName);
        } catch (Exception e) {
            log.warn("演示项目 {} 分析失败: {}", projectName, e.getMessage());
        }
        return p;
    }

    private Project project(FlowTemplate template, String projectName, String customerName, String advanceTo) {
        return project(template, projectName, customerName, advanceTo, null);
    }

    /** 追加额外演示消息（用于制造多样风险状态） */
    private void extraMessages(Project project, String contents) {
        if (project == null) {
            return;
        }
        int seq = 0;
        for (String line : contents.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            Message m = new Message();
            m.setProjectId(project.getId());
            m.setChannelType(Message.ChannelType.MOCK);
            m.setChannelId("mock_" + project.getId());
            m.setMsgId("seed_extra_" + project.getId() + "_" + seq++);
            m.setSenderName("系统演示");
            m.setContent(line.trim());
            m.setSentAt(LocalDateTime.now().minusMinutes(10 - seq));
            m.setSource("MOCK");
            messageRepository.save(m);
        }
    }
}
