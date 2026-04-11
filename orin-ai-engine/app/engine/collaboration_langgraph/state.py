"""
协作状态定义 - LangGraph 协作图
"""
from typing import TypedDict, List, Dict, Any, Optional
from enum import Enum


class CollaborationStatus(str, Enum):
    """协作状态"""
    PLANNING = "PLANNING"
    DECOMPOSING = "DECOMPOSING"
    EXECUTING = "EXECUTING"
    CONSENSUS = "CONSENSUS"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    FALLBACK = "FALLBACK"


class CollaborationMode(str, Enum):
    """协作模式"""
    SEQUENTIAL = "SEQUENTIAL"
    PARALLEL = "PARALLEL"
    CONSENSUS = "CONSENSUS"
    HIERARCHICAL = "HIERARCHICAL"


class SubTask(TypedDict):
    """子任务"""
    id: str
    description: str
    role: str
    dependsOn: List[str]
    promptTemplate: str
    inputData: Dict[str, Any]
    status: str
    result: Optional[str]


class CollaborationState(TypedDict):
    """协作图状态"""
    # 基础信息
    package_id: str
    root_task_id: Optional[int]
    intent: str
    trace_id: Optional[str]
    
    # 任务分解
    sub_tasks: List[SubTask]
    completed_subtasks: List[str]
    current_task_index: int
    
    # 协作模式
    collaboration_mode: str
    
    # 共享上下文 (黑板)
    shared_context: Dict[str, Any]
    
    # 并行分支结果
    branch_results: Dict[str, Any]
    
    # 最终结果
    final_result: Optional[str]
    
    # 状态
    status: str
    
    # 错误信息
    error_message: Optional[str]
    
    # 检查点
    savepoint_id: Optional[str]
