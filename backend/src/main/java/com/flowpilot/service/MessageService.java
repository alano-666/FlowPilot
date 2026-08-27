package com.flowpilot.service;

import com.flowpilot.model.Message;
import com.flowpilot.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务：多来源消息入库（按渠道+msgId 去重）与查询。
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /** 批量入库，返回实际新增条数（自动去重） */
    @Transactional
    public int saveAll(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        List<Message> fresh = new ArrayList<>();
        for (Message m : messages) {
            if (m.getMsgId() == null || m.getMsgId().isBlank()) {
                m.setMsgId("local_" + System.nanoTime() + "_" + m.hashCode());
            }
            if (!messageRepository.existsByChannelTypeAndChannelIdAndMsgId(
                    m.getChannelType(), m.getChannelId(), m.getMsgId())) {
                fresh.add(m);
            }
        }
        messageRepository.saveAll(fresh);
        return fresh.size();
    }

    /** 单条入库，返回是否新增 */
    @Transactional
    public boolean save(Message m) {
        if (m.getMsgId() == null || m.getMsgId().isBlank()) {
            m.setMsgId("local_" + System.nanoTime());
        }
        if (messageRepository.existsByChannelTypeAndChannelIdAndMsgId(
                m.getChannelType(), m.getChannelId(), m.getMsgId())) {
            return false;
        }
        messageRepository.save(m);
        return true;
    }

    /** 水位线之后的增量消息（升序） */
    public List<Message> newMessages(Long projectId, LocalDateTime after) {
        if (after == null) {
            return messageRepository.findByProjectIdOrderBySentAtAsc(projectId);
        }
        return messageRepository.findByProjectIdAndSentAtAfterOrderBySentAtAsc(projectId, after);
    }

    public Page<Message> pageByProject(Long projectId, int page, int size) {
        return messageRepository.findByProjectIdOrderBySentAtDesc(projectId, PageRequest.of(page, size));
    }

    public long countByProject(Long projectId) {
        return messageRepository.countByProjectId(projectId);
    }
}
