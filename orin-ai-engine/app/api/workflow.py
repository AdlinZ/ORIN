from fastapi import APIRouter, HTTPException, BackgroundTasks
from app.models.workflow import RunWorkflowRequest, ExecutionResult, WorkflowContext
from app.engine.executor import GraphExecutor

router = APIRouter()
executor = GraphExecutor()

@router.post("/run", response_model=ExecutionResult)
async def run_workflow(request: RunWorkflowRequest):
    """
    Execute a workflow DSL.
    """
    context = request.context
    if context is None:
        context = WorkflowContext()
        
    try:
        # For now, we run synchronously (waiting for result)
        # In production, this might be offloaded to background tasks if long-running
        result = await executor.execute(request.dsl, context.inputs)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
