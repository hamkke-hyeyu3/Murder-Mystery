import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { LAST_SESSION_KEY } from '@/types/session'
import type { JoinSessionResponse } from '@/types/session'
import Join from './Join'

function LocationCapture({ onLocation }: { onLocation: (path: string) => void }) {
  const loc = useLocation()
  onLocation(loc.pathname)
  return null
}

function renderJoin(initialEntry = '/join') {
  const navigatedTo: string[] = []
  const result = render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/join" element={<Join />} />
        <Route
          path="/lobby/:inviteCode"
          element={<LocationCapture onLocation={(p) => navigatedTo.push(p)} />}
        />
        <Route path="/" element={<LocationCapture onLocation={(p) => navigatedTo.push(p)} />} />
      </Routes>
    </MemoryRouter>
  )
  return { ...result, navigatedTo }
}

const defaultJoinResponse: JoinSessionResponse = {
  sessionId: 'sess-001',
  inviteCode: 'ABC123',
  scenarioId: 'toy-manor',
  phase: 'lobby',
  nickname: 'bob',
  playerId: 'player-bob',
  players: [
    { playerId: 'player-alice', nickname: 'alice', isHost: true },
    { playerId: 'player-bob', nickname: 'bob', isHost: false },
  ],
}

describe('Join', () => {
  it('쿼리 파라미터 invite가 있으면 초대 코드 입력란에 프리필된다', () => {
    renderJoin('/join?invite=XYZ999')

    expect(screen.getByPlaceholderText('초대 코드')).toHaveValue('XYZ999')
  })

  it('합류 성공 시 POST 후 /lobby/:inviteCode로 이동하고 localStorage에 저장한다', async () => {
    server.use(
      http.post('/api/sessions/:inviteCode/join', async ({ request, params }) => {
        const body = await request.json() as { nickname: string }
        expect(params.inviteCode).toBe('ABC123')
        expect(body.nickname).toBe('bob')
        return HttpResponse.json(defaultJoinResponse)
      })
    )

    const { navigatedTo } = renderJoin('/join?invite=ABC123')

    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'bob' } })
    fireEvent.click(screen.getByRole('button', { name: '합류' }))

    await waitFor(() => expect(navigatedTo).toContain('/lobby/ABC123'))

    const saved = JSON.parse(localStorage.getItem(LAST_SESSION_KEY)!)
    expect(saved.inviteCode).toBe('ABC123')
    expect(saved.sessionId).toBe('sess-001')
    expect(saved.playerId).toBe('player-bob')
    expect(saved.isHost).toBe(false)
    expect(saved.savedAt).toBeTypeOf('number')
  })

  it('409 응답 시 닉네임 충돌 인라인 에러를 표시한다', async () => {
    server.use(
      http.post('/api/sessions/:inviteCode/join', () =>
        HttpResponse.json({ detail: 'nickname already taken' }, { status: 409 })
      )
    )

    renderJoin('/join?invite=ABC123')

    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: '합류' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임')
  })

  it('400 응답 시 알 수 없는 초대 코드 인라인 에러를 표시한다', async () => {
    server.use(
      http.post('/api/sessions/:inviteCode/join', () =>
        new HttpResponse(null, { status: 400 })
      )
    )

    renderJoin('/join?invite=BADCODE')

    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'bob' } })
    fireEvent.click(screen.getByRole('button', { name: '합류' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('초대 번호를 다시 확인하세요')
  })

  it('닉네임 또는 초대 코드가 비어 있으면 합류 버튼이 비활성이다', () => {
    renderJoin('/join')

    const button = screen.getByRole('button', { name: '합류' })
    expect(button).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('초대 코드'), { target: { value: 'ABC123' } })
    expect(button).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'bob' } })
    expect(button).not.toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: '' } })
    expect(button).toBeDisabled()
  })
})
