package com.flowpilot.repository;

import com.flowpilot.model.PendingSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingSuggestionRepository extends JpaRepository<PendingSuggestion, Long> {
    List<PendingSuggestion> findByProjectIdAndStatusOrderByCreatedAtDesc(
            Long projectId, PendingSuggestion.Status status);

    List<PendingSuggestion> findByStatus(PendingSuggestion.Status status);
    void deleteByProjectId(Long projectId);

}
