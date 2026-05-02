---
name: GrepSearch
description: Search for text in files.
type: SHELL
version: 1.0.0
---

# GrepSearch

## Description

Search for text in files.

## Type

SHELL

## Shell Configuration

```bash
grep -R --line-number --color=never "${pattern}" "${path}"
```

## Input Schema

```json
{"path": "string", "pattern": "string"}
```

## Output Schema

```json
{"stderr": "string", "stdout": "string", "exitCode": "number"}
```

## Usage

This skill can be invoked through the Skill-Hub API or integrated into workflows.
