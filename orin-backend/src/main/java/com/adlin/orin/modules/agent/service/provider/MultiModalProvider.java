package com.adlin.orin.modules.agent.service.provider;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 多模态能力提供者接口
 * 用于统一不同服务商 (Dify, OpenAI, StabilityAI) 的交互逻辑
 */
public interface MultiModalProvider {

    /**
     * 获取 Provider 名称，如 "DIFY", "OPENAI"
     */
    String getProviderName();

    /**
     * 处理交互请求
     * 
     * @param meta    智能体元数据
     * @param request 交互请求详情
     * @return 交互结果
     */
    InteractionResult process(AgentMetadata meta, InteractionRequest request);

    /**
     * 异步任务状态查询
     */
    default InteractionResult checkStatus(String jobId) {
        return InteractionResult.error("Job status check not supported by this provider");
    }

    // ================== Data Classes ==================

    class InteractionRequest {
        private String type; // TEXT, AUDIO, IMAGE
        private String content; // Text prompts or JSON inputs
        private MultipartFile file; // Uploaded file
        private Map<String, Object> context; // Extra context (e.g. conversationId)

        // Getters and Setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public MultipartFile getFile() {
            return file;
        }

        public void setFile(MultipartFile file) {
            this.file = file;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }

        public InteractionRequest(String type, String content, MultipartFile file, Map<String, Object> context) {
            this.type = type;
            this.content = content;
            this.file = file;
            this.context = context;
        }
    }

    class InteractionResult {
        private String status; // SUCCESS, PROCESSING, FAILED
        private String dataType; // TEXT, IMAGE_URL, JSON
        private Object data; // Final result content
        private String jobId; // For async tasks
        private String errorMessage;

        // Constructors
        public InteractionResult(String status, String dataType, Object data) {
            this.status = status;
            this.dataType = dataType;
            this.data = data;
        }

        public static InteractionResult success(String dataType, Object data) {
            return new InteractionResult("SUCCESS", dataType, data);
        }

        public static InteractionResult processing(String jobId) {
            InteractionResult r = new InteractionResult("PROCESSING", null, null);
            r.setJobId(jobId);
            return r;
        }

        public static InteractionResult error(String message) {
            InteractionResult r = new InteractionResult("FAILED", null, null);
            r.setErrorMessage(message);
            return r;
        }

        // Getters and Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
