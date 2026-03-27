from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field
from enum import Enum

class NodeStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"

class WorkflowStatus(str, Enum):
    SUCCESS = "success"
    PARTIAL = "partial" # Some nodes failed, but workflow finished
    ERROR = "error"     # Engine execution error

# DSL v2 协作节点类型
class CollaborationNodeType(str, Enum):
    PLANNER = "planner"
    DELEGATE = "delegate"
    PARALLEL_FORK = "parallel_fork"
    CONSENSUS = "consensus"
    CRITIC = "critic"
    MEMORY_READ = "memory_read"
    MEMORY_WRITE = "memory_write"
    EVENT_EMIT = "event_emit"
    EVENT_LISTEN = "event_listen"
    RETRY_POLICY = "retry_policy"

class NodeExecutionOutput(BaseModel):
    outputs: Dict[str, Any]
    selected_handle: Optional[str] = None

class NodeData(BaseModel):
    label: Optional[str] = None
    prompt: Optional[str] = None
    model: Optional[str] = None
    timeout: Optional[float] = None # Node-level timeout in seconds
    temperature: Optional[float] = None
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    
    model_config = {"extra": "allow"}

class Node(BaseModel):
    id: str
    type: str 
    data: Optional[Dict[str, Any]] = Field(default_factory=dict)
    position: Optional[Dict[str, float]] = None 

class Edge(BaseModel):
    id: str
    source: str
    target: str
    sourceHandle: Optional[str] = None
    targetHandle: Optional[str] = None

class WorkflowDSL(BaseModel):
    nodes: List[Node]
    edges: List[Edge]
    viewport: Optional[Dict[str, float]] = None
    version: Optional[str] = "1.0"  # DSL protocol version for compatibility

class WorkflowContext(BaseModel):
    inputs: Dict[str, Any] = Field(default_factory=dict)
    model_config = {"extra": "allow"}

class RunWorkflowRequest(BaseModel):
    dsl: WorkflowDSL
    context: Optional[WorkflowContext] = Field(default_factory=WorkflowContext)

class NodeTrace(BaseModel):
    node_id: str
    status: NodeStatus
    start_time: float
    end_time: float
    duration: float
    outputs: Optional[Dict[str, Any]] = None
    selected_handle: Optional[str] = None
    error: Optional[str] = None

class ExecutionResult(BaseModel):
    status: WorkflowStatus
    outputs: Dict[str, Any] = Field(default_factory=dict) # NodeID -> Output
    trace: List[NodeTrace] = Field(default_factory=list)
    error: Optional[str] = None # Global error message if status is ERROR
    trace_summary: Optional[Dict[str, Any]] = None # Tracing summary for distributed tracing


# ============================================================================
# Unified Collaboration Data Structures (C1.1)
# ============================================================================

class TaskStatus(str, Enum):
    """Task execution status"""
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    RETRYING = "retrying"
    ROLLED_BACK = "rolled_back"


class RetryStrategy(str, Enum):
    """Retry backoff strategies"""
    FIXED = "fixed"
    LINEAR = "linear"
    EXPONENTIAL = "exponential"
    EXPONENTIAL_CAPPED = "exponential_capped"


class TaskType(str, Enum):
    """Task type classification"""
    PLANNING = "planning"           # Task decomposition
    EXECUTION = "execution"         # Single agent execution
    PARALLEL = "parallel"           # Parallel execution
    CRITIQUE = "critique"           # Review/critique
    CONSENSUS = "consensus"         # Multi-agent consensus
    ROLLBACK = "rollback"           # Rollback task


class Task(BaseModel):
    """
    Unified Task definition for multi-agent collaboration.
    Represents the top-level unit of collaborative work.
    """
    id: str                                    # Unique task identifier
    type: TaskType                              # Task type classification
    description: str                            # Human-readable task description
    parent_id: Optional[str] = None            # Parent task ID for hierarchical tasks

    # Execution configuration
    timeout: Optional[float] = 300.0            # Task timeout in seconds
    max_retries: int = 3                       # Maximum retry attempts
    retry_strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_CAPPED
    retry_delay: float = 1.0                    # Initial retry delay in seconds
    retry_multiplier: float = 2.0              # Backoff multiplier

    # Dependencies
    depends_on: List[str] = Field(default_factory=list)  # Task IDs this depends on
    required_roles: List[str] = Field(default_factory=list)  # Required agent roles

    # Context
    input_schema: Optional[Dict[str, Any]] = None   # Expected input structure
    output_schema: Optional[Dict[str, Any]] = None  # Expected output structure

    # Metadata
    priority: int = 0                          # Higher = more priority
    metadata: Dict[str, Any] = Field(default_factory=dict)

    class Config:
        json_schema_extra = {
            "example": {
                "id": "task_001",
                "type": "execution",
                "description": "Analyze user query and generate response",
                "timeout": 60.0,
                "max_retries": 3,
                "retry_strategy": "exponential_capped",
                "depends_on": [],
                "required_roles": ["analyst"]
            }
        }


