import re
from typing import Any, Dict
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

class AnswerNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Generates a final answer using a template and context variables.
        """
        answer_template = node.data.get("answer", "")
        
        if not answer_template:
            # Check for alternative keys if coming from different export formats
            answer_template = node.data.get("text", "")

        # Resolve variables like {{query}} or {{llm_node.text}}
        pattern = re.compile(r'\{+([a-zA-Z0-9_.\-]+)\}+')
        
        def replace_var(match):
            var_path = match.group(1)
            parts = var_path.split(".")
            curr = context
            for p in parts:
                if isinstance(curr, dict) and p in curr:
                    curr = curr[p]
                else:
                    return match.group(0)
            return str(curr)

        final_answer = pattern.sub(replace_var, answer_template)
        
        return NodeExecutionOutput(outputs={
            "answer": final_answer,
            "template": answer_template
        })
