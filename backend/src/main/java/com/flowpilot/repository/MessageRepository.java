package com.flowpilot.repository;

import com.flowpilot.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    boolean existsByChannelTypeAndChannelIdAndMsgId(
            Message.ChannelType channelType, String channelId, String msgId);

    List<Message> findByProjectIdAndSentAtAfterOrderBySentAtAsc(Long projectId, LocalDateTime after);

    List<Message> findByProjectIdOrderBySentAtAsc(Long projectId);

    Page<Message> findByProjectIdOrderBySentAtDesc(Long projectId, Pageable pageable);

    long countByProjectId(Long projectId);

    /** 数据留存清理：删除指定时间之前的原始消息 */
    long deleteBySentAtBefore(LocalDateTime before);
}
