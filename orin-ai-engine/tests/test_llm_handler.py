import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from app.models.workflow import Node
from app.engine.handlers.llm import RealLLMNodeHandler
from openai import RateLimitError, OpenAIError
import httpx

@pytest.mark.asyncio
async def test_llm_handler_success():
    # Mock OpenAI
    with patch("app.engine.handlers.llm.AsyncOpenAI") as MockClient:
        # Configuration
        with patch("app.engine.handlers.llm.settings") as mock_settings:
            mock_settings.OPENAI_API_KEY = "sk-test"
            
            # Setup Mock Response
            mock_instance = MockClient.return_value
            mock_response = MagicMock()
            mock_response.choices = [MagicMock(message=MagicMock(content="Mocked Answer"))]
            mock_response.usage = MagicMock(total_tokens=42)
            
            mock_instance.chat.completions.create = AsyncMock(return_value=mock_response)
            
            # Test Run
            handler = RealLLMNodeHandler()
            node = Node(id="1", type="llm", data={"prompt": "Hello {query}", "model": "gpt-4"})
            context = {"inputs": {"query": "World"}}
            
            result = await handler.run(node, context)
            
            # Verify
            assert result["text"] == "Mocked Answer"
            assert result["tokens_used"] == 42
            assert result["model"] == "gpt-4"
            
            # Verify Prompt Replacement
            call_args = mock_instance.chat.completions.create.call_args
            assert call_args[1]["messages"][0]["content"] == "Hello World"

@pytest.mark.asyncio
async def test_llm_handler_missing_key():
    # We must patch settings BEFORE handler init, because handler reads settings in __init__
    with patch("app.engine.handlers.llm.settings") as mock_settings:
        mock_settings.OPENAI_API_KEY = None
        
        # When API Key is missing, AsyncOpenAI might raise error on init OR
        # our custom check inside run() might raise it.
        # However, AsyncOpenAI(api_key=None) IS allowed by the SDK (it tries to find env var),
        # but if we passed explicit None from settings, it might be fine until call.
        # BUT our handler init:
        # self.client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)
        # If settings.OPENAI_API_KEY is None, calls to client will fail or init might fail.
        # Actually, AsyncOpenAI throws OpenAIError if no key found.
        
        # In our implementation run() explicitly checks:
        # if not settings.OPENAI_API_KEY: raise ValueError
        
        # So we can test that.
        # We also need to prevent AsyncOpenAI from exploding during init if that happens.
        with patch("app.engine.handlers.llm.AsyncOpenAI"):
             handler = RealLLMNodeHandler()
             node = Node(id="1", type="llm", data={"prompt": "hi"})
             
             with pytest.raises(ValueError, match="OPENAI_API_KEY"):
                 await handler.run(node, {})


@pytest.mark.asyncio
async def test_llm_handler_api_error():
    with patch("app.engine.handlers.llm.AsyncOpenAI") as MockClient:
        with patch("app.engine.handlers.llm.settings") as mock_settings:
            mock_settings.OPENAI_API_KEY = "sk-test"
            
            mock_instance = MockClient.return_value
            
            # RateLimitError requires a response object and request object in recent versions
            # We can mock it properly or allow generic exception bubbling.
            # Easiest is to create a fake response/request.
            mock_request = httpx.Request("POST", "http://test")
            mock_response = httpx.Response(429, request=mock_request)
            
            error = RateLimitError("Rate Limit", response=mock_response, body=None)
            
            mock_instance.chat.completions.create = AsyncMock(side_effect=error)
            
            handler = RealLLMNodeHandler()
            node = Node(id="1", type="llm", data={"prompt": "hi"})
            
            with pytest.raises(RuntimeError, match="Rate Limit Exceeded"):
                await handler.run(node, {})
