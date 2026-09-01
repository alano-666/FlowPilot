package com.flowpilot.repository;

import com.flowpilot.model.ImportRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {
    Page<ImportRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ImportRecord> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    void deleteByProjectId(Long projectId);

}
