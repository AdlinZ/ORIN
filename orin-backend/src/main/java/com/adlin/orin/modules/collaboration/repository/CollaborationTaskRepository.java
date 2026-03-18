package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollaborationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborationTaskRepository extends JpaRepository<CollaborationTask, Long> {

    List<CollaborationTask> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<CollaborationTask> findByStatusOrderByCreatedAtDesc(String status);
}
