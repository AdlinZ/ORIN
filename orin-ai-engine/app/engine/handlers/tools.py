import httpx
import re
from typing import Any, Dict, List, Optional
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

class HTTPRequestNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Executes an HTTP request (GET, POST, PUT, DELETE, etc.)
        """
        url = node.data.get("url", "")
        method = node.data.get("method", "GET").upper()
        headers = node.data.get("headers", {})
        params = node.data.get("params", {})
        body_type = node.data.get("body_type", "none") # none, json, formData, raw
        body_content = node.data.get("body_content", "")
        
        if not url:
            raise ValueError("HTTP Request Node: URL is missing")

        # 1. Resolve Variables in URL, Params, Headers, and Body
        url = self._resolve_template(url, context)
        
        resolved_params = {}
        for k, v in params.items():
            resolved_params[k] = self._resolve_template(str(v), context)
            
        resolved_headers = {}
        for k, v in headers.items():
            resolved_headers[k] = self._resolve_template(str(v), context)

        # 2. Prepare Body
        data = None
        json_data = None
        if body_type == "json":
            # If body_content is a string that looks like JSON template
            if isinstance(body_content, str):
                resolved_body = self._resolve_template(body_content, context)
                try:
                    import json
                    json_data = json.loads(resolved_body)
                except:
                    json_data = {"error": "Failed to parse resolved JSON body", "content": resolved_body}
            else:
                json_data = body_content # Assume already a dict
        elif body_type == "raw":
            data = self._resolve_template(body_content, context)

        # 3. Execute Request
        async with httpx.AsyncClient(follow_redirects=True) as client:
            try:
                response = await client.request(
                    method=method,
                    url=url,
                    headers=resolved_headers,
                    params=resolved_params,
                    content=data,
                    json=json_data,
                    timeout=node.data.get("timeout", 30.0)
                )
                
                # Attempt to parse as JSON if possible
                try:
                    resp_json = response.json()
                except:
                    resp_json = None
                    
                return NodeExecutionOutput(outputs={
                    "status_code": response.status_code,
                    "body": response.text,
                    "json": resp_json,
                    "headers": dict(response.headers),
                    "is_success": response.is_success
                })
            except Exception as e:
                raise RuntimeError(f"HTTP Request Node Error ({url}): {str(e)}")

    def _resolve_template(self, template: str, context: Dict[str, Any]) -> str:
        if not template or not isinstance(template, str):
            return template
            
        # Support both {{var}} and {var}
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
            
        return pattern.sub(replace_var, template)

class ListOperatorNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Performs operations on lists (limit, sort, reverse).
        """
        list_path = node.data.get("list_variable")
        operation = node.data.get("operation", "limit")
        
        if not list_path:
            return NodeExecutionOutput(outputs={"error": "List variable path is required"})
            
        target_list = self._resolve_variable(list_path, context)
        
        if not isinstance(target_list, list):
            return NodeExecutionOutput(outputs={"error": f"Value at {list_path} is not a list. Type: {type(target_list).__name__}"})
            
        result = target_list.copy()
        
        if operation == "limit":
            count = int(node.data.get("limit_count", 10))
            result = result[:count]
        elif operation == "reverse":
            result = result[::-1]
        elif operation == "sort":
            result = sorted(result) # Basic sort
            
        return NodeExecutionOutput(outputs={"result": result, "count": len(result)})

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class ToolNodeHandler(BaseNodeHandler):
    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Placeholder for external tools
        tool_name = node.data.get("tool_name", "unknown")
        return NodeExecutionOutput(outputs={"status": f"Tool '{tool_name}' execution simulated"})
