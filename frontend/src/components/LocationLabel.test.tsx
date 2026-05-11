import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { LocationLabel } from './LocationLabel'

describe('LocationLabel', () => {
  it('icon이 있으면 icon과 이름을 함께 보인다', () => {
    render(<LocationLabel icon="📚" name="도서관 서고" />)
    expect(screen.getByTestId('location-label')).toHaveTextContent('📚')
    expect(screen.getByTestId('location-label')).toHaveTextContent('도서관 서고')
  })

  it('icon이 없으면 📍 fallback을 보인다', () => {
    render(<LocationLabel name="알 수 없는 장소" />)
    expect(screen.getByTestId('location-label')).toHaveTextContent('📍')
    expect(screen.getByTestId('location-label')).toHaveTextContent('알 수 없는 장소')
  })
})
