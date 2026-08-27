package com.flowpilot.channel;

import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.Project;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 邮件渠道测试：项目归属匹配规则与正文提取工具。
 */
class EmailChannelServiceTest {

    private EmailChannelService service;

    @BeforeEach
    void setUp() {
        FlowPilotProperties props = new FlowPilotProperties();
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        MessageService messageService = Mockito.mock(MessageService.class);

        Project p1 = new Project();
        p1.setId(1L);
        p1.setName("上海某某科技远程安装");
        p1.setCustomerName("上海某某科技");
        Project p2 = new Project();
        p2.setId(2L);
        p2.setName("杭州云启-数据中台建设项目");
        p2.setCustomerName("杭州云启科技");

        Mockito.when(projectRepository.findByStatus(Project.Status.ACTIVE))
                .thenReturn(List.of(p1, p2));
        service = new EmailChannelService(props, projectRepository, messageService);
    }

    @Test
    void matchesByCustomerNameInSubject() {
        assertEquals(1L, service.matchProject("【上海某某科技】远程安装验收确认邮件"));
    }

    @Test
    void matchesByProjectNameInSender() {
        assertEquals(2L, service.matchProject("来自 数据中台建设 项目组的周报"));
    }

    @Test
    void fallsBackToSingleActiveProject() {
        // 两个进行中项目且无匹配 → null（不误归属）
        assertNull(service.matchProject("完全无关的营销邮件"));
    }

    @Test
    void singleActiveProjectFallback() {
        FlowPilotProperties props = new FlowPilotProperties();
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        Project only = new Project();
        only.setId(9L);
        only.setName("唯一项目");
        Mockito.when(projectRepository.findByStatus(Project.Status.ACTIVE)).thenReturn(List.of(only));
        EmailChannelService single = new EmailChannelService(props, projectRepository,
                Mockito.mock(MessageService.class));
        assertEquals(9L, single.matchProject("任意邮件"));
    }

    @Test
    void nullHaystackSafe() {
        assertNull(service.matchProject(null));
    }
}
