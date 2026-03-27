package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class KnowledgeTypeConverter implements AttributeConverter<KnowledgeType, String> {

    @Override
    public String convertToDatabaseColumn(KnowledgeType attribute) {
        return attribute == null ? KnowledgeType.UNSTRUCTURED.name() : attribute.name();
    }

    @Override
    public KnowledgeType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return KnowledgeType.UNSTRUCTURED;
        }

        if ("DOCUMENT".equalsIgnoreCase(dbData)) {
            return KnowledgeType.UNSTRUCTURED;
        }

        try {
            return KnowledgeType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return KnowledgeType.UNSTRUCTURED;
        }
    }
}
