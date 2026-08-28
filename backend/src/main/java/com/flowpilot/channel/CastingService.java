package com.flowpilot.channel;

import com.flowpilot.common.BizException;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.AnalysisService;
import com.flowpilot.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 虚拟群演服务（无真实飞书用户时的演示方案）：
 *
 * 内置多幕「剧本」，每幕由不同虚拟身份（客户/产品/研发/测试/运维）发布
 * 刻意模糊、不确定的群聊消息（"差不多了""应该没问题"），以飞书渠道消息
 * 写入系统并自动触发 AI 分析——AI 的价值正在于从这些含糊的对话中判断进度与风险。
 *
 * 消息走与真实飞书事件回调完全一致的入库与分析链路（channelType=FEISHU），
 * 若项目绑定了真实飞书群且 delivery=feishu，则由机器人把台词真实发进群里，
 * 经官方回调回流（双重真实）。
 */
@Service
public class CastingService {

    /** 一幕戏：角色身份 + 台词 */
    public record Line(String name, String role, String openId, String content) {
    }

    public record Scene(int number, String title, String description, List<Line> lines) {
    }

    private final ProjectRepository projectRepository;
    private final ProjectChannelRepository channelRepository;
    private final MessageService messageService;
    private final AnalysisService analysisService;
    private final FeishuClient feishuClient;

    public CastingService(ProjectRepository projectRepository, ProjectChannelRepository channelRepository,
                          MessageService messageService, AnalysisService analysisService,
                          FeishuClient feishuClient) {
        this.projectRepository = projectRepository;
        this.channelRepository = channelRepository;
        this.messageService = messageService;
        this.analysisService = analysisService;
        this.feishuClient = feishuClient;
    }

    /** 内置剧本：对应「软件需求更新与部署流程」的 8 个节点，台词刻意含糊 */
    public static final List<Scene> SCRIPT = List.of(
            new Scene(1, "需求收集", "客户提需求，产品接单", List.of(
                    new Line("陈总", "客户", "ou_cast_chen", "我们这边提了个新需求，单子已经发过去了，你们先看看吧，挺急的"),
                    new Line("王五", "产品经理", "ou_cast_wang", "收到陈总，我看了下需求单，大概是报表那边要加几个统计维度，我整理一下"),
                    new Line("王五", "产品经理", "ou_cast_wang", "【干系人】陈总|客户|wechat|chenzong")
            )),
            new Scene(2, "需求评审", "三方评审，结论基本通过但留了尾巴", List.of(
                    new Line("王五", "产品经理", "ou_cast_wang", "明天下午三点评审会，研发和测试都参加一下，需求范围我发群里了"),
                    new Line("李四", "研发负责人", "ou_cast_li", "范围我看了，改动不大，就是报表服务最近性能一般，可能要顺带优化"),
                    new Line("王五", "产品经理", "ou_cast_wang", "评审开完了，结论基本是通过，还有两个细节要再确认一下"),
                    new Line("王五", "产品经理", "ou_cast_wang", "【干系人】李四|研发负责人|feishu|ou_cast_li")
            )),
            new Scene(3, "方案设计", "技术方案评审通过", List.of(
                    new Line("李四", "研发负责人", "ou_cast_li", "技术方案我写完了，主要在报表服务加一个聚合层，改动不算大，你们看下"),
                    new Line("赵六", "测试工程师", "ou_cast_zhao", "方案我看了，聚合层这块建议补充边界值用例"),
                    new Line("李四", "研发负责人", "ou_cast_li", "方案评审通过了，可以开工"),
                    new Line("李四", "研发负责人", "ou_cast_li", "【干系人】赵六|测试工程师|feishu|ou_cast_zhao")
            )),
            new Scene(4, "开发实现", "开发进展模糊，自我感觉良好", List.of(
                    new Line("孙七", "研发工程师", "ou_cast_sun", "代码写得差不多了，就是有个接口的返回格式和文档对不上，我在调"),
                    new Line("孙七", "研发工程师", "ou_cast_sun", "已经合入主干了，自测跑了一遍，应该没什么大问题"),
                    new Line("李四", "研发负责人", "ou_cast_li", "合入的代码我瞄了一眼，整体OK，细节问题后面再跟"),
                    new Line("孙七", "研发工程师", "ou_cast_sun", "【干系人】孙七|研发工程师|feishu|ou_cast_sun")
            )),
            new Scene(5, "回归测试", "测试结果含糊，疑似环境问题", List.of(
                    new Line("赵六", "测试工程师", "ou_cast_zhao", "回归跑了一大半，大部分用例都过了，有两个失败的看起来像环境问题，我再确认下"),
                    new Line("赵六", "测试工程师", "ou_cast_zhao", "全部用例通过了，那两个确实是环境问题，测试报告我出了")
            )),
            new Scene(6, "客户验收", "客户基本满意但提了小意见", List.of(
                    new Line("王五", "产品经理", "ou_cast_wang", "陈总，验收演示约明天上午十点可以吗？"),
                    new Line("陈总", "客户", "ou_cast_chen", "可以，我们这边负责人也一起看"),
                    new Line("王五", "产品经理", "ou_cast_wang", "演示完了，客户提了个小意见，说报表导出的格式能不能再改改"),
                    new Line("陈总", "客户", "ou_cast_chen", "其他都挺好，验收单我明天签了发你")
            )),
            new Scene(7, "生产部署", "部署完成但出现波动，虚惊一场", List.of(
                    new Line("周八", "运维工程师", "ou_cast_zhou", "版本我已经发到生产了，发布脚本跑完了，服务起来了"),
                    new Line("周八", "运维工程师", "ou_cast_zhou", "不过线上流量上来之后 CPU 有点高，我先观察一下，不排除要回滚"),
                    new Line("周八", "运维工程师", "ou_cast_zhou", "虚惊一场，是监控采集配置问题，业务指标都正常"),
                    new Line("周八", "运维工程师", "ou_cast_zhou", "【干系人】周八|运维工程师|feishu|ou_cast_zhou")
            )),
            new Scene(8, "上线验证", "验证通过，流程关闭", List.of(
                    new Line("王五", "产品经理", "ou_cast_wang", "上线一天了，没接到客户投诉，这个需求可以关闭了"),
                    new Line("李四", "研发负责人", "ou_cast_li", "报表性能比之前好了不少，这个需求就算交付了")
            ))
    );

