# ORIN MCP Bridge

Local stdio bridge for connecting Claude Desktop to ORIN's Streamable HTTP MCP endpoint.

## Prerequisites

Before starting ORIN Backend for Claude Desktop, allow the bridge Origin:

```bash
export ORIN_MCP_ALLOWED_ORIGINS=http://localhost:8080,http://127.0.0.1:8080
```

Restart ORIN Backend after changing this value. The bridge sends `Origin` derived from `ORIN_BASE_URL` by default, so `ORIN_BASE_URL=http://localhost:8080` sends `Origin: http://localhost:8080`.

## Install Locally

```bash
cd /absolute/path/to/ORIN/orin-mcp-bridge
python3 -m venv .venv
.venv/bin/pip install -e .
```

The only direct dependency is `mcp==1.27.1`.

## Required Environment

```bash
export ORIN_BASE_URL=http://localhost:8080
export ORIN_API_KEY=<ORIN_API_KEY>
# Optional override when ORIN Backend allows a different Origin.
export ORIN_MCP_ORIGIN=http://localhost:8080
```

## Run

```bash
/absolute/path/to/ORIN/orin-mcp-bridge/.venv/bin/python -m orin_mcp_bridge
```

The process speaks MCP over stdin/stdout. Logs go to stderr so they do not corrupt the protocol stream.

## Claude Desktop

Use the venv Python as the `command` and `-m orin_mcp_bridge` as `args`. Example:

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

Restart Claude Desktop after editing its config.

## Troubleshooting

- `403` or `Origin not allowed`: ensure ORIN Backend was started with `ORIN_MCP_ALLOWED_ORIGINS` containing the same origin as `ORIN_BASE_URL`.
- `ORIN_API_KEY is required`: set a valid ORIN gateway API key in the Claude Desktop config.
- No tools appear: confirm the API key user owns at least one Agent with `mcpExposed=true`.

