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
    error: Optional[str] = None

class ExecutionResult(BaseModel):
    status: WorkflowStatus
    outputs: Dict[str, Any] = Field(default_factory=dict) # NodeID -> Output
    trace: List[NodeTrace] = Field(default_factory=list)
    error: Optional[str] = None # Global error message if status is ERROR
