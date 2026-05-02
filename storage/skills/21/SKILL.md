---
name: CheckDisk
description: Check disk usage.
type: SHELL
version: 1.0.0
---

# CheckDisk

## Description

Check disk usage.

## Type

SHELL

## Shell Configuration

```bash
df -h
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
