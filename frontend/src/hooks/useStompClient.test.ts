import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@stomp/stompjs', () => {
  const Client = vi.fn().mockImplementation(() => ({
    activate: vi.fn(),
    deactivate: vi.fn().mockResolvedValue(undefined),
  }))
  return { Client }
})

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockReturnValue({ close: vi.fn() }),
}))

vi.mock('@/lib/deviceId', () => ({
  getDeviceId: () => 'test-device-id',
}))

import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useStompClient } from './useStompClient'

const MockClient = vi.mocked(Client)
const MockSockJS = vi.mocked(SockJS)

const defaultOpts = { inviteCode: 'ABC123', nickname: 'alice', playerId: 'p1' }

describe('useStompClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('Client를 webSocketFactory로 생성하며 brokerURL을 사용하지 않는다', () => {
    renderHook(() => useStompClient(defaultOpts))

    expect(MockClient).toHaveBeenCalledOnce()
    const opts = MockClient.mock.calls[0][0] as Record<string, unknown>
    expect(opts.webSocketFactory).toBeTypeOf('function')
    expect(opts.brokerURL).toBeUndefined()
  })

  it('webSocketFactory 호출 시 SockJS("/ws")를 반환한다', () => {
    renderHook(() => useStompClient(defaultOpts))

    const opts = MockClient.mock.calls[0][0] as Record<string, unknown>
    ;(opts.webSocketFactory as () => unknown)()

    expect(MockSockJS).toHaveBeenCalledWith('/ws')
  })

  it('현재 client의 onConnect 콜백은 connected를 true로 만든다', () => {
    const { result } = renderHook(() => useStompClient(defaultOpts))

    expect(result.current.connected).toBe(false)

    const opts = MockClient.mock.calls[0][0] as Record<string, unknown>
    act(() => (opts.onConnect as () => void)())

    expect(result.current.connected).toBe(true)
  })

  it('교체된 stale client의 onConnect는 connected를 변경하지 않는다 (StrictMode 방어)', () => {
    const { result, rerender } = renderHook(
      (props) => useStompClient(props),
      { initialProps: { inviteCode: 'OLD' } }
    )

    const staleOnConnect = (MockClient.mock.calls[0][0] as Record<string, unknown>).onConnect as () => void

    // 헤더 변경 → client 교체 트리거
    rerender({ inviteCode: 'NEW' })

    act(() => staleOnConnect())

    expect(result.current.connected).toBe(false)
  })

  it('교체 후 stale onDisconnect는 새 client가 연결된 상태를 false로 되돌리지 않는다', () => {
    const { result, rerender } = renderHook(
      (props) => useStompClient(props),
      { initialProps: { inviteCode: 'OLD' } }
    )

    const client1Opts = MockClient.mock.calls[0][0] as Record<string, unknown>
    act(() => (client1Opts.onConnect as () => void)())
    expect(result.current.connected).toBe(true)

    rerender({ inviteCode: 'NEW' })
    expect(MockClient).toHaveBeenCalledTimes(2)

    const client2Opts = MockClient.mock.calls[1][0] as Record<string, unknown>
    act(() => (client2Opts.onConnect as () => void)())
    expect(result.current.connected).toBe(true)

    // stale client1의 onDisconnect가 뒤늦게 도착
    act(() => (client1Opts.onDisconnect as () => void)())

    expect(result.current.connected).toBe(true)
  })
})
