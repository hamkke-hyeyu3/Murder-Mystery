import { describe, it, expect, beforeEach, vi } from 'vitest'

const UUID_A = '00000000-0000-0000-0000-000000000001'
const UUID_B = '11111111-1111-1111-1111-111111111111'

function resetModule() {
  vi.resetModules()
}

beforeEach(() => {
  resetModule()
  localStorage.clear()
  vi.unstubAllEnvs()
  // Reset location.search
  history.replaceState(null, '', '/')
})

describe('getDeviceId — dev override via ?deviceId=', () => {
  it('valid UUID in query string → localStorage 저장 + URL 파라미터 제거', async () => {
    vi.stubEnv('DEV', true)
    history.replaceState(null, '', `/?deviceId=${UUID_A}`)

    const { getDeviceId } = await import('./deviceId')
    const id = getDeviceId()

    expect(id).toBe(UUID_A)
    expect(localStorage.getItem('mm:deviceId')).toBe(UUID_A)
    expect(location.search).toBe('')
  })

  it('유효하지 않은 문자열 → override 무시, 새 UUID 발급', async () => {
    vi.stubEnv('DEV', true)
    history.replaceState(null, '', '/?deviceId=not-a-uuid')

    const { getDeviceId } = await import('./deviceId')
    const id = getDeviceId()

    expect(id).not.toBe('not-a-uuid')
    expect(id).toMatch(/^[0-9a-f-]{36}$/i)
  })

  it('prod 빌드(DEV=false) → query override 무시', async () => {
    vi.stubEnv('DEV', false)
    history.replaceState(null, '', `/?deviceId=${UUID_A}`)
    localStorage.setItem('mm:deviceId', UUID_B)

    const { getDeviceId } = await import('./deviceId')
    const id = getDeviceId()

    // override가 무시되고 localStorage 기존 값 반환
    expect(id).toBe(UUID_B)
    expect(localStorage.getItem('mm:deviceId')).toBe(UUID_B)
  })

  it('query 없으면 localStorage 기존 값 반환', async () => {
    vi.stubEnv('DEV', true)
    localStorage.setItem('mm:deviceId', UUID_B)

    const { getDeviceId } = await import('./deviceId')
    const id = getDeviceId()

    expect(id).toBe(UUID_B)
  })
})
