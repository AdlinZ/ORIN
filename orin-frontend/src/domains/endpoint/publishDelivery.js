/**
 * Build the one-time handoff material shown immediately after an Endpoint is
 * published. Keep this logic pure so the UI cannot accidentally turn an API
 * relative path into an unusable external command.
 */
export function buildPublicUrl(origin, externalUrl) {
  if (!externalUrl) return ''
  if (/^https?:\/\//i.test(externalUrl)) return externalUrl
  return `${String(origin || '').replace(/\/$/, '')}${externalUrl}`
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\"'\"'")}'`
}

export function buildCurlCommand(externalUrl, secretKey) {
  return [
    `curl --request POST ${shellQuote(externalUrl)}`,
    `  --header ${shellQuote(`Authorization: Bearer ${secretKey}`)}`,
    `  --header 'Content-Type: application/json'`,
    "  --data '{\"input\":\"你好\"}'"
  ].join(' \\\n')
}

export function buildMcpConfig(origin, secretKey) {
  return JSON.stringify({
    mcpServers: {
      orin: {
        url: `${String(origin || '').replace(/\/$/, '')}/v1/mcp`,
        headers: { Authorization: `Bearer ${secretKey}` }
      }
    }
  }, null, 2)
}
