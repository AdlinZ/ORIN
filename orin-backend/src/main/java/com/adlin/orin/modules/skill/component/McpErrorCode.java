package com.adlin.orin.modules.skill.component;

/**
 * MCP 工具调用失败的分类错误码，用于 ToolTrace 与日志中快速定位失败原因。
 */
public enum McpErrorCode {

    /** MCP 服务 id 不存在。 */
    MCP_NOT_FOUND,
    /** MCP 服务存在但被禁用。 */
    MCP_DISABLED,
    /** MCP 服务未处于 CONNECTED 状态。 */
    MCP_NOT_CONNECTED,
    /** 模型调用参数非法（缺少 toolName 等）。 */
    MCP_BAD_REQUEST,
    /** 调用 AI Engine 超时或网络不可达。 */
    MCP_TIMEOUT,
    /** AI Engine 返回非 2xx 响应。 */
    MCP_HTTP_ERROR,
    /** 其它未归类的调用失败。 */
    MCP_CALL_FAILED
}
