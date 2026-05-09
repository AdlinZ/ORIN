from fastapi import APIRouter, HTTPException, BackgroundTasks, Header
from app.models.workflow import RunWorkflowRequest, ExecutionResult, WorkflowContext
from app.engine.executor import GraphExecutor
import uuid

router = APIRouter()
executor = GraphExecutor()

@router.post("/run", response_model=ExecutionResult)
async def run_workflow(
    request: RunWorkflowRequest,
    x_trace_id: str = Header(None, alias="X-Trace-Id"),
    x_orin_authorization: str = Header(None, alias="X-ORIN-Authorization")
):
    """
    Execute a workflow DSL.
    """
    # Use provided trace_id or generate a new one
    trace_id = x_trace_id or str(uuid.uuid4())

    context = request.context
    if context is None:
        context = WorkflowContext()

    try:
        # For now, we run synchronously (waiting for result)
        # In production, this might be offloaded to background tasks if long-running
        runtime_context = {}
        if x_orin_authorization:
            runtime_context["_authorization"] = x_orin_authorization
        result = await executor.execute(request.dsl, context.inputs, trace_id, runtime_context)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
