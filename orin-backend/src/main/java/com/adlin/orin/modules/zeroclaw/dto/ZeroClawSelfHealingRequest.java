package com.adlin.orin.modules.zeroclaw.dto;

/**
 * ZeroClaw 主动维护操作请求 DTO
 */
public class ZeroClawSelfHealingRequest {

    /**
     * 操作类型：CLEAR_LOGS, RESTART_PROCESS, CLEANUP_CACHE, SCALE_RESOURCE
     */
    private String actionType;

    /**
     * 目标资源标识
     */
    private String targetResource;

    /**
     * 执行原因
     */
    private String reason;

    /**
     * 是否强制立即执行（跳过确认）
     */
    private Boolean forceExecute = false;

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Boolean getForceExecute() { return forceExecute; }
    public void setForceExecute(Boolean forceExecute) { this.forceExecute = forceExecute; }
}
