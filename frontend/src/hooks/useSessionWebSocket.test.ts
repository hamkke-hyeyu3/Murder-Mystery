import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { IMessage } from '@stomp/stompjs'
import { useSessionWebSocket } from './useSessionWebSocket'
import { useStompClient } from './useStompClient'
import { useSessionStore } from '@/stores/sessionStore'

vi.mock('./useStompClient')

const mockUseStompClient = vi.mocked(useStompClient)

describe('useSessionWebSocket', () => {
  let mockPublish: ReturnType<typeof vi.fn>
  let mockSubscribe: ReturnType<typeof vi.fn>
  let capturedCallback: ((msg: IMessage) => void) | null

  const defaultOptions = {
    sessionId: 'sess-001',
    inviteCode: 'ABC123',
    nickname: 'alice',
    playerId: 'player-alice',
  }

  beforeEach(() => {
    mockPublish = vi.fn()
    capturedCallback = null
    mockSubscribe = vi.fn((_topic: string, cb: (msg: IMessage) => void) => {
      capturedCallback = cb
      return { unsubscribe: vi.fn(), id: 'sub-1' }
    })

    mockUseStompClient.mockReturnValue({
      client: { current: { subscribe: mockSubscribe, publish: mockPublish } as never },
      connected: true,
    })

    useSessionStore.getState().reset()
  })

  it('connected 시 올바른 토픽에 구독한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    expect(mockSubscribe).toHaveBeenCalledWith(
      '/topic/session/sess-001/event',
      expect.any(Function)
    )
  })

  it('PLAYER_JOINED 이벤트 수신 시 players에 추가한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      capturedCallback!({
        body: JSON.stringify({
          type: 'PLAYER_JOINED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob', isHost: false },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().players).toContainEqual({ nickname: 'bob', isHost: false })
  })

  it('PLAYER_LEFT 이벤트 수신 시 players에서 제거한다', () => {
    useSessionStore.getState().setSession({
      players: [
        { nickname: 'alice', isHost: true },
        { nickname: 'bob', isHost: false },
      ],
    })

    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      capturedCallback!({
        body: JSON.stringify({
          type: 'PLAYER_LEFT',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob' },
        }),
      } as IMessage)
    })

    const players = useSessionStore.getState().players
    expect(players).not.toContainEqual(expect.objectContaining({ nickname: 'bob' }))
    expect(players).toContainEqual({ nickname: 'alice', isHost: true })
  })

  it('마운트/언마운트 시 publish를 호출하지 않는다', () => {
    const { unmount } = renderHook(() => useSessionWebSocket(defaultOptions))
    unmount()
    expect(mockPublish).not.toHaveBeenCalled()
  })

  it('LOBBY_COUNT_CHANGED 이벤트 수신 시 store의 joinedCount와 requiredCharacterCount를 갱신한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      capturedCallback!({
        body: JSON.stringify({
          type: 'LOBBY_COUNT_CHANGED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { joined: 2, required: 3 },
        }),
      } as IMessage)
    })

    const state = useSessionStore.getState()
    expect(state.joinedCount).toBe(2)
    expect(state.requiredCharacterCount).toBe(3)
  })

  it('publishLeave 호출 시 올바른 destination으로 send한다', () => {
    const { result } = renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      result.current.publishLeave()
    })

    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({ destination: '/app/session/sess-001/leave' })
    )
  })
})
