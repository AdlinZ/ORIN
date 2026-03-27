"""
Collaboration Models - Unified data structures for multi-agent collaboration.

This module defines the core data structures for C1.1:
- Task: Top-level collaborative work unit
- Subtask: Unit of work assigned to a specific agent
- CollaborationContext: Execution context passed through workflows
- SharedMemory: Structured shared state access
- CollaborationResult: Execution outcome wrapper
- RetryPolicy: Retry configuration
- RollbackAction: Rollback mechanism
- Checkpoint: State snapshot for fault recovery

Version: 1.0
"""

from app.models.workflow import (
    Task, TaskType, TaskStatus,
    Subtask, SubtaskResult,
    SharedMemory,
    CollaborationResult,
    RetryPolicy, RetryStrategy,
    RollbackAction,
    Checkpoint,
    CollaborationContext,
)

__all__ = [
    # Enums
    "TaskStatus",
    "RetryStrategy",
    "TaskType",

    # Core structures
    "Task",
    "Subtask",
    "SubtaskResult",

    # Memory
    "SharedMemory",

    # Result
    "CollaborationResult",

    # Retry & Rollback
    "RetryPolicy",
    "RollbackAction",
    "Checkpoint",

    # Context
    "CollaborationContext",
]