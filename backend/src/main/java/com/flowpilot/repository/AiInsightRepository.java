package com.flowpilot.repository;

import com.flowpilot.model.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {
    List<AiInsight> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<AiInsight> findByRunId(Long runId);
    void deleteByProjectId(Long projectId);

}
