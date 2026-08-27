package com.flowpilot.repository;

import com.flowpilot.model.Stakeholder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StakeholderRepository extends JpaRepository<Stakeholder, Long> {
    List<Stakeholder> findByProjectIdOrderByNodeKeyAsc(Long projectId);

    List<Stakeholder> findByProjectIdAndNodeKey(Long projectId, String nodeKey);

    void deleteByProjectId(Long projectId);
}
