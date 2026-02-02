package com.adlin.orin.modules.knowledge.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskCreatedEvent extends ApplicationEvent {
    private final String taskId;

    public TaskCreatedEvent(Object source, String taskId) {
        super(source);
        this.taskId = taskId;
    }
}
