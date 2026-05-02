---
name: ReadFile
description: Read file content.
type: SHELL
version: 1.0.0
---

# ReadFile

## Description

Read file content.

## Type

SHELL

## Shell Configuration

```bash
cat ${path}
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
