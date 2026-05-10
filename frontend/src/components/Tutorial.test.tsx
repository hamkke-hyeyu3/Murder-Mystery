import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Tutorial } from './Tutorial'

function renderTutorial(overrides: Partial<React.ComponentProps<typeof Tutorial>> = {}) {
  const onAck = vi.fn().mockResolvedValue(undefined)
  render(
    <Tutorial
      ackedCount={0}
      totalCount={3}
      isAcked={false}
      onAck={onAck}
      {...overrides}
    />
  )
  return { onAck }
}

describe('Tutorial', () => {
  it('거짓말 정책 고정 문구를 표시한다', () => {
    renderTutorial()
    expect(screen.getByText(/전원 거짓말 가능/)).toBeInTheDocument()
  })

  it('기본 규칙 안내를 표시한다', () => {
    renderTutorial()
    expect(screen.getByText(/라운드마다 장소를 조사/)).toBeInTheDocument()
    expect(screen.getByText(/투표로 범인을 지목/)).toBeInTheDocument()
  })

  it('"확인" 버튼 클릭 시 onAck을 호출한다', () => {
    const { onAck } = renderTutorial()
    fireEvent.click(screen.getByTestId('tutorial-confirm'))
    expect(onAck).toHaveBeenCalledOnce()
  })

  it('isAcked=true일 때 버튼이 비활성화된다', () => {
    renderTutorial({ isAcked: true, ackedCount: 1, totalCount: 3 })
    expect(screen.getByTestId('tutorial-confirm')).toBeDisabled()
  })

  it('isAcked=true일 때 대기 텍스트를 표시한다', () => {
    renderTutorial({ isAcked: true, ackedCount: 1, totalCount: 3 })
    expect(screen.getByTestId('tutorial-wait')).toBeInTheDocument()
    expect(screen.getByText(/1 \/ 3 통과/)).toBeInTheDocument()
  })

  it('ackedCount/totalCount 변화에 따라 대기 텍스트가 바뀐다', () => {
    renderTutorial({ isAcked: true, ackedCount: 3, totalCount: 3 })
    expect(screen.getByText(/3 \/ 3 통과/)).toBeInTheDocument()
  })
})
