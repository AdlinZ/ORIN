package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.entity.CollaborationTask;
import com.adlin.orin.modules.collaboration.repository.CollaborationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationService {

    private final CollaborationTaskRepository taskRepository;

    /**
     * 创建协作任务
     */
    @Transactional
    public CollaborationTask createTask(String name, String description, String taskType, 
            List<String> agentIds, String createdBy) {
        
        CollaborationTask task = CollaborationTask.builder()
                .name(name)
                .description(description)
                .taskType(taskType)
                .agentIds(agentIds)
                .status("PENDING")
                .currentAgentIndex(0)
                .createdBy(createdBy)
                .build();
        
        return taskRepository.save(task);
    }

    /**
     * 获取用户的协作任务列表
     */
    public List<CollaborationTask> getTasksByUser(String createdBy) {
        return taskRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }

    /**
     * 获取所有协作任务列表
     */
    public List<CollaborationTask> getAllTasks() {
        return taskRepository.findAll();
    }

    /**
     * 获取任务详情
     */
    public Optional<CollaborationTask> getTask(Long id) {
        return taskRepository.findById(id);
    }

    /**
     * 更新任务状态
     */
    @Transactional
    public CollaborationTask updateTaskStatus(Long id, String status, String result, String errorMessage) {
        Optional<CollaborationTask> taskOpt = taskRepository.findById(id);
        
        if (taskOpt.isPresent()) {
            CollaborationTask task = taskOpt.get();
            task.setStatus(status);
            
            if (result != null) {
                task.setResult(result);
            }
            
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                task.setCompletedAt(LocalDateTime.now());
            }
            
            return taskRepository.save(task);
        }
        
        throw new RuntimeException("Task not found: " + id);
    }

    /**
     * 执行下一个 Agent
     */
    @Transactional
    public CollaborationTask executeNextAgent(Long id) {
        Optional<CollaborationTask> taskOpt = taskRepository.findById(id);
        
        if (taskOpt.isPresent()) {
            CollaborationTask task = taskOpt.get();
            
            if (!"RUNNING".equals(task.getStatus())) {
                task.setStatus("RUNNING");
            }
            
            List<String> agentIds = task.getAgentIds();
            int currentIndex = task.getCurrentAgentIndex();
            
            if (currentIndex < agentIds.size()) {
                String currentAgentId = agentIds.get(currentIndex);
                log.info("Executing agent {} for task {}", currentAgentId, id);
                
                // TODO: 实际调用 Agent 执行任务
                // 这里可以集成 Agent 服务来执行任务
                
                // 移动到下一个 Agent
                task.setCurrentAgentIndex(currentIndex + 1);
                
                // 如果所有 Agent 都执行完毕，标记为完成
                if (currentIndex + 1 >= agentIds.size()) {
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(LocalDateTime.now());
                }
                
                return taskRepository.save(task);
            }
            
            return task;
        }
        
        throw new RuntimeException("Task not found: " + id);
    }

    /**
     * 删除任务
     */
    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    /**
     * 获取正在运行的任务
     */
    public List<CollaborationTask> getRunningTasks() {
        return taskRepository.findByStatusOrderByCreatedAtDesc("RUNNING");
    }
}
