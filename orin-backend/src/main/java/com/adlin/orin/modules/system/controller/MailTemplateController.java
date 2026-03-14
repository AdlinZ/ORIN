package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailTemplate;
import com.adlin.orin.modules.system.service.MailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件模板管理 Controller
 */
@RestController
@RequestMapping("/api/v1/system/mail-templates")
@RequiredArgsConstructor
public class MailTemplateController {

    private final MailTemplateService templateService;

    /**
     * 获取所有模板
     */
    @GetMapping
    public ResponseEntity<List<MailTemplate>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    /**
     * 获取启用的模板
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<MailTemplate>> getEnabledTemplates() {
        return ResponseEntity.ok(templateService.getEnabledTemplates());
    }

    /**
     * 根据ID获取模板
     */
    @GetMapping("/{id}")
    public ResponseEntity<MailTemplate> getTemplateById(@PathVariable Long id) {
        return templateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据代码获取模板
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<MailTemplate> getTemplateByCode(@PathVariable String code) {
        return templateService.getTemplateByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取默认模板
     */
    @GetMapping("/default")
    public ResponseEntity<MailTemplate> getDefaultTemplate() {
        return templateService.getDefaultTemplate()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建模板
     */
    @PostMapping
    public ResponseEntity<MailTemplate> createTemplate(@RequestBody MailTemplate template) {
        try {
            MailTemplate created = templateService.createTemplate(template);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public ResponseEntity<MailTemplate> updateTemplate(@PathVariable Long id,
                                                       @RequestBody MailTemplate template) {
        try {
            MailTemplate updated = templateService.updateTemplate(id, template);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量发送邮件
     */
    @PostMapping("/batch-send")
    public ResponseEntity<Map<String, Object>> batchSend(@RequestBody BatchSendRequest request) {
        try {
            Map<String, Object> result = templateService.sendBatchMail(
                    request.getRecipients(),
                    request.getTemplateId(),
                    request.getVariables()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 批量发送请求DTO
     */
    public static class BatchSendRequest {
        private List<String> recipients;
        private Long templateId;
        private Map<String, String> variables;

        public List<String> getRecipients() { return recipients; }
        public void setRecipients(List<String> recipients) { this.recipients = recipients; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Map<String, String> getVariables() { return variables; }
        public void setVariables(Map<String, String> variables) { this.variables = variables; }
    }
}
