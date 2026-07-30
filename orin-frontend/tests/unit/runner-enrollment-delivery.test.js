import { describe, expect, it } from 'vitest'
import { resolveRunnerControlPlaneOrigin } from '@/domains/runner/enrollmentDelivery'

describe('Runner enrollment command delivery', () => {
  it('bypasses the local Vite proxy for a relative enrollment endpoint', () => {
    expect(resolveRunnerControlPlaneOrigin(
      '/api/system/runners/enroll',
      'http://localhost:5173',
    )).toBe('http://localhost:8080')
  })

  it('preserves an absolute Control Plane endpoint returned by the backend', () => {
    expect(resolveRunnerControlPlaneOrigin(
      'https://control.orin.example/api/system/runners/enroll',
      'http://localhost:5173',
    )).toBe('https://control.orin.example')
  })

  it('uses the configured public origin for relative endpoints', () => {
    expect(resolveRunnerControlPlaneOrigin(
      '/api/system/runners/enroll',
      'http://localhost:5173',
      'https://api.orin.example/',
    )).toBe('https://api.orin.example')
  })
})
