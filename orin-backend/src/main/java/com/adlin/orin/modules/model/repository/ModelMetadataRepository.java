package com.adlin.orin.modules.model.repository;

import com.adlin.orin.modules.model.entity.ModelMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelMetadataRepository extends JpaRepository<ModelMetadata, Long> {
    List<ModelMetadata> findByStatus(String status);

    java.util.Optional<ModelMetadata> findByModelId(String modelId);
}
