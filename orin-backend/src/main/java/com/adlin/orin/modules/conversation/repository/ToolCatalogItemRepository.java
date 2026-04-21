package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.ToolCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolCatalogItemRepository extends JpaRepository<ToolCatalogItem, String> {
}
