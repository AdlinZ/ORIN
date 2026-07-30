# ORIN MCP Bridge

Local stdio bridge for connecting Claude Desktop to ORIN's Streamable HTTP MCP endpoint.

## Prerequisites

The stdio bridge is a server-to-server client and does not send an `Origin`
header by default, so normal Claude Desktop use does not require a CORS change.
Set `ORIN_MCP_ORIGIN` only when a reverse proxy or an explicit browser-like
Origin policy requires it; then add that exact value to `CORS_ALLOWED_ORIGINS`
before restarting the backend.

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
# Optional browser-like Origin for a proxy that explicitly requires it.
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

- `403` or `Origin not allowed`: only relevant when `ORIN_MCP_ORIGIN` is set;
  ensure `CORS_ALLOWED_ORIGINS` contains that exact Origin.
- `ORIN_API_KEY is required`: set a valid ORIN gateway API key in the Claude Desktop config.
- No tools appear: confirm the `CLIENT_ACCESS` API Key is assigned to at least
  one active published Endpoint. Workflow tools remain visible only when the
  key owner has a published workflow with `mcpExposed=true`.
