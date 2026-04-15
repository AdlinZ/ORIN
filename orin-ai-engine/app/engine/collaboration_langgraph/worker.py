"""
LangGraph worker compatibility shim.

The architecture now uses one MQ worker implementation (`app.engine.mq_worker`).
This module keeps legacy import paths stable while delegating to the unified worker.
"""

import logging

from app.engine.mq_worker import collab_mq_worker, start_worker, stop_worker

logger = logging.getLogger(__name__)


async def start_langgraph_worker():
    """Compatibility entrypoint: start the unified MQ worker."""
    logger.info("[LangGraph Worker] Delegating start to unified mq_worker")
    await start_worker()


async def stop_langgraph_worker():
    """Compatibility entrypoint: stop the unified MQ worker."""
    logger.info("[LangGraph Worker] Delegating stop to unified mq_worker")
    await stop_worker()


__all__ = [
    "collab_mq_worker",
    "start_langgraph_worker",
    "stop_langgraph_worker",
]
