---
name: Knowledge_Search
description: 检索知识库并返回相关片段（待绑定 knowledgeConfigId）。
type: KNOWLEDGE
version: 1.0.0
---

# Knowledge_Search

## Description

检索知识库并返回相关片段（待绑定 knowledgeConfigId）。

## Type

KNOWLEDGE

## Input Schema

```json
{"topK": "number", "query": "string"}
```

## Output Schema

```json
{"hits": "array"}
```

## Usage

This skill can be invoked through the Skill-Hub API or integrated into workflows.
