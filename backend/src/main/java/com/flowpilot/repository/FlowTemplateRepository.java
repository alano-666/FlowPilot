package com.flowpilot.repository;

import com.flowpilot.model.FlowTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowTemplateRepository extends JpaRepository<FlowTemplate, Long> {
    Page<FlowTemplate> findByStatusNotOrderByUpdatedAtDesc(FlowTemplate.Status exclude, Pageable pageable);

    Page<FlowTemplate> findByNameContainingIgnoreCaseAndStatusNotOrderByUpdatedAtDesc(
            String name, FlowTemplate.Status exclude, Pageable pageable);
}
