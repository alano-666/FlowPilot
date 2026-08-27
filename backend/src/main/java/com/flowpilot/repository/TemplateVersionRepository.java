package com.flowpilot.repository;

import com.flowpilot.model.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {
    List<TemplateVersion> findByTemplateIdOrderByVersionDesc(Long templateId);
}
