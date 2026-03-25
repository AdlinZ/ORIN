package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollabSubtaskRepository extends JpaRepository<CollabSubtaskEntity, Long> {

    List<CollabSubtaskEntity> findByPackageId(String packageId);

    Optional<CollabSubtaskEntity> findByPackageIdAndSubTaskId(String packageId, String subTaskId);

    List<CollabSubtaskEntity> findByStatus(String status);

    List<CollabSubtaskEntity> findByPackageIdAndStatus(String packageId, String status);
}