package com.adlin.orin.modules.multimodal.repository;

import com.adlin.orin.modules.multimodal.entity.MultimodalTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MultimodalTaskRepository extends JpaRepository<MultimodalTask, Long> {
    List<MultimodalTask> findAllByOrderByCreatedAtDesc();
}
