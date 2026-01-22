package com.adlin.orin.modules.trace.interceptor;

import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能追踪拦截器
 * 使用 AOP 拦截技能执行,自动记录追踪信息
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SkillTraceInterceptor {

    private final TraceService traceService;

    /**
     * 拦截技能执行方法
     * 注意: 这个切面需要与实际的技能执行方法配合使用
     */
    @Around("execution(* com.adlin.orin.modules.skill.service.SkillService.executeSkill(..))")
    public Object traceSkillExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        if (args.length < 2) {
            return joinPoint.proceed();
        }

        Long skillId = (Long) args[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) args[1];

        // 从 ThreadLocal 或上下文获取追踪信息
        String traceId = TraceContext.getTraceId();
        Long instanceId = TraceContext.getInstanceId();
        Long stepId = TraceContext.getStepId();
        String stepName = TraceContext.getStepName();

        // 如果没有追踪上下文,直接执行
        if (traceId == null) {
            log.debug("No trace context found, executing without tracing");
            return joinPoint.proceed();
        }

        // 开始追踪
        WorkflowTraceEntity trace = traceService.startTrace(
                traceId,
                instanceId,
                stepId,
                stepName,
                skillId,
                "Skill-" + skillId,
                inputs);

        try {
            // 执行技能
            Object result = joinPoint.proceed();

            // 记录成功
            @SuppressWarnings("unchecked")
            Map<String, Object> outputData = (Map<String, Object>) result;
            traceService.completeTrace(trace.getId(), outputData);

            return result;

        } catch (Throwable e) {
            // 记录失败
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("exception", e.getClass().getName());
            errorDetails.put("stackTrace", getStackTraceString(e));

            traceService.failTrace(
                    trace.getId(),
                    "SKILL_EXECUTION_ERROR",
                    e.getMessage(),
                    errorDetails);

            throw e;
        }
    }

    /**
     * 获取异常堆栈字符串
     */
    private String getStackTraceString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 1000)
                break; // 限制长度
        }
        return sb.toString();
    }

    /**
     * 追踪上下文 (使用 ThreadLocal)
     */
    public static class TraceContext {
        private static final ThreadLocal<String> traceId = new ThreadLocal<>();
        private static final ThreadLocal<Long> instanceId = new ThreadLocal<>();
        private static final ThreadLocal<Long> stepId = new ThreadLocal<>();
        private static final ThreadLocal<String> stepName = new ThreadLocal<>();

        public static void setTraceId(String id) {
            traceId.set(id);
        }

        public static String getTraceId() {
            return traceId.get();
        }

        public static void setInstanceId(Long id) {
            instanceId.set(id);
        }

        public static Long getInstanceId() {
            return instanceId.get();
        }

        public static void setStepId(Long id) {
            stepId.set(id);
        }

        public static Long getStepId() {
            return stepId.get();
        }

        public static void setStepName(String name) {
            stepName.set(name);
        }

        public static String getStepName() {
            return stepName.get();
        }

        public static void clear() {
            traceId.remove();
            instanceId.remove();
            stepId.remove();
            stepName.remove();
        }
    }
}
