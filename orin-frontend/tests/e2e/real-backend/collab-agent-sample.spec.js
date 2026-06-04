import { expect, test } from '@playwright/test'

/**
 * ORIN Phase 1D: Real Agent / MCP subtask sample (opt-in).
 *
 * This spec is NOT part of the default Phase 1D real-backend run.
 * Enable by setting ORIN_PHASE1D_RUN_AGENT=1 and providing ORIN_PHASE1D_AGENT_ID.
 *
 * Run:
 *   ORIN_PHASE1D_RUN_AGENT=1 ORIN_PHASE1D_AGENT_ID=<agent-id> \
 *     npx playwright test --config playwright.real-backend.config.js \
 *     tests/e2e/real-backend/collab-agent-sample.spec.js
 */

const BASE = process.env.ORIN_BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ORIN_ADMIN_USERNAME || 'admin'
const ADMIN_PASS = process.env.ORIN_ADMIN_PASSWORD || 'admin123'
const AGENT_ID = process.env.ORIN_PHASE1D_AGENT_ID || ''

test.describe('Phase 1D: Real Agent / MCP subtask sample', () => {

  let token = ''

  test.beforeAll(async ({ request }) => {
    if (process.env.ORIN_PHASE1D_RUN_AGENT !== '1') {
      test.skip()
    }
    if (!AGENT_ID) {
      throw new Error('ORIN_PHASE1D_AGENT_ID is required for this spec')
    }

    const res = await request.post(`${BASE}/api/v1/auth/login`, {
      data: { username: ADMIN_USER, password: ADMIN_PASS },
      headers: { 'Content-Type': 'application/json' }
    })
    expect(res.status()).toBe(200)
    token = (await res.json()).token
  })

  test('agent chat returns a non-error response with traceId', async ({ request }) => {
    const res = await request.post(`${BASE}/api/v1/agents/${AGENT_ID}/chat`, {
      data: {
        message: 'say hello in one word',
        stream: false
      },
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    // Accept both 200 (direct) and 202 (async/queued)
    expect([200, 202]).toContain(res.status())

    const body = await res.json()
    // Must not be an error shape
    if (body.error || body.code) {
      throw new Error(`Agent chat returned error: ${JSON.stringify(body)}`)
    }

    // TraceId must be present
    expect(body.traceId || body.trace_id).toBeDefined()

    // Text response must be non-empty
    expect(body.text || body.message || body.response).toBeTruthy()
  })

  test('collaboration package with agent subtask creates a trace record', async ({ request }) => {
    const createRes = await request.post(`${BASE}/api/v1/collaboration/packages`, {
      data: {
        intent: 'e2e agent subtask with trace',
        priority: 'HIGH',
        // Request agent as the performer so this package will invoke an agent
        capabilities: { llm: true, knowledge: false, mcp: false }
      },
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    expect(createRes.status()).toBe(200)
    const { packageId } = await createRes.json()

    try {
      const decompRes = await request.post(`${BASE}/api/v1/collaboration/packages/${packageId}/decompose`, {
        data: { capabilities: { llm: true } },
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
      })
      expect([200, 202]).toContain(decompRes.status())

      // Get subtask list
      const subtasksRes = await request.get(`${BASE}/api/v1/collaboration/packages/${packageId}/subtasks`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      expect(subtasksRes.status()).toBe(200)
      const subtasks = await subtasksRes.json()
      expect(Array.isArray(subtasks)).toBe(true)

      // Verify trace summary links back to the collaboration package
      const pkgRes = await request.get(`${BASE}/api/v1/collaboration/packages/${packageId}`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      expect(pkgRes.status()).toBe(200)
      const pkg = await pkgRes.json()

      if (pkg.traceId) {
        const summaryRes = await request.get(`${BASE}/api/v1/traces/${pkg.traceId}/summary`, {
          headers: { Authorization: `Bearer ${token}` }
        })
        expect(summaryRes.status()).toBe(200)
        const summary = await summaryRes.json()
        // Summary must include collaboration package reference
        expect(
          summary.collaborationPackage ||
          summary.collaborationPackages ||
          summary.packages
        ).toBeDefined()
      }
    } finally {
      // Cleanup
      await request.post(`${BASE}/api/v1/collaboration/packages/${packageId}/cancel`, {
        headers: { Authorization: `Bearer ${token}` }
      }).catch(() => {})
    }
  })
})