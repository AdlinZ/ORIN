import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  formatRemainingTime,
  getTokenRemainingTime,
  isTokenExpired,
  isTokenExpiringSoon,
  parseJwt
} from '@/utils/jwt'

const tokenFor = (payload) => `header.${btoa(JSON.stringify(payload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')}.signature`

afterEach(() => vi.restoreAllMocks())

describe('JWT helpers', () => {
  it('parses valid payloads and rejects missing or malformed tokens without throwing', () => {
    expect(parseJwt(tokenFor({ sub: 'runner', roles: ['ROLE_USER'] }))).toEqual({ sub: 'runner', roles: ['ROLE_USER'] })
    expect(parseJwt()).toBeNull()

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(parseJwt('header.%%%.signature')).toBeNull()
    expect(consoleError).toHaveBeenCalled()
  })

  it('calculates expiry and remaining time from the JWT exp claim', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1_000_000)

    expect(isTokenExpired(tokenFor({ exp: 999 }))).toBe(true)
    expect(isTokenExpired(tokenFor({ exp: 1001 }))).toBe(false)
    expect(isTokenExpired(tokenFor({ sub: 'no-exp' }))).toBe(false)
    expect(getTokenRemainingTime(tokenFor({ exp: 1002 }))).toBe(2_000)
    expect(getTokenRemainingTime(tokenFor({ exp: 999 }))).toBe(0)
    expect(getTokenRemainingTime(tokenFor({ sub: 'no-exp' }))).toBe(Number.MAX_SAFE_INTEGER)
  })

  it('formats remaining time and identifies soon-to-expire credentials', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1_000_000)

    expect(formatRemainingTime(0)).toBe('已过期')
    expect(formatRemainingTime(59_000)).toBe('59 秒')
    expect(formatRemainingTime(60_000)).toBe('1 分钟')
    expect(formatRemainingTime(3_600_000)).toBe('1 小时')
    expect(formatRemainingTime(86_400_000)).toBe('1 天')
    expect(isTokenExpiringSoon(tokenFor({ exp: 1002 }))).toBe(true)
    expect(isTokenExpiringSoon(tokenFor({ exp: 2000 }))).toBe(false)
    expect(isTokenExpiringSoon(tokenFor({ exp: 1002 }), 0)).toBe(false)
  })
})
