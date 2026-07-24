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

    // ===== F02 创建并冻结 Agent (ADR-002 v4.1) =====
    // 30006..30016 占用；HTTP status 由 GlobalExceptionHandler#determineHttpStatus 维护。
    AGENT_VERSION_FROZEN("30006", "AgentVersion 已冻结,不可变更"),
    AGENT_VERSION_DELETE_FORBIDDEN("30007", "AgentVersion 不可删除,使用 deprecate"),
    AGENT_VERSION_NOT_FOUND("30008", "AgentVersion 未找到"),
    RUN_VERSION_RETIRED("30009", "目标 AgentVersion 已退役"),
    IDEMPOTENCY_KEY_CONFLICT("30010", "Idempotency-Key 与历史请求不一致"),
    SNAPSHOT_SCHEMA_INCOMPATIBLE("30011", "快照 schema 与实现不兼容"),
    SNAPSHOT_CANONICALIZE_FAILED("30012", "快照规范化失败"),
    SECRET_REFERENCE_NOT_FOUND("30013", "引用的 Secret 不存在或不可用"),
    RUNNER_LOCAL_SECRET_MISSING("30014", "MVP 不支持 RUNNER_LOCAL 引用"),
    MISSING_IDEMPOTENCY_KEY("30015", "freeze 请求必须带 Idempotency-Key"),
    AGENT_DRAFT_INVALID("30016", "草稿校验失败"),

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
    // Runner 鉴权 / 接入 (F01 接入并监控服务器)
    // ============================================
    // 错误码语义遵循 ADR-001 §D-1.7：401 与 403 必须区分"凭据无效"和"凭据有效但 Runner 已被撤销"。
    RUNNER_CREDENTIAL_INVALID("70006", "Runner Credential 无效"),
    RUNNER_REVOKED("70007", "Runner 凭据已被撤销"),
    RUNNER_OFFLINE("70008", "Runner 当前不接收新请求"),
    RUNNER_NOT_ENROLLED("70009", "Runner 未完成接入"),
    ENROLLMENT_TOKEN_INVALID("70010", "Enrollment Token 无效或已使用"),
    ENROLLMENT_TOKEN_EXPIRED("70011", "Enrollment Token 已过期"),
    RUNNER_NOT_FOUND("70012", "Runner 不存在"),

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
    VALIDATION_OUT_OF_RANGE("90004", "数值超出范围"),

    // ============================================
    // 限流 / 配额 (10xxxx)
    // ============================================
    // 区别于 10005 TOO_MANY_REQUESTS (通用入口限流), 这里是细分的限流语义
    RATE_LIMIT_EXCEEDED("100001", "请求频率超限"),
    API_KEY_QUOTA_EXCEEDED("100002", "API Key 配额超限"),
    API_KEY_RATE_LIMITED("100003", "API Key 调用频率超限"),
    CONCURRENCY_LIMIT_EXCEEDED("100004", "并发数超限"),

    // ============================================
    // 任务 / 队列 (11xxxx)
    // ============================================
    TASK_NOT_FOUND("110001", "任务未找到"),
    TASK_EXECUTION_FAILED("110002", "任务执行失败"),
    TASK_TIMEOUT("110003", "任务执行超时"),
    TASK_DEAD_LETTER("110004", "任务进入死信队列"),
    TASK_CANCELLED("110005", "任务已取消"),
    TASK_RETRY_EXHAUSTED("110006", "任务重试次数耗尽"),

    // ============================================
    // 协作 / 子任务 (12xxxx)
    // ============================================
    COLLABORATION_NOT_FOUND("120001", "协作包未找到"),
    COLLABORATION_PACKAGE_DECOMPOSE_FAILED("120002", "协作包分解失败"),
    COLLABORATION_PACKAGE_INVALID_STATE("120003", "协作包状态非法"),
    SUBTASK_NOT_FOUND("120004", "子任务未找到"),
    SUBTASK_EXECUTION_FAILED("120005", "子任务执行失败"),
    SUBTASK_INVALID_TRANSITION("120006", "子任务状态流转非法"),

    // ============================================
    // 服务可用性 (13xxxx) - Gateway MVP 专用
    // ============================================
    SERVICE_UNAVAILABLE("130001", "服务暂不可用"),

    // ============================================
    // Agent Run 执行记录 (14xxxx) - F03
    // ============================================
    RUN_NOT_FOUND("140001", "Run 未找到"),
    RUN_INVALID_STATE("140002", "Run 状态非法"),
    RUN_ALREADY_TERMINAL("140003", "Run 已终结，不可修改"),
    RUN_NO_AVAILABLE_RUNNER("140004", "无可用的 Runner"),
    RUN_LEASE_EXPIRED("140005", "Run lease 已过期"),
    RUN_RETRY_EXHAUSTED("140006", "重试次数耗尽"),
    RUN_VERSION_NOT_FROZEN("140007", "AgentVersion 未冻结，不可执行"),
    RUN_LEASE_NOT_FOUND("140008", "Lease 不存在"),
    RUN_RESULT_CONFLICT("140009", "幂等键冲突：同一 key 不同 payload"),
    RUN_ASSIGNMENT_NOT_FOUND("140010", "Assignment 不存在"),
    RUN_FEATURE_NOT_AVAILABLE("140011", "功能尚未实现"),

    // ============================================
    // Agent Endpoint 发布 (15xxxx) - F05
    // ============================================
    ENDPOINT_NOT_FOUND("150001", "Endpoint 未找到"),
    ENDPOINT_PATH_CONFLICT("150002", "Endpoint 路径已被占用"),
    ENDPOINT_VERSION_NOT_FROZEN("150003", "AgentVersion 未冻结，不可发布");

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
