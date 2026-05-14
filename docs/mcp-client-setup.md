# MCP Client Setup

ORIN exposes selected Agents and Workflows through the Streamable HTTP MCP endpoint:

```text
POST <ORIN_BASE_URL>/v1/mcp
Authorization: Bearer <ORIN_API_KEY>
```

Only Agents and Workflows owned by the API key user and marked `mcpExposed=true` are listed as MCP tools. The `/v1/mcp` endpoint uses ORIN client access API keys (`CLIENT_ACCESS`) for authentication; other `/v1/*` gateway routes keep their existing gateway authentication path.

Tool names are prefixed by resource type:

- `agent.<base64url-agent-id>`
- `workflow.<workflow-id>`

## Cursor

```json
{
  "mcpServers": {
    "orin": {
      "url": "<ORIN_BASE_URL>/v1/mcp",
      "headers": {
        "Authorization": "Bearer <ORIN_API_KEY>"
      }
    }
  }
}
```

## Windsurf

```json
{
  "mcpServers": {
    "orin": {
      "serverUrl": "<ORIN_BASE_URL>/v1/mcp",
      "headers": {
        "Authorization": "Bearer <ORIN_API_KEY>"
      }
    }
  }
}
```

Cursor and Windsurf support Streamable HTTP in current releases; exact compatibility depends on the installed client version. Claude Desktop uses the stdio bridge below.

## Claude Desktop

Claude Desktop starts local MCP servers through `claude_desktop_config.json`, so ORIN uses the local `orin-mcp-bridge` stdio bridge to forward requests to `/v1/mcp`.

Before starting ORIN Backend, allow the bridge Origin and restart the backend:

```bash
export ORIN_MCP_ALLOWED_ORIGINS=http://localhost:8080,http://127.0.0.1:8080
```

Install the bridge locally:

```bash
cd <ORIN_REPO>/orin-mcp-bridge
python3 -m venv .venv
.venv/bin/pip install -e .
```

If the bridge reports `403` or `Origin not allowed`, check that `ORIN_MCP_ALLOWED_ORIGINS` contains the origin derived from `ORIN_BASE_URL`.

Bridge environment:

```bash
export ORIN_BASE_URL=http://localhost:8080
export ORIN_API_KEY=<ORIN_API_KEY>
export ORIN_MCP_ORIGIN=http://localhost:8080
```

### macOS

Edit:

```text
~/Library/Application Support/Claude/claude_desktop_config.json
```

```json
{
  "mcpServers": {
    "orin": {
      "command": "/absolute/path/to/ORIN/orin-mcp-bridge/.venv/bin/python",
      "args": ["-m", "orin_mcp_bridge"],
      "env": {
        "ORIN_BASE_URL": "http://localhost:8080",
        "ORIN_API_KEY": "<ORIN_API_KEY>"
      }
    }
  }
}
```

### Windows

Edit:

```text
%APPDATA%\\Claude\\claude_desktop_config.json
```

```json
{
  "mcpServers": {
    "orin": {
      "command": "C:\\absolute\\path\\to\\ORIN\\orin-mcp-bridge\\.venv\\Scripts\\python.exe",
      "args": ["-m", "orin_mcp_bridge"],
      "env": {
        "ORIN_BASE_URL": "http://localhost:8080",
        "ORIN_API_KEY": "<ORIN_API_KEY>"
      }
    }
  }
}
```

Restart Claude Desktop after changing the config.

## Agent Call Example

After enabling "MCP 暴露" on an Agent owned by the API key user, MCP clients can call it by its generated `agent.<base64url-agent-id>` tool name.

```json
{
  "name": "agent.<base64url-agent-id>",
  "arguments": {
    "message": "Summarize the current ORIN MCP status",
    "context": "Answer concisely.",
    "max_tokens": 512
  }
}
```

Agent calls run through ORIN's existing collaboration execution path. They are not executed by a separate MCP-specific runtime.

## Workflow Call Example

After enabling "MCP 暴露" on a published workflow, MCP clients can call it by its `workflow.<id>` tool name.

```json
{
  "name": "workflow.42",
  "arguments": {
    "query": "Summarize this week's MCP progress"
  }
}
```

Workflow calls are submitted asynchronously through ORIN's existing workflow execution queue. The MCP response returns submission metadata such as `taskId`, `workflowInstanceId`, `traceId`, and `statusUrl`.

## Smoke Checklist

1. Create or reuse a `CLIENT_ACCESS` API key for the owning user.
2. Mark at least one owned Agent or Workflow as `mcpExposed=true`.
3. POST `initialize` and `tools/list` to `<ORIN_BASE_URL>/v1/mcp` with `Authorization: Bearer <ORIN_API_KEY>`.
4. For Claude Desktop, run `orin-mcp-bridge` over stdio and confirm the same tools are listed.
5. Call one Agent tool and confirm the response content is returned without logging the API key.
