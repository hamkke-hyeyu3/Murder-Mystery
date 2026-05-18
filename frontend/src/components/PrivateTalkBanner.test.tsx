import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { useSessionStore } from '@/stores/sessionStore'
import { PrivateTalkBanner } from './PrivateTalkBanner'

beforeEach(() => {
  useSessionStore.getState().reset()
})

describe('PrivateTalkBanner', () => {
  it('currentPrivateTalk이 null이면 렌더하지 않는다', () => {
    render(<PrivateTalkBanner />)
    expect(screen.queryByTestId('private-talk-banner')).toBeNull()
  })

  it('"A, B 밀담 중" 배너를 모든 단말에 보여준다', () => {
    useSessionStore.setState({
      currentPrivateTalk: {
        requestId: 'talk-1',
        participants: [
          { playerId: 'alice-id', nickname: 'Alice' },
          { playerId: 'bob-id', nickname: 'Bob' },
        ],
        startedAt: Date.now(),
      },
    })

    render(<PrivateTalkBanner />)

    const banner = screen.getByTestId('private-talk-banner')
    expect(banner.textContent).toContain('Alice')
    expect(banner.textContent).toContain('Bob')
  })

  it('참여자 단말에서도 배너를 표시한다 (PRD A4.5: 모든 단말)', () => {
    useSessionStore.setState({
      currentPrivateTalk: {
        requestId: 'talk-2',
        participants: [
          { playerId: 'alice-id', nickname: 'Alice' },
          { playerId: 'bob-id', nickname: 'Bob' },
        ],
        startedAt: Date.now(),
      },
    })

    render(<PrivateTalkBanner />)
    expect(screen.getByTestId('private-talk-banner')).toBeTruthy()
  })
})
