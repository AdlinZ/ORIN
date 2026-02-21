import asyncio
from typing import Any, Dict
from openai import AsyncOpenAI, APITimeoutError, APIConnectionError, RateLimitError
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.core.config import settings

class RealLLMNodeHandler(BaseNodeHandler):
    def __init__(self):
        self._client = None

    @property
    def client(self):
        if self._client is None:
            if not settings.OPENAI_API_KEY:
                raise ValueError("OPENAI_API_KEY is not configured in environment or .env file.")
            self._client = AsyncOpenAI(
                api_key=settings.OPENAI_API_KEY,
                base_url=settings.OPENAI_BASE_URL
            )
        return self._client

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Executes LLM call using OpenAI compatible SDK.
        """
        # 1. Credentials setup
        node_api_key = node.data.get("api_key")
        node_base_url = node.data.get("base_url")

        effective_api_key = node_api_key or settings.OPENAI_API_KEY
        effective_base_url = node_base_url or settings.OPENAI_BASE_URL

        if not effective_api_key:
             raise ValueError("API Key is missing (neither in node data nor in engine environment).")

        # Use temporary client if node provides custom credentials, else use cached global client
        if node_api_key or node_base_url:
            run_client = AsyncOpenAI(
                api_key=effective_api_key,
                base_url=effective_base_url
            )
            should_close = True
        else:
            run_client = self.client
            should_close = False

        # 2. Resolve Prompt
        # Support multiple formats: 
        # 1. flat data.prompt
        # 2. nested data.config.prompt (Dify export style)
        # 3. data.prompt_template list (Dify UI style)
        prompt_template = node.data.get("prompt")
        
        if not prompt_template and "config" in node.data:
            prompt_template = node.data.get("config", {}).get("prompt")
            
        if not prompt_template and isinstance(node.data.get("prompt_template"), list):
            pt = node.data.get("prompt_template")
            # Prefer system role text
            sys_item = next((item for item in pt if item.get("role") == "system"), None)
            if sys_item:
                prompt_template = sys_item.get("text")
            elif pt and "text" in pt[0]:
                prompt_template = pt[0].get("text")
            
        if not prompt_template:
            # Fallback for display/debugging
            log_id = node.id
            raise ValueError(f"Node {log_id}: Prompt is missing in node configuration. (Data keys: {list(node.data.keys())})")
        
        try:
            # Create a full resolution map
            # 1. Start with inputs
            res_map = {}
            if "inputs" in context:
                res_map.update(context["inputs"])
            
            # 2. Add outputs from previous nodes (namespaced by node_id)
            for nid, out in context.items():
                if nid != "inputs" and isinstance(out, dict):
                    res_map[nid] = out
                    # Also support flattening the immediate predecessor's output if needed?
                    # For now, let's keep it clean
            
            # 3. Simple regex/replace for variables like {{variable}} or {variable}
            import re
            prompt = prompt_template
            
            # Support {query} and {{query}}
            pattern = re.compile(r'\{+([a-zA-Z0-9_.\-]+)\}+')
            
            def replace_var(match):
                var_path = match.group(1)
                # Simple case: direct match in res_map
                if var_path in res_map:
                    return str(res_map[var_path])
                # Nested case: node_id.output_key
                if "." in var_path:
                    parts = var_path.split(".")
                    curr = res_map
                    for p in parts:
                        if isinstance(curr, dict) and p in curr:
                            curr = curr[p]
                        else:
                            return match.group(0) # Keep original if not found
                    return str(curr)
                return match.group(0) # Keep original
                
            prompt = pattern.sub(replace_var, prompt)
            
        except Exception as e:
            raise ValueError(f"Prompt template resolution failed: {str(e)}")

        # Handle nested model structure from frontend
        model_data = node.data.get("model", "gpt-3.5-turbo")
        if isinstance(model_data, dict):
            model = model_data.get("name") or "gpt-3.5-turbo"
        else:
            model = str(model_data)
        
        # 3. Call LLM
        try:
             response = await run_client.chat.completions.create(
                 model=model,
                 messages=[
                     {"role": "user", "content": prompt}
                 ],
                 temperature=node.data.get("temperature", 0.7)
             )
             
             content = response.choices[0].message.content
             usage = response.usage
             
             return NodeExecutionOutput(outputs={
                 "text": content,
                 "model": model,
                 "tokens_used": usage.total_tokens if usage else 0
             })

        except RateLimitError:
            raise RuntimeError("LLM Rate Limit Exceeded")
        except APIConnectionError:
            raise RuntimeError("LLM Connection Failed")
        except Exception as e:
             raise RuntimeError(f"LLM Provider Error: {str(e)}")
        finally:
            if should_close:
                await run_client.close()
