package com.flowpilot.repository;

import com.flowpilot.model.CalibrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibrationLogRepository extends JpaRepository<CalibrationLog, Long> {
    List<CalibrationLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
