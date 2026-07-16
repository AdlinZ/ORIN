import { describe, expect, it } from 'vitest'
import monitorSettingsSource from '@/views/System/MonitorSettings.vue?raw'

describe('MonitorSettings secret display contract', () => {
  it('does not introduce development credential fallbacks into the browser bundle', () => {
    expect(monitorSettingsSource).not.toContain("'spring.rabbitmq.password': 'guest'")
    expect(monitorSettingsSource).not.toContain("host: '192.168.1.164'")
  })

  it('continues to model credentials as password fields rather than visible text', () => {
    expect(monitorSettingsSource).toContain("dbConfig['spring.datasource.password']")
    expect(monitorSettingsSource).toContain("dbConfig['spring.data.redis.password']")
    expect(monitorSettingsSource).toContain("dbConfig['spring.rabbitmq.password']")
    expect(monitorSettingsSource).toContain('type="password"')
  })
})