class Subtask(BaseModel):
    """
    Subtask within a collaborative task.
    Represents a unit of work assigned to a specific agent.
    """
    id: str                                    # Unique subtask identifier
    task_id: str                               # Parent task ID
    sequence: int                              # Execution sequence number

    # Subtask definition
    description: str                           # What this subtask does
    assigned_role: str                         # Role assigned to this subtask
    agent_id: Optional[str] = None             # Specific agent ID (if pre-assigned)

    # Execution parameters
    input_data: Dict[str, Any] = Field(default_factory=dict)  # Input for this subtask
    prompt_template: Optional[str] = None      # Optional prompt template

    # Checkpoint for rollback
    checkpoint_id: Optional[str] = None        # Checkpoint to restore on failure

    # Result placeholder
    result: Optional['SubtaskResult'] = None  # Populated after execution

    # Status
    status: TaskStatus = TaskStatus.PENDING
    started_at: Optional[float] = None
    completed_at: Optional[float] = None

    class Config:
        json_schema_extra = {
            "example": {
                "id": "subtask_001_1",
                "task_id": "task_001",
                "sequence": 1,
                "description": "Collect user information",
                "assigned_role": "collector",
                "input_data": {"query": "{{inputs.query}}"}
            }
        }


class SubtaskResult(BaseModel):
    """
    Result of a subtask execution.
    """
    subtask_id: str
    status: TaskStatus
    output: Optional[Any] = None               # Subtask output
    error: Optional[str] = None                # Error message if failed

    # Execution metadata
    execution_time: float = 0.0                 # Time spent in seconds
    retries_used: int = 0                       # Number of retries used
    trace_id: Optional[str] = None             # Associated trace ID

    # Checkpoint
    checkpoint_snapshot: Optional[Dict[str, Any]] = None  # State snapshot for rollback


class SharedMemory(BaseModel):
    """
    Shared memory interface for multi-agent collaboration.
    Provides structured access to shared state across agents.
    """
    memory_id: str                             # Unique memory identifier
    memory_type: str                            # Type: short_term, long_term, working

    # Content
    key: str                                   # Memory key
    value: Any                                 # Memory value
    content_schema: Optional[Dict[str, Any]] = None  # Expected content structure

    # Access control
    owner_task_id: Optional[str] = None        # Task that owns this memory
    readable_by: List[str] = Field(default_factory=list)  # Roles/agents that can read
    writable_by: List[str] = Field(default_factory=list)  # Roles/agents that can write

    # Lifecycle
    ttl: Optional[float] = None                # Time to live in seconds
    created_at: Optional[float] = None
    updated_at: Optional[float] = None
    expires_at: Optional[float] = None

    # Versioning
    version: int = 1                           # Optimistic locking version

    class Config:
        json_schema_extra = {
            "example": {
                "memory_id": "mem_001",
                "memory_type": "working",
                "key": "user_context",
                "value": {"name": "John", "preferences": {}},
                "readable_by": ["analyst", "executor"],
                "writable_by": ["collector"]
            }
        }


class CollaborationResult(BaseModel):
    """
    Unified result structure for collaboration operations.
    Wraps the outcome of any collaborative task execution.
    """
    # Result identification
    collaboration_id: str                      # Unique collaboration session ID
    task_id: str                               # Associated task ID

    # Status
    status: TaskStatus
    success: bool                              # Shortcut for status == SUCCESS

    # Results
    primary_result: Optional[Any] = None        # Main result output
    subtask_results: List[SubtaskResult] = Field(default_factory=list)

    # Summary statistics
    total_subtasks: int = 0
    successful_subtasks: int = 0
    failed_subtasks: int = 0

    # Timing
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    total_duration: float = 0.0                # Total execution time in seconds

    # Error handling
    errors: List[str] = Field(default_factory=list)  # All errors encountered
    fatal_error: Optional[str] = None          # Error that caused complete failure

    # Memory effects
    memory_writes: List['SharedMemory'] = Field(default_factory=list)  # Memory modifications

    # Rollback information
    rolled_back: bool = False
    rollback_reason: Optional[str] = None

    class Config:
        json_schema_extra = {
            "example": {
                "collaboration_id": "collab_001",
                "task_id": "task_001",
                "status": "success",
                "success": True,
                "primary_result": {"response": "Analysis complete"},
                "total_subtasks": 3,
                "successful_subtasks": 3,
                "failed_subtasks": 0
            }
        }


