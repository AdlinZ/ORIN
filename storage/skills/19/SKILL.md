---
name: ListFiles
description: List files in a directory.
type: SHELL
version: 1.0.0
---

# ListFiles

## Description

List files in a directory.

## Type

SHELL

## Shell Configuration

```bash
ls -la ${path}
```

## Input Schema

```json
{"path": "string"}
```

## Output Schema

```json
{"stderr": "string", "stdout": "string", "exitCode": "number"}
```

## Usage

This skill can be invoked through the Skill-Hub API or integrated into workflows.
