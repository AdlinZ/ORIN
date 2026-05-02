---
name: CheckProcesses
description: List top running processes.
type: SHELL
version: 1.0.0
---

# CheckProcesses

## Description

List top running processes.

## Type

SHELL

## Shell Configuration

```bash
ps aux | head -n 20
```

## Input Schema

```json
{}
```

## Output Schema

```json
{"stderr": "string", "stdout": "string", "exitCode": "number"}
```

## Usage

This skill can be invoked through the Skill-Hub API or integrated into workflows.
