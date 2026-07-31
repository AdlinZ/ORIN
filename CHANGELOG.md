# Changelog

All notable changes to ORIN will be documented in this file.

This project follows the spirit of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses Semantic Versioning during the `v0.x` phase.

## [Unreleased - v0.2.0]

The repository `VERSION` file and the current `main` branch are the source of truth for this product line. The existing `v0.2.0-alpha.1` and `v0.2.0-submission.*` tags are historical prereleases; no final `v0.2.0` release has been tagged yet.

### Current baseline

- Agent management, provider routing, API key governance, MCP exposure, workflow management, knowledge management, collaboration management, trace views, and role-based navigation remain in the product baseline.
- Backend, frontend, AI Engine, and MCP bridge now report the same `0.2.0` version.
- CI verifies that component versions remain aligned with the root `VERSION` file.

### Fixed

- Bypassed macOS/system HTTP proxies for loopback MCP bridge targets so the documented local ORIN bridge path does not fail with a proxy-generated 502; remote targets continue to honor the environment proxy.

### Removed from the restored baseline

- Removed the standalone Runner enrollment and machine-management subsystem.
- Removed the Agent draft/freeze/immutable-version implementation introduced after the submission prerelease.
- Removed backup/restore scripts, audit export, the coverage gate, and the unfinished extended OTel trace implementation.
- Removed several unclosed Java workflow node handlers. Complex workflow node support must be treated as conditional until it has an end-to-end acceptance test.

### Verification

- Clean backend test: 559 tests passed.
- Frontend unit test: 130 tests passed; production build passed.
- Frontend browser smoke: 13 tests passed. These tests do not replace real-provider or real-backend business acceptance.
- AI Engine: 86 tests passed.
- MCP bridge: 6 tests passed.

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
