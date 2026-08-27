package com.flowpilot.repository;

import com.flowpilot.model.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncStateRepository extends JpaRepository<SyncState, Long> {
    Optional<SyncState> findByChannelTypeAndChannelId(String channelType, String channelId);
}
