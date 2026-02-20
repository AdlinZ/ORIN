import asyncio
import sys
import io
import traceback
from typing import Any, Dict
from app.models.workflow import Node
from app.engine.handlers.base import BaseNodeHandler

class CodeNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Executes arbitrary Python code.
        The code can access variables from 'context' (inputs and previous node outputs).
        To return values, the code should set values in a 'output' dictionary.
        """
        code = node.data.get("code", "")
        if not code:
            return {"result": "No code provided", "status": "skipped"}

        # Prepare execution environment
        # We copy context to avoid accidental corruption of the main engine context
        local_scope = {**context}
        
        # Pre-define an output dict for the user to use
        if "output" not in local_scope:
            local_scope["output"] = {}
        
        # Capture stdout
        stdout_capture = io.StringIO()
        original_stdout = sys.stdout
        sys.stdout = stdout_capture
        
        try:
            # Execute the code
            # Note: This is NOT a secure sandbox. In production, use restricted environments.
            exec(code, {"__builtins__": __builtins__}, local_scope)
            
            # Flush stdout
            sys.stdout = original_stdout
            logs = stdout_capture.getvalue()
            
            # Collect results
            # Priority: 
            # 1. The 'output' dictionary if it was modified
            # 2. Any new variables introduced in the local scope (optional, let's stick to 'output' for now)
            
            result = local_scope.get("output", {})
            if not isinstance(result, dict):
                result = {"result": result}
            
            if logs:
                result["logs"] = logs.strip()
                
            return result
            
        except Exception as e:
            sys.stdout = original_stdout
            error_trace = traceback.format_exc()
            raise RuntimeError(f"Python Execution Error: {str(e)}\n{error_trace}")
        finally:
            if sys.stdout != original_stdout:
                sys.stdout = original_stdout
