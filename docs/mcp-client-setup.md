# MCP Client Setup

ORIN exposes selected Agents through the Streamable HTTP MCP endpoint:

```text
POST <ORIN_BASE_URL>/v1/mcp
Authorization: Bearer <ORIN_API_KEY>
```

Only Agents owned by the API key user and marked `mcpExposed=true` are listed as MCP tools.

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

Cursor and Windsurf support Streamable HTTP in current releases; exact compatibility depends on the installed client version. Claude Desktop stdio bridge is planned separately.

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
