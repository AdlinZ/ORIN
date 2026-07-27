import { describe, expect, it } from 'vitest'
import {
  buildCurlCommand,
  buildMcpConfig,
  buildPublicUrl
} from '@/domains/endpoint/publishDelivery'

describe('Endpoint publish delivery helpers', () => {
  it('builds an absolute public REST URL from the published relative URL', () => {
    expect(buildPublicUrl('https://orin.example.com/', '/v1/endpoints/ep_01/run'))
      .toBe('https://orin.example.com/v1/endpoints/ep_01/run')
  })

  it('preserves an absolute URL returned by the API', () => {
    expect(buildPublicUrl('https://orin.example.com', 'https://api.example.com/run'))
      .toBe('https://api.example.com/run')
  })

  it('builds a copyable curl command with API key authentication', () => {
    const command = buildCurlCommand('https://orin.example.com/v1/endpoints/ep_01/run', 'sk-orin-test')

    expect(command).toContain("--request POST 'https://orin.example.com/v1/endpoints/ep_01/run'")
    expect(command).toContain("'Authorization: Bearer sk-orin-test'")
    expect(command).toContain('Content-Type: application/json')
  })

  it('builds MCP configuration for the existing public MCP endpoint', () => {
    expect(JSON.parse(buildMcpConfig('https://orin.example.com/', 'sk-orin-test'))).toEqual({
      mcpServers: {
        orin: {
          url: 'https://orin.example.com/v1/mcp',
          headers: { Authorization: 'Bearer sk-orin-test' }
        }
      }
    })
  })
})
