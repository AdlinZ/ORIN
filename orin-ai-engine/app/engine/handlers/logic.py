import re
from typing import Any, Dict, List, Optional
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.llm import RealLLMNodeHandler

class IfElseNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Evaluates conditions and returns either 'if' or 'else' handle.
        """
        # conditions: list of {variable, operator, value}
        conditions = node.data.get("conditions", [])
        logical_operator = node.data.get("logical_operator", "and") # and / or
        
        def evaluate_condition(cond):
            var_path = cond.get("variable")
            op = cond.get("operator")
            expected = cond.get("value")
            
            # Resolve variable value from context
            val = self._resolve_variable(var_path, context)
            
            if op == "contains":
                return str(expected) in str(val)
            elif op == "not_contains":
                return str(expected) not in str(val)
            elif op == "equals":
                return str(val) == str(expected)
            elif op == "not_equals":
                return str(val) != str(expected)
            elif op == "is_empty":
                return not val
            elif op == "is_not_empty":
                return bool(val)
            # Add more operators as needed
            return False

        results = [evaluate_condition(c) for c in conditions]
        
        is_true = False
        if logical_operator == "or":
            is_true = any(results) if results else False
        else:
            is_true = all(results) if results else True # Default true if no conditions? Or false? Dify usually requires one.

        selected = "if" if is_true else "else"
        
        return NodeExecutionOutput(
            outputs={"result": is_true, "selected_branch": selected},
            selected_handle=selected
        )

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        if not path: return None
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class QuestionClassifierNodeHandler(RealLLMNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Uses LLM to classify a question into defined categories.
        """
        query_var = node.data.get("query_variable", "inputs.query")
        classes = node.data.get("classes", []) # List of {id, name}
        
        if not classes:
            return NodeExecutionOutput(outputs={"error": "No classes defined"}, selected_handle=None)
            
        query_val = self._resolve_variable(query_var, context) or ""
        
        class_str = "\n".join([f"- {c['id']}: {c.get('name', c['id'])}" for c in classes])
        
        system_prompt = f"You are a question classifier. Categorize the user input into one of the following classes:\n{class_str}\n\nOnly output the class ID."
        
        # We override the run logic of RealLLMNodeHandler slightly or just call its client
        # For simplicity, let's just make a call
        try:
            response = await self.client.chat.completions.create(
                model=node.data.get("model", "gpt-3.5-turbo"),
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": str(query_val)}
                ],
                temperature=0
            )
            selected_id = response.choices[0].message.content.strip()
            
            # Validate selected_id
            valid_ids = [c["id"] for c in classes]
            if selected_id not in valid_ids:
                # Fallback to first if not found? Or None.
                selected_id = valid_ids[0] if valid_ids else None
                
            return NodeExecutionOutput(
                outputs={"class": selected_id},
                selected_handle=selected_id
            )
        except Exception as e:
            raise RuntimeError(f"Question Classifier Error: {str(e)}")

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class VariableAggregatorNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Aggregates multiple variables into one output.
        """
        variables = node.data.get("variables", []) # List of variable paths
        output_name = node.data.get("output_variable", "aggregated_value")
        
        # Pick the first non-null value? Or a list?
        # Aggregator usually picks the first one that is present in the context.
        result = None
        for path in variables:
            val = self._resolve_variable(path, context)
            if val is not None:
                result = val
                break
        
        return NodeExecutionOutput(outputs={output_name: result})

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class IterationNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Simple placeholder for now
        return NodeExecutionOutput(outputs={"status": "Iteration logic not yet implemented for parallel engine"})

class LoopNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Simple placeholder for now
        return NodeExecutionOutput(outputs={"status": "Loop logic not yet implemented for parallel engine"})
