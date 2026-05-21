import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { RequestPrivateTalkPanel } from './RequestPrivateTalkPanel'
import type { PlayerSummary } from '@/stores/sessionStore'

const alice: PlayerSummary = { playerId: 'alice-id', nickname: 'Alice', isHost: true }
const bob: PlayerSummary = { playerId: 'bob-id', nickname: 'Bob', isHost: false }
const charlie: PlayerSummary = { playerId: 'charlie-id', nickname: 'Charlie', isHost: false }

describe('RequestPrivateTalkPanel', () => {
  it('자기 자신(myPlayerId)은 목록에 렌더되지 않는다', () => {
    render(
      <RequestPrivateTalkPanel
        players={[alice, bob]}
        myPlayerId="alice-id"
        leftPlayerIds={[]}
        disabled={false}
        onRequest={vi.fn()}
      />
    )
    expect(screen.queryByTestId('private-talk-request-alice-id')).toBeNull()
    expect(screen.getByTestId('private-talk-request-bob-id')).toBeTruthy()
  })

  it('이탈한 플레이어(leftPlayerIds 포함)는 목록에 렌더되지 않는다', () => {
    render(
      <RequestPrivateTalkPanel
        players={[alice, bob, charlie]}
        myPlayerId="alice-id"
        leftPlayerIds={['charlie-id']}
        disabled={false}
        onRequest={vi.fn()}
      />
    )
    expect(screen.getByTestId('private-talk-request-bob-id')).toBeTruthy()
    expect(screen.queryByTestId('private-talk-request-charlie-id')).toBeNull()
  })

  it('버튼 클릭 시 onRequest(targetPlayerId) 호출', () => {
    const onRequest = vi.fn()
    render(
      <RequestPrivateTalkPanel
        players={[alice, bob]}
        myPlayerId="alice-id"
        leftPlayerIds={[]}
        disabled={false}
        onRequest={onRequest}
      />
    )
    fireEvent.click(screen.getByTestId('private-talk-request-bob-id'))
    expect(onRequest).toHaveBeenCalledWith('bob-id')
  })

  it('myPlayerId=null이면 아무것도 렌더되지 않는다', () => {
    render(
      <RequestPrivateTalkPanel
        players={[alice, bob]}
        myPlayerId={null}
        leftPlayerIds={[]}
        disabled={false}
        onRequest={vi.fn()}
      />
    )
    expect(screen.queryByTestId('private-talk-request-panel')).toBeNull()
  })

  it('disabled=true이면 모든 버튼이 비활성화되고 클릭해도 핸들러 미호출', () => {
    const onRequest = vi.fn()
    render(
      <RequestPrivateTalkPanel
        players={[alice, bob, charlie]}
        myPlayerId="alice-id"
        leftPlayerIds={[]}
        disabled={true}
        onRequest={onRequest}
      />
    )
    const bobBtn = screen.getByTestId('private-talk-request-bob-id') as HTMLButtonElement
    const charlieBtn = screen.getByTestId('private-talk-request-charlie-id') as HTMLButtonElement
    expect(bobBtn.disabled).toBe(true)
    expect(charlieBtn.disabled).toBe(true)
    fireEvent.click(bobBtn)
    expect(onRequest).not.toHaveBeenCalled()
  })
})