    /** 剧本列表（前端展示） */
    public List<Scene> script() {
        return SCRIPT;
    }

    /**
     * 上演一幕。
     * @param delivery virtual=本地虚拟注入（默认）；feishu=机器人真实发送到已绑定飞书群
     * @return 本幕消息条数
     */
    @Transactional
    public int cast(Long projectId, int sceneNumber, String delivery) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        Scene scene = SCRIPT.stream().filter(s -> s.number() == sceneNumber).findFirst()
                .orElseThrow(() -> new BizException(40009, "剧本幕数不存在: " + sceneNumber + "（可选 1~" + SCRIPT.size() + "）"));

        if ("feishu".equalsIgnoreCase(delivery)) {
            return castToRealGroup(project, scene);
        }
        return castVirtual(project, scene);
    }

    /** 虚拟注入：直接写入飞书渠道消息（与真实事件同链路），自动触发 AI 分析 */
    private int castVirtual(Project project, Scene scene) {
        String channelId = "cast_virtual_" + project.getId();
        if (!channelRepository.existsByProjectIdAndChannelTypeAndChannelId(
                project.getId(), ProjectChannel.ChannelType.FEISHU, channelId)) {
            ProjectChannel pc = new ProjectChannel();
            pc.setProjectId(project.getId());
            pc.setChannelType(ProjectChannel.ChannelType.FEISHU);
            pc.setChannelId(channelId);
            pc.setChannelName("虚拟群演(第" + scene.number() + "幕:" + scene.title() + ")");
            pc.setSyncEnabled(false);
            channelRepository.save(pc);
        }
        LocalDateTime t = LocalDateTime.now();
        int seq = 0;
        for (Line line : scene.lines()) {
            Message m = new Message();
            m.setProjectId(project.getId());
            m.setChannelType(Message.ChannelType.FEISHU);
            m.setChannelId(channelId);
            m.setMsgId("cast_" + project.getId() + "_" + scene.number() + "_" + seq++);
            m.setSenderId(line.openId());
            m.setSenderName(line.name());
            m.setContent(line.content());
            m.setMsgType(Message.MsgType.TEXT);
            m.setSentAt(t.plusMinutes(seq));
            m.setSource("EVENT");
            messageService.save(m);
        }
        analysisService.analyzeAsync(project.getId(), "EVENT");
        return scene.lines().size();
    }

    /** 真实群模式：机器人把台词发进项目绑定的飞书群，经官方回调回流 */
    private int castToRealGroup(Project project, Scene scene) {
        ProjectChannel feishu = channelRepository.findByProjectId(project.getId()).stream()
                .filter(c -> c.getChannelType() == ProjectChannel.ChannelType.FEISHU && c.isSyncEnabled()
                        && !c.getChannelId().startsWith("cast_virtual_"))
                .findFirst()
                .orElseThrow(() -> new BizException(40009, "项目未绑定真实飞书群，无法使用 feishu 发送模式（先绑定群 chat_id）"));
        if (!feishuClient.configured()) {
            throw new BizException(50030, "飞书未配置凭证");
        }
        for (Line line : scene.lines()) {
            // 机器人统一发送，台词带角色前缀保持身份清晰
            feishuClient.sendTextToChat(feishu.getChannelId(),
                    "【" + line.name() + "·" + line.role() + "】" + line.content());
        }
        return scene.lines().size();
    }
}
