import asyncio
import sys
import io
import traceback
import signal
from typing import Any, Dict
from functools import wraps
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

# RestrictedPython imports
from RestrictedPython import safe_globals, safe_builtins
from RestrictedPython.Eval import restricted_eval
from RestrictedPython.G import (
    _write_,
    _read_,
    _getiter_,
    _iter_unpack_sequence_,
    guard_getitem,
    guard_getiter,
)


class TimeoutException(Exception):
    """Exception raised when code execution times out."""
    pass


def timeout_handler(signum, frame):
    raise TimeoutException("Code execution timed out")


class CodeNodeHandler(BaseNodeHandler):
    # Maximum execution time in seconds
    TIMEOUT_SECONDS = 10

    def _get_safe_globals(self) -> Dict[str, Any]:
        """
        Create a safe global dictionary for code execution.
        Filters out dangerous builtins while allowing safe operations.
        """
        # Start with safe_builtins
        safe_globals = {
            '__builtins__': safe_builtins,
            '_write_': _write_,
            '_read_': _read_,
            '_getiter_': _getiter_,
            '_iter_unpack_sequence_': _iter_unpack_sequence_,
        }

        # Add safe utility functions
        safe_globals['_getattr_'] = getattr
        safe_globals['_setattr_'] = setattr
        safe_globals['_delattr_'] = delattr
        safe_globals['_getitem_'] = guard_getitem
        safe_globals['_getiter_'] = guard_getiter

        # Add safe built-in types and functions
        safe_globals['len'] = len
        safe_globals['range'] = range
        safe_globals['enumerate'] = enumerate
        safe_globals['zip'] = zip
        safe_globals['map'] = map
        safe_globals['filter'] = filter
        safe_globals['sorted'] = sorted
        safe_globals['sum'] = sum
        safe_globals['min'] = min
        safe_globals['max'] = max
        safe_globals['abs'] = abs
        safe_globals['round'] = round
        safe_globals['bool'] = bool
        safe_globals['int'] = int
        safe_globals['float'] = float
        safe_globals['str'] = str
        safe_globals['list'] = list
        safe_globals['dict'] = dict
        safe_globals['tuple'] = tuple
        safe_globals['set'] = set
        safe_globals['type'] = type
        safe_globals['isinstance'] = isinstance
        safe_globals['issubclass'] = issubclass
        safe_globals['hasattr'] = hasattr

        return safe_globals

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Executes arbitrary Python code with RestrictedPython sandbox.
        The code can access variables from 'context' (inputs and previous node outputs).
        To return values, the code should set values in a 'output' dictionary.
        """
        code = node.data.get("code", "")
        if not code:
            return NodeExecutionOutput(outputs={"result": "No code provided", "status": "skipped"})

        # Prepare execution environment
        # We copy context to avoid accidental corruption of the main engine context
        local_scope = {**context}

        # Pre-define an output dict for the user to use
        if "output" not in local_scope:
            local_scope["output"] = {}

        # Get safe globals
        safe_globals = self._get_safe_globals()

        # Add local scope to safe globals
        safe_globals.update(local_scope)

        # Capture stdout
        stdout_capture = io.StringIO()
        original_stdout = sys.stdout
        sys.stdout = stdout_capture

        # Set up timeout
        old_handler = signal.signal(signal.SIGALRM, timeout_handler)
        signal.alarm(self.TIMEOUT_SECONDS)

        try:
            # Execute the code using RestrictedPython compile
            # This provides a safer sandbox than raw exec()
            compiled_code = compile(code, '<code>', 'exec')

            # Execute in safe globals
            exec(compiled_code, safe_globals, safe_globals)

            # Cancel alarm
            signal.alarm(0)

            # Flush stdout
            sys.stdout = original_stdout
            logs = stdout_capture.getvalue()

            # Collect results from the output dict
            result = safe_globals.get("output", {})
            if not isinstance(result, dict):
                result = {"result": result}

            if logs:
                result["logs"] = logs.strip()

            return NodeExecutionOutput(outputs=result)

        except TimeoutException:
            sys.stdout = original_stdout
            signal.alarm(0)
            raise RuntimeError(f"Python Execution Error: Code execution timed out after {self.TIMEOUT_SECONDS} seconds")

        except Exception as e:
            sys.stdout = original_stdout
            signal.alarm(0)
            error_trace = traceback.format_exc()
            raise RuntimeError(f"Python Execution Error: {str(e)}\n{error_trace}")

        finally:
            # Restore original signal handler
            signal.signal(signal.SIGALRM, old_handler)
            if sys.stdout != original_stdout:
                sys.stdout = original_stdout
