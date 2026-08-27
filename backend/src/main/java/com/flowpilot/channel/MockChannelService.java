package com.flowpilot.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;
import com.flowpilot.model.FlowTemplate;
import com.flowpilot.model.Message;
import com.flowpilot.model.Project;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.repository.FlowTemplateRepository;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 演示渠道：无任何外部凭证时生成仿真群聊，驱动完整业务闭环。
 * 消息中携带标记供 MockLlmProvider 识别（【完成】/【干系人】/【风险】/【下一步】），
 * 供用户在未接入真实渠道前体验产品全流程。
 */
@Service
public class MockChannelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectRepository projectRepository;
    private final FlowTemplateRepository templateRepository;
    private final ProjectChannelRepository channelRepository;
    private final MessageService messageService;

    public MockChannelService(ProjectRepository projectRepository, FlowTemplateRepository templateRepository,
                              ProjectChannelRepository channelRepository, MessageService messageService) {
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.channelRepository = channelRepository;
        this.messageService = messageService;
    }

    private static final List<Map<String, String>> ROLES = List.of(
            Map.of("name", "张工", "role", "客户IT", "type", "wecom", "id", "zhanggong_it"),
            Map.of("name", "李四", "role", "我方技术支持", "type", "feishu", "id", "ou_lisi"),
            Map.of("name", "王五", "role", "销售", "type", "wechat", "id", "wangwu_sales"));

    /**
     * 为项目生成一段仿真对话，推进到指定节点（advanceTo 为节点 key，null 则推进到中间节点）。
     */
    @Transactional
    public List<Message> generate(Long projectId, String advanceTo) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(40401, "项目不存在: " + projectId));
        FlowTemplate template = templateRepository.findById(project.getTemplateId())
                .orElseThrow(() -> new BizException(40402, "模板不存在"));
        List<Map<String, Object>> nodes = readNodes(template.getNodesJson());
        if (nodes.isEmpty()) {
            throw new BizException(40910, "模板没有节点，无法生成演示对话");
        }

        // 绑定 mock 渠道
        if (!channelRepository.existsByProjectIdAndChannelTypeAndChannelId(
                projectId, ProjectChannel.ChannelType.MOCK, "mock_" + projectId)) {
            ProjectChannel pc = new ProjectChannel();
            pc.setProjectId(projectId);
            pc.setChannelType(ProjectChannel.ChannelType.MOCK);
            pc.setChannelId("mock_" + projectId);
            pc.setChannelName("演示群聊(自动生成)");
            channelRepository.save(pc);
        }

        // 推进位置：默认完成一半节点
        int advanceIdx = nodes.size() - 1;
        if (advanceTo != null && !advanceTo.isBlank()) {
            for (int i = 0; i < nodes.size(); i++) {
                if (String.valueOf(nodes.get(i).get("key")).equals(advanceTo)) {
                    advanceIdx = Math.max(0, i - 1);
                    break;
                }
            }
        } else {
            advanceIdx = Math.max(0, nodes.size() / 2 - 1);
        }

        List<Message> generated = new ArrayList<>();
        // 消息时间基准：晚于项目最近分析水位线，保证每次生成都能被增量分析拾取
        LocalDateTime t = project.getLastAnalyzedAt() == null
                ? LocalDateTime.now().minusHours(6)
                : project.getLastAnalyzedAt().plusMinutes(5);
        int seq = 0;
        for (int i = 0; i <= advanceIdx && i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            String name = String.valueOf(node.get("name"));
            String key = String.valueOf(node.get("key"));
            Map<String, String> person = ROLES.get(i % ROLES.size());

            // 开工消息
            generated.add(build(projectId, t.plusMinutes(seq++ * 7), person,
                    "收到，「" + name + "」这个环节我来对接。"));
            // 干系人标记
            generated.add(build(projectId, t.plusMinutes(seq++ * 7), person,
                    "【干系人】" + person.get("name") + "|" + person.get("role") + "|"
                            + person.get("type") + "|" + person.get("id")));
            // 完成消息
            generated.add(build(projectId, t.plusMinutes(seq++ * 7), person,
                    "【完成】" + name + " " + key + " 已完成，" + name + "环节验收通过。"));
        }
        // 当前进行中节点：一条推进中的消息 + 下一步指引
        int curIdx = Math.min(advanceIdx + 1, nodes.size() - 1);
        Map<String, Object> cur = nodes.get(curIdx);
        Map<String, String> curPerson = ROLES.get(curIdx % ROLES.size());
        generated.add(build(projectId, t.plusMinutes(seq++ * 7), curPerson,
                "「" + cur.get("name") + "」正在进行中，需要客户侧配合。"));
        generated.add(build(projectId, t.plusMinutes(seq++ * 7), curPerson,
                "【干系人】" + curPerson.get("name") + "|" + curPerson.get("role") + "|"
                        + curPerson.get("type") + "|" + curPerson.get("id")
                        + " 【下一步】推进节点「" + cur.get("name") + "」"));
        if (curIdx > 0) {
            generated.add(build(projectId, t.plusMinutes(seq++ * 7), curPerson,
                    "【风险】节点「" + cur.get("name") + "」已持续 2 小时未推进，请注意跟进。"));
        }
        messageService.saveAll(generated);
        return generated;
    }

    private Message build(Long projectId, LocalDateTime time, Map<String, String> person, String content) {
        Message m = new Message();
        m.setProjectId(projectId);
        m.setChannelType(Message.ChannelType.MOCK);
        m.setChannelId("mock_" + projectId);
        m.setMsgId("mock_" + projectId + "_" + time + "_" + content.hashCode());
        m.setSenderId(person.get("id"));
        m.setSenderName(person.get("name"));
        m.setContent(content);
        m.setMsgType(Message.MsgType.TEXT);
        m.setSentAt(time);
        m.setSource("MOCK");
        return m;
    }

    private List<Map<String, Object>> readNodes(String json) {
        try {
            return MAPPER.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
