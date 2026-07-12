package com.adlin.orin.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adlin.orin.modules.knowledge.controller.KnowledgeManageController;
import com.adlin.orin.modules.model.controller.ModelConfigController;
import com.adlin.orin.modules.task.controller.TaskQueueController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminSurfaceAuthorizationContractTest {

    @Test
    void platformModelConfigurationIsAdminOnly() {
        assertAdminOnly(ModelConfigController.class.getAnnotation(PreAuthorize.class));
    }

    @Test
    void legacyGlobalTaskQueueIsAdminOnly() {
        assertAdminOnly(TaskQueueController.class.getAnnotation(PreAuthorize.class));
    }

    @Test
    void knowledgeInfrastructureDiagnosisIsAdminOnly() throws Exception {
        Method method = KnowledgeManageController.class.getMethod("diagnoseMilvus");
        assertAdminOnly(method.getAnnotation(PreAuthorize.class));
    }

    private void assertAdminOnly(PreAuthorize annotation) {
        assertNotNull(annotation);
        assertTrue(annotation.value().contains("ADMIN"));
        assertTrue(annotation.value().contains("SUPER_ADMIN"));
        assertTrue(annotation.value().contains("PLATFORM_ADMIN"));
    }
}
