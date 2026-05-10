import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { IMessage } from '@stomp/stompjs'
import { useSessionWebSocket } from './useSessionWebSocket'
import { useStompClient } from './useStompClient'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useTimerStore } from '@/stores/timerStore'

vi.mock('./useStompClient')

const mockUseStompClient = vi.mocked(useStompClient)

describe('useSessionWebSocket', () => {
  let mockPublish: ReturnType<typeof vi.fn>
  let mockSubscribe: ReturnType<typeof vi.fn>
  // topic callback for /topic/session/{id}/event
  let topicCallback: ((msg: IMessage) => void) | null
  // private callback for /user/queue/.../private
  let privateCallback: ((msg: IMessage) => void) | null

  const defaultOptions = {
    sessionId: 'sess-001',
    inviteCode: 'ABC123',
    nickname: 'alice',
    playerId: 'player-alice',
  }

  beforeEach(() => {
    mockPublish = vi.fn()
    topicCallback = null
    privateCallback = null
    mockSubscribe = vi.fn((topic: string, cb: (msg: IMessage) => void) => {
      if (topic.includes('/topic/')) topicCallback = cb
      else privateCallback = cb
      return { unsubscribe: vi.fn(), id: 'sub-1' }
    })

    mockUseStompClient.mockReturnValue({
      client: { current: { subscribe: mockSubscribe, publish: mockPublish } as never },
      connected: true,
    })

    useSessionStore.getState().reset()
    useCardStore.getState().reset()
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
      topicCallback!({
        body: JSON.stringify({
          type: 'PLAYER_JOINED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob', isHost: false },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().players).toContainEqual({ playerId: 'player-bob', nickname: 'bob', isHost: false })
  })

  it('PLAYER_JOINED가 이미 존재하는 playerId면 중복 추가하지 않는다', () => {
    useSessionStore.getState().setSession({
      players: [{ playerId: 'player-bob', nickname: 'bob', isHost: false }],
    })

    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'PLAYER_JOINED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob', isHost: false },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().players).toHaveLength(1)
  })

  it('PLAYER_LEFT 이벤트 수신 시 leftPlayerIds에 추가한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'PLAYER_LEFT',
          sessionId: 'sess-001',
          occurredAt: '2026-05-06T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob' },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().leftPlayerIds).toContain('player-bob')
  })

  it('PLAYER_LEFT 이벤트 수신 시 players에서 제거한다', () => {
    useSessionStore.getState().setSession({
      players: [
        { playerId: 'player-alice', nickname: 'alice', isHost: true },
        { playerId: 'player-bob', nickname: 'bob', isHost: false },
      ],
    })

    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
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
    expect(players).toContainEqual({ playerId: 'player-alice', nickname: 'alice', isHost: true })
  })

  it('마운트/언마운트 시 publish를 호출하지 않는다', () => {
    const { unmount } = renderHook(() => useSessionWebSocket(defaultOptions))
    unmount()
    expect(mockPublish).not.toHaveBeenCalled()
  })

  it('LOBBY_COUNT_CHANGED 이벤트 수신 시 store의 joinedCount와 requiredCharacterCount를 갱신한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
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

  it('private 큐를 구독한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    expect(mockSubscribe).toHaveBeenCalledWith(
      '/user/queue/session/sess-001/private',
      expect.any(Function)
    )
  })

  it('SESSION_STATE_CHANGED 수신 시 sessionStore의 phase와 state를 갱신한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'SESSION_STATE_CHANGED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: { state: 'intro', turnOrder: ['char-a', 'char-b', 'char-c'] },
        }),
      } as IMessage)
    })

    const state = useSessionStore.getState()
    expect(state.phase).toBe('in_progress')
    expect(state.state).toBe('intro')
    expect(state.turnOrder).toEqual(['char-a', 'char-b', 'char-c'])
  })

  it('CHARACTER_CARD_DEALT 수신 시 cardStore에 캐릭터 카드를 설정한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      privateCallback!({
        body: JSON.stringify({
          type: 'CHARACTER_CARD_DEALT',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: { characterId: 'char-a', name: 'Alice', turnOrderIndex: 0 },
        }),
      } as IMessage)
    })

    const card = useCardStore.getState().characterCard
    expect(card).toEqual({ characterId: 'char-a', name: 'Alice', turnOrderIndex: 0 })
  })

  it('TUTORIAL_ACKED 수신 시 tutorialAckedCount와 tutorialTotalCount를 갱신한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'TUTORIAL_ACKED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: { playerId: 'player-bob', nickname: 'bob', acked: 2, total: 3 },
        }),
      } as IMessage)
    })

    const state = useSessionStore.getState()
    expect(state.tutorialAckedCount).toBe(2)
    expect(state.tutorialTotalCount).toBe(3)
    expect(state.myTutorialAcked).toBe(false)
  })

  it('TUTORIAL_ACKED 페이로드가 본인 playerId면 myTutorialAcked를 true로 설정한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'TUTORIAL_ACKED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: { playerId: 'player-alice', nickname: 'alice', acked: 1, total: 3 },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().myTutorialAcked).toBe(true)
  })

  it('SERVER_TIME_SYNC 이벤트 수신 시 timerStore.serverOffsetMs를 설정한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    const fakeServerNow = Date.now() + 2000
    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'SERVER_TIME_SYNC',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: { serverNow: fakeServerNow },
        }),
      } as IMessage)
    })

    expect(useTimerStore.getState().serverOffsetMs).toBeCloseTo(2000, -2)
  })

  it('ROUND_STARTED 이벤트 수신 시 sessionStore와 timerStore를 갱신한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    const deadlineAt = Date.now() + 300_000

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'ROUND_STARTED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: {
            roundNumber: 1,
            prompt: '한 사람씩 자기 캐릭터를 짧게 소개해 주세요.',
            commonHint: null,
            deadlineAt,
            startedAt: Date.now(),
          },
        }),
      } as IMessage)
    })

    const sessionState = useSessionStore.getState()
    expect(sessionState.state).toBe('round')
    expect(sessionState.roundNumber).toBe(1)
    expect(sessionState.roundPrompt).toBe('한 사람씩 자기 캐릭터를 짧게 소개해 주세요.')
    expect(sessionState.roundCommonHint).toBeNull()

    expect(useTimerStore.getState().deadlineAt).toBe(deadlineAt)
  })

  it('ROUND_STARTED commonHint가 있으면 sessionStore에 저장한다', () => {
    renderHook(() => useSessionWebSocket(defaultOptions))

    act(() => {
      topicCallback!({
        body: JSON.stringify({
          type: 'ROUND_STARTED',
          sessionId: 'sess-001',
          occurredAt: '2026-05-10T00:00:00Z',
          payload: {
            roundNumber: 2,
            prompt: '알리바이를 비교해 보세요.',
            commonHint: '부검 결과가 공개됐다.',
            deadlineAt: Date.now() + 300_000,
            startedAt: Date.now(),
          },
        }),
      } as IMessage)
    })

    expect(useSessionStore.getState().roundCommonHint).toBe('부검 결과가 공개됐다.')
  })
})
