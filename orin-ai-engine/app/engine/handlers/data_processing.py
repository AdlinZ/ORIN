import re
import json
import httpx
from typing import Any, Dict, List, Optional, TYPE_CHECKING
from jinja2 import Template, Environment
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.llm import RealLLMNodeHandler
from app.core.config import settings

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class TemplateTransformNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

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
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

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
    """
    Knowledge Retrieval node that queries the ORIN backend knowledge base.
    Makes real API calls to the backend retrieval service.
    """
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Retrieves relevant documents from the knowledge base via the ORIN backend API.
        """
        query_var = node.data.get("query_variable", "inputs.query")
        knowledge_id = node.data.get("knowledge_id")
        top_k = node.data.get("top_k", 5)
        alpha = node.data.get("alpha", 0.7)  # Hybrid search weight for vector search

        query = self._resolve_variable(query_var, context) or ""

        if not query:
            return NodeExecutionOutput(outputs={
                "result": [],
                "text": "",
                "error": "Query is empty"
            })

        if not knowledge_id:
            return NodeExecutionOutput(outputs={
                "result": [],
                "text": "",
                "error": "knowledge_id is required for knowledge retrieval"
            })

        # Call the backend knowledge retrieval API
        backend_url = settings.ORIN_BACKEND_URL.rstrip("/")
        retrieve_url = f"{backend_url}/api/v1/knowledge/retrieve/test"

        try:
            async with httpx.AsyncClient(follow_redirects=True, timeout=30.0) as client:
                response = await client.post(
                    retrieve_url,
                    json={
                        "query": query,
                        "kbId": knowledge_id,
                        "topK": top_k,
                        "alpha": alpha
                    }
                )

                if response.status_code != 200:
                    raise RuntimeError(f"Knowledge retrieval API returned status {response.status_code}: {response.text}")

                result = response.json()

                # Parse the retrieval results
                # Backend returns {data: [...], records: [...]} or similar structure
                retrieved_docs = []
                if isinstance(result, dict):
                    # Try common response structures
                    data = result.get("data", result.get("records", result.get("result", [])))
                    if isinstance(data, list):
                        retrieved_docs = data
                    elif isinstance(result, list):
                        retrieved_docs = result
                elif isinstance(result, list):
                    retrieved_docs = result

                # Format results
                formatted_results = []
                for doc in retrieved_docs:
                    if isinstance(doc, dict):
                        content = doc.get("content", doc.get("text", doc.get("chunk_content", str(doc))))
                        score = doc.get("score", doc.get("similarity", 0.0))
                        doc_id = doc.get("id", doc.get("doc_id", ""))
                        formatted_results.append({
                            "content": content,
                            "score": float(score) if score else 0.0,
                            "doc_id": doc_id
                        })
                    else:
                        formatted_results.append({"content": str(doc), "score": 0.0, "doc_id": ""})

                text_output = "\n\n".join([r["content"] for r in formatted_results])

                return NodeExecutionOutput(outputs={
                    "result": formatted_results,
                    "text": text_output,
                    "count": len(formatted_results),
                    "query": query,
                    "knowledge_id": knowledge_id
                })

        except httpx.TimeoutException:
            raise RuntimeError(f"Knowledge retrieval timed out after 30 seconds")
        except httpx.RequestError as e:
            raise RuntimeError(f"Knowledge retrieval request failed: {str(e)}")

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
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

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
