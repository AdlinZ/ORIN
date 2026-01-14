package com.adlin.orin.modules.model.repository;

import com.adlin.orin.modules.model.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    Optional<ModelConfig> findFirstByOrderByIdDesc();
}