package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationPackageRepository extends JpaRepository<CollaborationPackageEntity, Long> {

    Optional<CollaborationPackageEntity> findByPackageId(String packageId);

    List<CollaborationPackageEntity> findByStatus(String status);

    List<CollaborationPackageEntity> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<CollaborationPackageEntity> findByTraceId(String traceId);

    List<CollaborationPackageEntity> findByRootTaskId(Long rootTaskId);

    List<CollaborationPackageEntity> findByIntentPriority(String intentPriority);

    List<CollaborationPackageEntity> findByStatusAndCreatedBy(String status, String createdBy);

    List<CollaborationPackageEntity> findByIntentPriorityAndCreatedBy(String intentPriority, String createdBy);

    List<CollaborationPackageEntity> findByIntentCategory(String category);
}