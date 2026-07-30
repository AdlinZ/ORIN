package com.adlin.orin.common.exception;

import lombok.Getter;

/**
 * 错误代码枚举
 * 定义系统中所有可能的错误代码和对应的错误消息
 */
@Getter
public enum ErrorCode {

    // ============================================
    // 通用错误 (1xxxx)
    // ============================================
    SUCCESS("00000", "操作成功"),
    SYSTEM_ERROR("10000", "系统内部错误"),
    INVALID_PARAMETER("10001", "参数验证失败"),
    OPERATION_FAILED("10002", "操作失败"),
    UNAUTHORIZED("10003", "未授权访问"),
    FORBIDDEN("10004", "禁止访问"),
    TOO_MANY_REQUESTS("10005", "请求过于频繁"),

    // ============================================
    // 资源相关错误 (2xxxx)
    // ============================================
    RESOURCE_NOT_FOUND("20001", "资源未找到"),
    RESOURCE_ALREADY_EXISTS("20002", "资源已存在"),
    RESOURCE_CONFLICT("20003", "资源冲突"),
    RESOURCE_LOCKED("20004", "资源已锁定"),

    // ============================================
    // Agent相关错误 (3xxxx)
    // ============================================
    AGENT_NOT_FOUND("30001", "智能体未找到"),
    AGENT_ALREADY_EXISTS("30002", "智能体已存在"),
    AGENT_CONNECTION_FAILED("30003", "智能体连接失败"),
    AGENT_ONBOARD_FAILED("30004", "智能体接入失败"),
    AGENT_PROVIDER_UNSUPPORTED("30005", "不支持的智能体提供商"),

    // ============================================
    // Knowledge相关错误 (4xxxx)
    // ============================================
    KNOWLEDGE_NOT_FOUND("40001", "知识库未找到"),
    KNOWLEDGE_SYNC_FAILED("40002", "知识库同步失败"),
    DOCUMENT_NOT_FOUND("40003", "文档未找到"),
    DOCUMENT_UPLOAD_FAILED("40004", "文档上传失败"),
    VECTORIZATION_FAILED("40005", "向量化失败"),

    // ============================================
    // Model相关错误 (5xxxx)
    // ============================================
    MODEL_NOT_FOUND("50001", "模型未找到"),
    MODEL_CONFIG_INVALID("50002", "模型配置无效"),
    MODEL_API_ERROR("50003", "模型API调用失败"),

    // ============================================
    // Workflow相关错误 (6xxxx)
    // ============================================
    WORKFLOW_NOT_FOUND("60001", "工作流未找到"),
    WORKFLOW_EXECUTION_FAILED("60002", "工作流执行失败"),
    WORKFLOW_STEP_FAILED("60003", "工作流步骤执行失败"),
    WORKFLOW_INVALID_CONFIG("60004", "工作流配置无效"),

    // ============================================
    // Authentication & Authorization (7xxxx)
    // ============================================
    AUTH_INVALID_CREDENTIALS("70001", "用户名或密码错误"),
    AUTH_TOKEN_EXPIRED("70002", "令牌已过期"),
    AUTH_TOKEN_INVALID("70003", "令牌无效"),
    AUTH_INSUFFICIENT_PERMISSIONS("70004", "权限不足"),
    AUTH_API_KEY_INVALID("70005", "API密钥无效"),

    // ============================================
    // External Service错误 (8xxxx)
    // ============================================
    DIFY_API_ERROR("80001", "Dify API调用失败"),
    DIFY_CONNECTION_TIMEOUT("80002", "Dify连接超时"),
    REDIS_CONNECTION_ERROR("80003", "Redis连接失败"),
    DATABASE_ERROR("80004", "数据库操作失败"),
    MILVUS_ERROR("80005", "Milvus向量数据库错误"),

    // ============================================
    // Validation错误 (9xxxx)
    // ============================================
    VALIDATION_ERROR("90001", "数据验证失败"),
    VALIDATION_REQUIRED_FIELD("90002", "必填字段缺失"),
    VALIDATION_INVALID_FORMAT("90003", "格式不正确"),
    VALIDATION_OUT_OF_RANGE("90004", "数值超出范围");

    /**
     * 错误代码
     */
    private final String code;

    /**
     * 错误消息
     */
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据错误代码获取ErrorCode枚举
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
