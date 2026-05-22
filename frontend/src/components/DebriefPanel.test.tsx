import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { DebriefPanel } from './DebriefPanel'

describe('DebriefPanel', () => {
  it('renders debrief panel with heading', () => {
    render(<DebriefPanel />)
    expect(screen.getByTestId('debrief-panel')).toBeInTheDocument()
    expect(screen.getByText(/디브리프/)).toBeInTheDocument()
  })

  it('shows survey waiting message', () => {
    render(<DebriefPanel />)
    expect(screen.getByText(/설문/)).toBeInTheDocument()
  })
})
