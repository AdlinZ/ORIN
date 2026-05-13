# Changelog

All notable changes to ORIN will be documented in this file.

This project follows the spirit of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses Semantic Versioning during the `v0.x` phase.

## [v0.2.0-alpha.1] - 2026-05-14

### Added

- Closed the collaboration human-intervention loop with skip, manual-complete, and retry actions.
- Added package-level FALLBACK max-attempt protection for collaboration execution.
- Added workflow loop node support in the backend DSL normalizer.
- Added a `limit` query parameter to the knowledge graph `/visualization` API.
- Added real knowledge graph visualization in the frontend graph detail page.
- Added two-layer graph search in the frontend: local canvas highlight plus Enter-triggered full-graph API search.

### Fixed

- Fixed collaboration `skip` and `manual-complete` so downstream subtasks are scheduled instead of leaving the package stalled.
- Fixed workflow subtask execution from AI Engine to backend by reusing contextual `_authorization` with `ORIN_BACKEND_AUTHORIZATION` fallback.
- Fixed the backend collaboration result listener so it consumes the result queue and advances subtask state.
- Fixed `branch_result` payload loss caused by listener/status double writes.
- Fixed the `future.complete` race where async completion could overwrite full result payloads with simplified payloads.
- Fixed workflow `loop` while/until execution so body outputs are written back before the next condition evaluation.
- Fixed `parallel_fork` error handling so branch exceptions are collected instead of being swallowed as plain strings.
- Fixed knowledge graph build stats so `entity_count` and `relation_count` are not overwritten by stale JPA entity instances.
- Fixed knowledge graph entity search and detail fallback paths to preserve `graphId` isolation across graphs.

### Changed

- Unified the if-else workflow node contract around the AI Engine `conditions` semantics and removed the old Java placeholder fallback.
- Changed knowledge graph MySQL visualization fallback node ids from synthetic `node_0` values to real `GraphEntity.id` values.
- Documented the local knowledge graph smoke checklist, including valid knowledge-base payload shape and document parse/build readiness checks.

### Removed

- Removed the unused legacy `GraphVisualization.vue` component.

### Deferred

- Deferred workflow `switch` node support until a real multi-branch routing use case appears.
- Deferred workflow collaboration-subprocess nodes until a real workflow-to-collaboration orchestration use case appears.
- Deferred unified error handling from Phase 1 to Phase 2 cross-cutting governance, alongside logging, Trace, and error tracking.

### Tech Debt

- `GraphExtractionService` should move from direct `SiliconFlowEmbeddingAdapter.chat()` calls to the unified LLM gateway.
- Knowledge graph relation extraction output rate needs evaluation against relation-heavy documents.
