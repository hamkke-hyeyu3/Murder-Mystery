import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { defaultScenario } from '@/mocks/handlers'
import Catalog from './Catalog'

describe('Catalog', () => {
  it('카탈로그 1편 카드를 렌더한다', async () => {
    render(<Catalog />)

    expect(await screen.findByText('Toy Manor 살인 사건')).toBeInTheDocument()
    expect(screen.getByText('🏚️')).toBeInTheDocument()
    expect(screen.getByText('3명')).toBeInTheDocument()
    expect(screen.getByText('약 60분')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '세션 만들기' })).toBeInTheDocument()
  })

  it('빈 카탈로그에는 안내 문구를 보인다', async () => {
    server.use(http.get('/api/scenarios', () => HttpResponse.json([])))

    render(<Catalog />)

    expect(await screen.findByText('출시 시나리오가 없습니다')).toBeInTheDocument()
  })

  it('다편일 때 카드를 여러 개 렌더한다', async () => {
    const second = { ...defaultScenario, id: 'manor-2', title: '두 번째 시나리오' }
    server.use(http.get('/api/scenarios', () => HttpResponse.json([defaultScenario, second])))

    render(<Catalog />)

    expect(await screen.findByText('Toy Manor 살인 사건')).toBeInTheDocument()
    expect(screen.getByText('두 번째 시나리오')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '세션 만들기' })).toHaveLength(2)
  })

  it('서버 오류 시 에러 메시지를 보인다', async () => {
    server.use(http.get('/api/scenarios', () => new HttpResponse(null, { status: 500 })))

    render(<Catalog />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('시나리오를 불러오지 못했습니다')
  })
})