class RetryPolicy(BaseModel):
    """
    Retry policy configuration for collaboration tasks.
    """
    policy_id: str                             # Unique policy identifier
    max_retries: int = 3                       # Maximum retry attempts
    strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_CAPPED

    # Timing
    initial_delay: float = 1.0                # Initial delay in seconds
    max_delay: float = 60.0                    # Maximum delay cap
    multiplier: float = 2.0                    # Backoff multiplier

    # Jitter (randomization to avoid thundering herd)
    enable_jitter: bool = True
    jitter_factor: float = 0.1                 # Jitter as fraction of delay

    # Conditions
    retryable_errors: List[str] = Field(default_factory=list)  # Errors that trigger retry
    non_retryable_errors: List[str] = Field(default_factory=list)  # Errors that skip retry

    # Budget (shared across related tasks)
    shared_budget: bool = False                # Whether retries are shared across branches
    budget_key: Optional[str] = None           # Key for shared budget tracking

    def calculate_delay(self, attempt: int) -> float:
        """Calculate delay for given attempt number"""
        if self.strategy == RetryStrategy.FIXED:
            delay = self.initial_delay
        elif self.strategy == RetryStrategy.LINEAR:
            delay = self.initial_delay * (attempt + 1)
        elif self.strategy == RetryStrategy.EXPONENTIAL:
            delay = self.initial_delay * (self.multiplier ** attempt)
        elif self.strategy == RetryStrategy.EXPONENTIAL_CAPPED:
            delay = min(self.initial_delay * (self.multiplier ** attempt), self.max_delay)
        else:
            delay = self.initial_delay

        if self.enable_jitter:
            import random
            jitter = delay * self.jitter_factor
            delay = delay + random.uniform(-jitter, jitter)

        return max(0, delay)

    class Config:
        json_schema_extra = {
            "example": {
                "policy_id": "retry_default",
                "max_retries": 3,
                "strategy": "exponential_capped",
                "initial_delay": 1.0,
                "max_delay": 60.0,
                "multiplier": 2.0,
                "enable_jitter": True,
                "jitter_factor": 0.1
            }
        }


class RollbackAction(BaseModel):
    """
    Rollback action definition for collaboration rollback mechanism.
    """
    action_id: str                             # Unique action identifier
    task_id: str                               # Task this rollback belongs to
    action_type: str                           # Type: restore_checkpoint, compensate, notify

    # Target
    target_memory_key: Optional[str] = None    # Memory key to restore
    target_checkpoint_id: Optional[str] = None # Checkpoint to restore
    target_agent_id: Optional[str] = None     # Agent to notify

    # Compensation data
    compensation_data: Optional[Dict[str, Any]] = None  # Data for compensation action

    # Status
    executed: bool = False
    executed_at: Optional[float] = None
    result: Optional[Any] = None


class Checkpoint(BaseModel):
    """
    Checkpoint for saving and restoring collaboration state.
    Enables rollback and fault recovery.
    """
    checkpoint_id: str                         # Unique checkpoint identifier
    task_id: str                               # Associated task
    subtask_id: Optional[str] = None           # Associated subtask (if any)

    # Snapshot
    context_snapshot: Dict[str, Any] = Field(default_factory=dict)  # Full context state
    memory_snapshot: List['SharedMemory'] = Field(default_factory=list)  # Memory state
    output_snapshot: Optional[Dict[str, Any]] = None  # Node output at checkpoint

    # Metadata
    created_at: float                          # Timestamp
    description: Optional[str] = None          # Human-readable description
    tags: List[str] = Field(default_factory=list)


class CollaborationContext(BaseModel):
    """
    Unified context for collaboration execution.
    Passed through the entire collaboration workflow.
    """
    # Identity
    collaboration_id: str                       # Unique collaboration session ID
    trace_id: Optional[str] = None             # Distributed trace ID
    root_task_id: Optional[str] = None         # Root task for nested collaborations

    # Tasks
    current_task: Optional[Task] = None        # Currently executing task
    task_queue: List[Task] = Field(default_factory=list)  # Pending tasks

    # Subtasks
    subtasks: Dict[str, Subtask] = Field(default_factory=dict)  # All subtasks by ID

    # Shared memory
    shared_memory: Dict[str, SharedMemory] = Field(default_factory=dict)  # Key -> Memory

    # Rollback support
    checkpoints: Dict[str, Checkpoint] = Field(default_factory=dict)  # ID -> Checkpoint
    rollback_stack: List[RollbackAction] = Field(default_factory=list)

    # Retry tracking
    retry_budgets: Dict[str, int] = Field(default_factory=dict)  # Task ID -> remaining retries

    # Result aggregation
    results: Dict[str, CollaborationResult] = Field(default_factory=dict)  # Collaboration ID -> Result

    # Configuration
    config: Dict[str, Any] = Field(default_factory=dict)

    # Timing
    started_at: Optional[float] = None
    last_updated_at: Optional[float] = None
