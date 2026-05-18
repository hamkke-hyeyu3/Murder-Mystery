import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { getSession } from '@/lib/sessionApi'
import Play from './Play'

vi.mock('@/hooks/useSessionWebSocket')
vi.mock('@/lib/sessionApi', () => ({
  postTutorialAck: vi.fn().mockResolvedValue({ acked: 1, total: 3, state: 'tutorial' }),
  getSession: vi.fn(() => new Promise(() => {})),  // pending by default; override per test
}))

const mockUseSessionWebSocket = vi.mocked(useSessionWebSocket)

const mockGetSession = vi.mocked(getSession)

const mockWsBase = () => ({
  connected: false as const,
  publishLeave: vi.fn(),
  publishSelectLocation: vi.fn(),
  publishItemExchange: vi.fn(),
  publishItemShareFull: vi.fn(),
  publishItemSharePartial: vi.fn(),
  publishPrivateTalkRequest: vi.fn(),
  publishPrivateTalkAccept: vi.fn(),
  publishPrivateTalkReject: vi.fn(),
  publishPrivateTalkEnd: vi.fn(),
})

beforeEach(() => {
  mockUseSessionWebSocket.mockReturnValue(mockWsBase())
})

afterEach(() => {
  vi.useRealTimers()
})

function renderPlay(sessionId = 'sess-001') {
  return render(
    <MemoryRouter initialEntries={[`/play/${sessionId}`]}>
      <Routes>
        <Route path="/play/:sessionId" element={<Play />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('Play', () => {
  it('캐릭터 카드가 없으면 배정 대기 placeholder를 보인다', () => {
    renderPlay()
    expect(screen.getByTestId('play-waiting-card')).toBeInTheDocument()
    expect(screen.getByText('캐릭터 배정 중…')).toBeInTheDocument()
  })

  it('캐릭터 카드가 있으면 카드를 렌더하고 배정 대기 placeholder는 숨긴다', () => {
    useCardStore.getState().setCharacterCard({
      characterId: 'char-a',
      name: 'Alice',
      turnOrderIndex: 0,
    })

    renderPlay()

    expect(screen.getByTestId('character-card')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.queryByTestId('play-waiting-card')).not.toBeInTheDocument()
  })

  it("state='tutorial'일 때 Tutorial 컴포넌트를 렌더한다", () => {
    useSessionStore.getState().setSession({ state: 'tutorial', players: [] })

    renderPlay()

    expect(screen.getByTestId('tutorial')).toBeInTheDocument()
    expect(screen.getByText(/전원 거짓말 가능/)).toBeInTheDocument()
    expect(screen.queryByTestId('play-waiting-card')).not.toBeInTheDocument()
  })

  it("state='round'일 때 RoundPanel을 렌더한다", () => {
    useSessionStore.getState().setSession({
      state: 'round',
      roundNumber: 1,
      roundPrompt: '한 사람씩 자기 캐릭터를 짧게 소개해 주세요.',
      roundCommonHint: null,
    })

    renderPlay()

    expect(screen.getByTestId('round-panel')).toBeInTheDocument()
    expect(screen.getByTestId('round-prompt')).toHaveTextContent('한 사람씩 자기 캐릭터를 짧게 소개해 주세요.')
    expect(screen.queryByTestId('tutorial')).not.toBeInTheDocument()
  })

  it("state='character_assignment'일 때 카드 영역을 렌더한다", () => {
    useSessionStore.getState().setSession({ state: 'character_assignment' })
    useCardStore.getState().setCharacterCard({
      characterId: 'char-a',
      name: 'Alice',
      turnOrderIndex: 0,
    })

    renderPlay()

    expect(screen.getByTestId('character-card')).toBeInTheDocument()
    expect(screen.queryByTestId('tutorial')).not.toBeInTheDocument()
  })

  it('스냅샷 hydration: myClues=[] 이면 cardStore.clues가 빈 배열로 덮어쓰인다', async () => {
    useCardStore.getState().setClues([
      { id: 'clue-old', itemId: 'item-1', title: '낡은 편지', originLocationId: 'loc-a',
        roundNumberDiscovered: 1, discoveredAt: 1000, source: 'discovery' },
    ])

    const snap = {
      sessionId: 'sess-001', inviteCode: '123456', scenarioId: 'toy-manor',
      phase: 'in_progress', requiredCharacterCount: 3, joinedCount: 3,
      players: [{ playerId: 'p1', nickname: 'Alice', isHost: true }],
      me: {
        playerId: 'p1', nickname: 'Alice', isHost: true,
        assignedCharacterId: 'char-a',
        character: { characterId: 'char-a', name: 'Alice', turnOrderIndex: 0 },
        objective: null, tutorialAckedAt: null,
        myClues: [],
      },
    }
    mockGetSession.mockResolvedValueOnce(snap as never)

    renderPlay('sess-001')

    await waitFor(() => {
      expect(useCardStore.getState().clues).toHaveLength(0)
    })
  })

  it("state='round'일 때 단서 long-press → 전체공유 시 publishItemShareFull 이 호출된다", async () => {
    vi.useFakeTimers()
    const publishItemShareFull = vi.fn()
    mockUseSessionWebSocket.mockReturnValue({ ...mockWsBase(), publishItemShareFull })
    useSessionStore.getState().setSession({
      state: 'round',
      roundNumber: 1,
      roundPrompt: '자기소개',
      roundCommonHint: null,
      players: [{ playerId: 'player-me', nickname: 'Alice', isHost: true }],
    })
    useSessionStore.getState().setSession({ playerId: 'player-me' })
    useCardStore.getState().setClues([
      { id: 'clue-share', itemId: 'item-1', title: '단서A', originLocationId: 'loc-1',
        roundNumberDiscovered: 1, discoveredAt: 1000, source: 'discovery' },
    ])

    const { getByTestId } = renderPlay()

    const item = getByTestId('clue-item-clue-share')
    fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })
    await act(async () => { vi.advanceTimersByTime(500) })

    const shareFullBtn = getByTestId('action-share-full')
    fireEvent.click(shareFullBtn)

    expect(publishItemShareFull).toHaveBeenCalledWith('clue-share')
  })

  it("state='round'일 때 단서 long-press → 부분공유 시 publishItemSharePartial 이 호출된다", async () => {
    vi.useFakeTimers()
    const publishItemSharePartial = vi.fn()
    mockUseSessionWebSocket.mockReturnValue({ ...mockWsBase(), publishItemSharePartial })
    useSessionStore.getState().setSession({
      state: 'round',
      roundNumber: 1,
      roundPrompt: '자기소개',
      roundCommonHint: null,
      players: [
        { playerId: 'player-me', nickname: 'Alice', isHost: true },
        { playerId: 'player-bob', nickname: 'Bob', isHost: false },
      ],
    })
    useSessionStore.getState().setSession({ playerId: 'player-me' })
    useCardStore.getState().setClues([
      { id: 'clue-partial', itemId: 'item-2', title: '단서B', originLocationId: 'loc-1',
        roundNumberDiscovered: 1, discoveredAt: 1000, source: 'discovery' },
    ])

    const { getByTestId } = renderPlay()

    const item = getByTestId('clue-item-clue-partial')
    fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })
    await act(async () => { vi.advanceTimersByTime(500) })

    fireEvent.click(getByTestId('action-share-partial'))
    fireEvent.click(getByTestId('recipient-player-bob'))
    fireEvent.click(getByTestId('confirm-share-partial'))

    expect(publishItemSharePartial).toHaveBeenCalledWith('clue-partial', ['player-bob'])
  })

  it("state='vote'일 때 vote-placeholder를 렌더한다", () => {
    useSessionStore.getState().setSession({ state: 'vote' })

    renderPlay()

    expect(screen.getByTestId('vote-placeholder')).toBeInTheDocument()
    expect(screen.queryByTestId('round-panel')).not.toBeInTheDocument()
  })

  it("state='round'일 때 CharacterCard와 RoundPanel이 함께 렌더된다", () => {
    useSessionStore.getState().setSession({
      state: 'round',
      roundNumber: 1,
      roundPrompt: '자기소개를 해주세요.',
      roundCommonHint: null,
    })
    useCardStore.getState().setCharacterCard({
      characterId: 'char-a',
      name: 'Alice',
      turnOrderIndex: 0,
    })

    renderPlay()

    expect(screen.getByTestId('character-card')).toBeInTheDocument()
    expect(screen.getByTestId('round-panel')).toBeInTheDocument()
  })
})
