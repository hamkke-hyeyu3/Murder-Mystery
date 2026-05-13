import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { useTransientStore } from '@/stores/transientStore'
import { BannerStack } from './BannerStack'

beforeEach(() => {
  useTransientStore.getState().reset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('BannerStack', () => {
  it('배너가 없으면 아무것도 렌더하지 않는다', () => {
    render(<BannerStack />)
    expect(screen.queryByTestId('banner-stack')).toBeNull()
  })

  it('배너 2개를 push하면 메시지가 모두 표시된다', () => {
    useTransientStore.getState().pushBanner({ id: 'b1', message: '첫 번째 배너' })
    useTransientStore.getState().pushBanner({ id: 'b2', message: '두 번째 배너' })

    render(<BannerStack />)

    expect(screen.getByTestId('banner-b1')).toBeInTheDocument()
    expect(screen.getByTestId('banner-b2')).toBeInTheDocument()
    expect(screen.getByText('첫 번째 배너')).toBeInTheDocument()
    expect(screen.getByText('두 번째 배너')).toBeInTheDocument()
  })

  it('× 버튼 클릭 시 해당 배너가 사라진다', () => {
    useTransientStore.getState().pushBanner({ id: 'b1', message: '배너 A' })
    useTransientStore.getState().pushBanner({ id: 'b2', message: '배너 B' })

    render(<BannerStack />)

    fireEvent.click(screen.getByTestId('banner-b1-dismiss'))

    expect(screen.queryByTestId('banner-b1')).toBeNull()
    expect(screen.getByTestId('banner-b2')).toBeInTheDocument()
  })

  it('4초 후 배너가 자동으로 사라진다', () => {
    vi.useFakeTimers()
    useTransientStore.getState().pushBanner({ id: 'b1', message: '자동 사라짐' })

    render(<BannerStack />)
    expect(screen.getByTestId('banner-b1')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(4000)
    })

    expect(screen.queryByTestId('banner-b1')).toBeNull()
  })

  it('같은 id로 중복 push해도 배너가 하나만 표시된다', () => {
    useTransientStore.getState().pushBanner({ id: 'b1', message: '첫 push' })
    useTransientStore.getState().pushBanner({ id: 'b1', message: '중복 push' })

    render(<BannerStack />)

    const items = screen.getAllByTestId('banner-b1')
    expect(items).toHaveLength(1)
    expect(screen.getByText('첫 push')).toBeInTheDocument()
  })
})
