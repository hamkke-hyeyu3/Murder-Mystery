import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Catalog from '@/pages/Catalog'
import Join from '@/pages/Join'
import Lobby from '@/pages/Lobby'
import Play from '@/pages/Play'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<Catalog />} />
        <Route path="/lobby/:inviteCode" element={<Lobby />} />
        <Route path="/play/:sessionId" element={<Play />} />
        <Route path="/join" element={<Join />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('App routes', () => {
  it('renders Catalog at /', () => {
    renderAt('/')
    expect(screen.getByTestId('page-catalog')).toBeInTheDocument()
  })

  it('renders Lobby at /lobby/:inviteCode and exposes the code', () => {
    renderAt('/lobby/ABC123')
    expect(screen.getByTestId('page-lobby')).toHaveTextContent('ABC123')
  })

  it('renders Play at /play/:sessionId', () => {
    renderAt('/play/sess-1')
    expect(screen.getByTestId('page-play')).toBeInTheDocument()
  })

  it('renders Join at /join', () => {
    renderAt('/join')
    expect(screen.getByTestId('page-join')).toBeInTheDocument()
  })
})
