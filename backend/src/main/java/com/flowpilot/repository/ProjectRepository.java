package com.flowpilot.repository;

import com.flowpilot.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    List<Project> findByStatusAndRiskStatusNot(Project.Status status, Project.RiskStatus riskStatus);

    List<Project> findByStatus(Project.Status status);

    Page<Project> findByStatusNot(Project.Status exclude, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByTemplateId(Long templateId);
}
