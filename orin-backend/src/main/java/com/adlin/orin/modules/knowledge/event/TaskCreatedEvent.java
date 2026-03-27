package com.adlin.orin.modules.knowledge.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskCreatedEvent extends ApplicationEvent {
    private final String taskId;
    private final String traceId;

    public TaskCreatedEvent(Object source, String taskId, String traceId) {
        super(source);
        this.taskId = taskId;
        this.traceId = traceId;
    }
}
