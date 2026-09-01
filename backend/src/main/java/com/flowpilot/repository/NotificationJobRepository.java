package com.flowpilot.repository;

import com.flowpilot.model.NotificationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {
    Page<NotificationJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<NotificationJob> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    List<NotificationJob> findByStatus(NotificationJob.Status status);

    List<NotificationJob> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    void deleteByProjectId(Long projectId);

}
