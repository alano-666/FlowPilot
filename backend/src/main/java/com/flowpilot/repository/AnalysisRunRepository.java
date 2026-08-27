package com.flowpilot.repository;

import com.flowpilot.model.AnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    List<AnalysisRun> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
