import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useTransientStore } from '@/stores/transientStore'
import { PrivateTalkInlineCard } from './PrivateTalkInlineCard'

const EXPIRES_60S = Date.now() + 60_000

beforeEach(() => {
  useTransientStore.getState().reset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('PrivateTalkInlineCard — target(incoming) view', () => {
  it('수락·거절 두 버튼이 동등 시각으로 렌더된다', () => {
    useTransientStore.getState().setPendingPrivateTalk({
      requestId: 'req-1',
      partnerPlayerId: 'alice-id',
      partnerNickname: 'Alice',
      role: 'target',
      expiresAt: EXPIRES_60S,
    })
    const onAccept = vi.fn()
    const onReject = vi.fn()

    render(<PrivateTalkInlineCard onAccept={onAccept} onReject={onReject} />)

    expect(screen.getByTestId('private-talk-accept')).toBeTruthy()
    expect(screen.getByTestId('private-talk-reject')).toBeTruthy()
  })

  it('수락 클릭 시 onAccept(requestId) 호출', () => {
    useTransientStore.getState().setPendingPrivateTalk({
      requestId: 'req-2',
      partnerPlayerId: 'alice-id',
      partnerNickname: 'Alice',
      role: 'target',
      expiresAt: EXPIRES_60S,
    })
    const onAccept = vi.fn()
    const onReject = vi.fn()

    render(<PrivateTalkInlineCard onAccept={onAccept} onReject={onReject} />)
    fireEvent.click(screen.getByTestId('private-talk-accept'))

    expect(onAccept).toHaveBeenCalledWith('req-2')
  })

  it('거절 클릭 시 onReject(requestId) 호출 + pendingPrivateTalk 해제', () => {
    useTransientStore.getState().setPendingPrivateTalk({
      requestId: 'req-3',
      partnerPlayerId: 'alice-id',
      partnerNickname: 'Alice',
      role: 'target',
      expiresAt: EXPIRES_60S,
    })
    const onAccept = vi.fn()
    const onReject = vi.fn()

    render(<PrivateTalkInlineCard onAccept={onAccept} onReject={onReject} />)
    fireEvent.click(screen.getByTestId('private-talk-reject'))

    expect(onReject).toHaveBeenCalledWith('req-3')
    expect(useTransientStore.getState().pendingPrivateTalk).toBeNull()
  })
})

describe('PrivateTalkInlineCard — requester(pending) view', () => {
  it('카운트다운 텍스트가 보인다', () => {
    vi.useFakeTimers()
    const expiresAt = Date.now() + 60_000
    useTransientStore.getState().setPendingPrivateTalk({
      requestId: 'req-4',
      partnerPlayerId: 'bob-id',
      partnerNickname: 'Bob',
      role: 'requester',
      expiresAt,
    })

    render(<PrivateTalkInlineCard onAccept={vi.fn()} onReject={vi.fn()} />)

    expect(screen.getByTestId('private-talk-pending')).toBeTruthy()
  })

  it('60초 경과 후 카드가 사라진다', async () => {
    vi.useFakeTimers()
    const expiresAt = Date.now() + 1000
    useTransientStore.getState().setPendingPrivateTalk({
      requestId: 'req-5',
      partnerPlayerId: 'bob-id',
      partnerNickname: 'Bob',
      role: 'requester',
      expiresAt,
    })

    render(<PrivateTalkInlineCard onAccept={vi.fn()} onReject={vi.fn()} />)
    expect(screen.getByTestId('private-talk-pending')).toBeTruthy()

    await act(async () => { vi.advanceTimersByTime(2000) })

    expect(screen.queryByTestId('private-talk-pending')).toBeNull()
    expect(useTransientStore.getState().pendingPrivateTalk).toBeNull()
  })
})

describe('PrivateTalkInlineCard — null state', () => {
  it('pendingPrivateTalk이 null이면 아무것도 렌더하지 않는다', () => {
    render(<PrivateTalkInlineCard onAccept={vi.fn()} onReject={vi.fn()} />)
    expect(screen.queryByTestId('private-talk-accept')).toBeNull()
    expect(screen.queryByTestId('private-talk-pending')).toBeNull()
  })
})
