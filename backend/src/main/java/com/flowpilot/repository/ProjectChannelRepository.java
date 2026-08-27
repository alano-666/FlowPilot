package com.flowpilot.repository;

import com.flowpilot.model.ProjectChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectChannelRepository extends JpaRepository<ProjectChannel, Long> {
    List<ProjectChannel> findByProjectId(Long projectId);

    Optional<ProjectChannel> findByProjectIdAndChannelTypeAndChannelId(
            Long projectId, ProjectChannel.ChannelType channelType, String channelId);

    boolean existsByProjectIdAndChannelTypeAndChannelId(
            Long projectId, ProjectChannel.ChannelType channelType, String channelId);

    List<ProjectChannel> findByChannelTypeAndSyncEnabledTrue(ProjectChannel.ChannelType channelType);
}
