---
name: ZeroClaw_Analyze
description: 分析系统模块健康状态与异常信号。
type: API
version: 1.0.0
---

# ZeroClaw_Analyze

## Description

分析系统模块健康状态与异常信号。

## Type

API

## API Configuration

- **Endpoint**: http://localhost:8080/api/v1/monitor/analyze
- **Method**: POST

## Input Schema

```json
{"module": "string", "windowMinutes": "number"}
```

## Output Schema

```json
{"data": "object", "success": "boolean"}
```

## Usage

This skill can be invoked through the Skill-Hub API or integrated into workflows.
