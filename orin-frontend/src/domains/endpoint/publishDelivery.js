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

export function resolvePublicOrigin(browserOrigin, configuredOrigin = '') {
  const configured = String(configuredOrigin || '').trim().replace(/\/$/, '')
  if (configured) return configured

  try {
    const url = new URL(browserOrigin)
    const isLocalDevelopment = ['localhost', '127.0.0.1'].includes(url.hostname)
      && ['5173', '4174'].includes(url.port)
    if (isLocalDevelopment) {
      url.port = '8080'
      return url.origin
    }
    return url.origin
  } catch (_) {
    return String(browserOrigin || '').replace(/\/$/, '')
  }
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
