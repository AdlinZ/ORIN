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
