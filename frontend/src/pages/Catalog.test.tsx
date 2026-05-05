import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { defaultScenario } from '@/mocks/handlers'
import type { CreateSessionResponse } from '@/types/session'
import { LAST_SESSION_KEY } from '@/types/session'
import Catalog from './Catalog'

function LocationCapture({ onLocation }: { onLocation: (path: string) => void }) {
  const loc = useLocation()
  onLocation(loc.pathname)
  return null
}

function renderCatalog(initialEntries = ['/']) {
  const navigatedTo: string[] = []
  const result = render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/" element={<Catalog />} />
        <Route
          path="/lobby/:inviteCode"
          element={<LocationCapture onLocation={(p) => navigatedTo.push(p)} />}
        />
      </Routes>
    </MemoryRouter>
  )
  return { ...result, navigatedTo }
}

describe('Catalog', () => {
  it('카탈로그 1편 카드를 렌더한다', async () => {
    renderCatalog()

    expect(await screen.findByText('Toy Manor 살인 사건')).toBeInTheDocument()
    expect(screen.getByText('🏚️')).toBeInTheDocument()
    expect(screen.getByText('3명')).toBeInTheDocument()
    expect(screen.getByText('약 60분')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '세션 만들기' })).toBeInTheDocument()
  })

  it('빈 카탈로그에는 안내 문구를 보인다', async () => {
    server.use(http.get('/api/scenarios', () => HttpResponse.json([])))

    renderCatalog()

    expect(await screen.findByText('출시 시나리오가 없습니다')).toBeInTheDocument()
  })

  it('다편일 때 카드를 여러 개 렌더한다', async () => {
    const second = { ...defaultScenario, id: 'manor-2', title: '두 번째 시나리오' }
    server.use(http.get('/api/scenarios', () => HttpResponse.json([defaultScenario, second])))

    renderCatalog()

    expect(await screen.findByText('Toy Manor 살인 사건')).toBeInTheDocument()
    expect(screen.getByText('두 번째 시나리오')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '세션 만들기' })).toHaveLength(2)
  })

  it('서버 오류 시 에러 메시지를 보인다', async () => {
    server.use(http.get('/api/scenarios', () => new HttpResponse(null, { status: 500 })))

    renderCatalog()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('시나리오를 불러오지 못했습니다')
  })

  it('"세션 만들기" 클릭 시 닉네임 입력과 확인·취소 버튼이 표시된다', async () => {
    renderCatalog()

    fireEvent.click(await screen.findByRole('button', { name: '세션 만들기' }))

    expect(screen.getByPlaceholderText('닉네임')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument()
  })

  it('빈 닉네임에서 "확인" 버튼이 비활성이다', async () => {
    renderCatalog()

    fireEvent.click(await screen.findByRole('button', { name: '세션 만들기' }))
    const confirm = screen.getByRole('button', { name: '확인' })

    expect(confirm).toBeDisabled()
  })

  it('닉네임 입력 후 확인 시 POST 호출 → /lobby/:inviteCode로 이동 + localStorage 저장', async () => {
    const sessionRes: CreateSessionResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      hostNickname: 'alice',
      phase: 'lobby',
      playerId: 'player-001',
    }
    server.use(
      http.post('/api/sessions', async ({ request }) => {
        const body = await request.json() as { scenarioId: string; hostNickname: string }
        expect(body.scenarioId).toBe('toy-manor')
        expect(body.hostNickname).toBe('alice')
        return HttpResponse.json(sessionRes)
      })
    )

    const { navigatedTo } = renderCatalog()

    fireEvent.click(await screen.findByRole('button', { name: '세션 만들기' }))
    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: '확인' }))

    await waitFor(() => expect(navigatedTo).toContain('/lobby/012345'))

    const saved = JSON.parse(localStorage.getItem(LAST_SESSION_KEY)!)
    expect(saved.inviteCode).toBe('012345')
    expect(saved.sessionId).toBe('sess-001')
    expect(saved.playerId).toBe('player-001')
    expect(saved.isHost).toBe(true)
    expect(saved.savedAt).toBeTypeOf('number')
  })

  it('POST 실패 시 인라인 에러 메시지를 보인다', async () => {
    server.use(
      http.post('/api/sessions', () => new HttpResponse(null, { status: 500 }))
    )

    renderCatalog()

    fireEvent.click(await screen.findByRole('button', { name: '세션 만들기' }))
    fireEvent.change(screen.getByPlaceholderText('닉네임'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: '확인' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
