package com.adlin.orin.modules.run.controller;

import com.adlin.orin.modules.run.dto.AssignmentResponse;
import com.adlin.orin.modules.run.dto.CreateRunRequest;
import com.adlin.orin.modules.run.dto.RunEventResponse;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.entity.RunLog;
import com.adlin.orin.modules.run.service.RunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Run 业务 API（F03 + F04）。
 *
 * <p>Base: /api/v1/runs（JWT 鉴权）。
 */
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    /** 创建 Run：选择已冻结 AgentVersion + 可用 Runner。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse createRun(@Valid @RequestBody CreateRunRequest request,
                                  Authentication auth) {
        return runService.createRun(request, auth.getName());
    }

    /** Run 列表（分页，按创建时间倒序；F04：支持按状态/Agent/Runner 筛选）。 */
    @GetMapping
    public Page<RunResponse> listRuns(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String runnerId) {
        if (status != null || agentId != null || runnerId != null) {
            return runService.listRuns(status, agentId, runnerId, pageable);
        }
        return runService.listRuns(pageable);
    }

    /** Run 详情。 */
    @GetMapping("/{runId}")
    public RunResponse getRun(@PathVariable String runId) {
        return runService.getRun(runId);
    }

    /** 取消 Run。 */
    @PostMapping("/{runId}/cancel")
    public RunResponse cancelRun(@PathVariable String runId, Authentication auth) {
        return runService.cancelRun(runId, auth.getName());
    }

    /** 重试 Run。 */
    @PostMapping("/{runId}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse retryRun(@PathVariable String runId, Authentication auth) {
        return runService.retryRun(runId, auth.getName());
    }

    /** F04：拉取 Run 日志（增量：afterSeq 之后的新行）。 */
    @GetMapping("/{runId}/logs")
    public List<RunLog> getRunLogs(@PathVariable String runId,
                                    @RequestParam(required = false) Integer afterSeq) {
        return runService.getLogs(runId, afterSeq);
    }

    /** F04：获取 Run 事件时间线（增量：afterSeq 之后的新事件）。 */
    @GetMapping("/{runId}/events")
    public List<RunEventResponse> getRunEvents(@PathVariable String runId,
                                                @RequestParam(required = false) Integer afterSeq) {
        return runService.getEvents(runId, afterSeq);
    }

    /** F04：获取 Run 分配历史（run_assignment 行）。 */
    @GetMapping("/{runId}/assignments")
    public List<AssignmentResponse> getRunAssignments(@PathVariable String runId) {
        return runService.getAssignments(runId);
    }
}
