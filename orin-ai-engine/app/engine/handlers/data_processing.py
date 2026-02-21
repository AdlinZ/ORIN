import re
import json
from typing import Any, Dict, List, Optional
from jinja2 import Template, Environment
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.llm import RealLLMNodeHandler

class TemplateTransformNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Transforms input variables using a Jinja2 template.
        """
        template_str = node.data.get("template", "")
        # Dify style often uses 'variables' to define what's available
        # But we'll just give it the whole context
        
        if not template_str:
            return NodeExecutionOutput(outputs={"result": "", "error": "No template provided"})

        try:
            # Prepare a safe environment or simple template
            env = Environment()
            jinja_template = env.from_string(template_str)
            
            # Context for Jinja2 should be a flat map of variables if possible
            # or the whole context
            result = jinja_template.render(**context)
            
            return NodeExecutionOutput(outputs={"result": result})
        except Exception as e:
            raise RuntimeError(f"Template Transform Error: {str(e)}")

class ParameterExtractorNodeHandler(RealLLMNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Extracts structured parameters from text using LLM.
        """
        input_text_var = node.data.get("input_variable", "inputs.query")
        parameters = node.data.get("parameters", []) # List of {name, type, description, required}
        
        input_text = self._resolve_variable(input_text_var, context) or ""
        
        if not parameters:
            return NodeExecutionOutput(outputs={"error": "No parameters defined for extraction"})

        # Build prompt for extraction
        param_desc = "\n".join([f"- {p['name']} ({p.get('type', 'string')}): {p.get('description', '')}" for p in parameters])
        
        system_prompt = (
            "You are a precise data extractor. Extract the following parameters from the user text and return ONLY a valid JSON object.\n"
            f"Parameters to extract:\n{param_desc}\n\n"
            "If a parameter is not found, use null. Do not include any explanations or markdown formatting other than the JSON itself."
        )
        
        try:
            response = await self.client.chat.completions.create(
                model=node.data.get("model", "gpt-3.5-turbo"),
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": str(input_text)}
                ],
                temperature=0
            )
            content = response.choices[0].message.content.strip()
            
            # Try to find JSON block if LLM included it
            if "```json" in content:
                content = content.split("```json")[1].split("```")[0].strip()
            elif "```" in content:
                content = content.split("```")[1].split("```")[0].strip()
                
            result = json.loads(content)
            return NodeExecutionOutput(outputs=result)
        except Exception as e:
            raise RuntimeError(f"Parameter Extractor Error: {str(e)}")

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class KnowledgeRetrievalNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Mock for knowledge retrieval. In a real system, this would query a vector store.
        """
        query_var = node.data.get("query_variable", "inputs.query")
        knowledge_id = node.data.get("knowledge_id")
        
        query = self._resolve_variable(query_var, context) or ""
        
        # Mocking RAG result
        mock_result = [
            {"content": f"Information related to '{query}' found in knowledge base {knowledge_id}.", "score": 0.95},
            {"content": "ORIN (Open Resource Intelligence Network) is an advanced AI agent platform.", "score": 0.88}
        ]
        
        return NodeExecutionOutput(outputs={
            "result": mock_result,
            "text": "\n\n".join([r["content"] for r in mock_result])
        })

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class DocumentExtractorNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Extracts content from document/file variables.
        """
        file_var = node.data.get("file_variable", "inputs.files")
        files = self._resolve_variable(file_var, context)
        
        if not files:
            return NodeExecutionOutput(outputs={"text": "", "count": 0})
            
        if not isinstance(files, list):
            files = [files]
            
        # Simplified extraction logic
        ext_text = []
        for f in files:
            if isinstance(f, dict):
                ext_text.append(f.get("content", f.get("name", "Unknown File")))
            else:
                ext_text.append(str(f))
                
        return NodeExecutionOutput(outputs={
            "text": "\n---\n".join(ext_text),
            "count": len(ext_text)
        })

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr
