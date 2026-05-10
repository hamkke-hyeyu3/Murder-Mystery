import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useCountdown } from './useCountdown'

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('deadlineAt이 null이면 remainingSec=0, isWarning=false를 반환한다', () => {
    const { result } = renderHook(() => useCountdown(null, 0))
    expect(result.current.remainingSec).toBe(0)
    expect(result.current.isWarning).toBe(false)
  })

  it('deadline이 미래이면 남은 초를 반환한다', () => {
    const now = Date.now()
    const deadlineAt = now + 30_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, 0))
    expect(result.current.remainingSec).toBe(30)
  })

  it('시간이 지남에 따라 remainingSec이 감소한다', () => {
    const now = Date.now()
    const deadlineAt = now + 30_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, 0))

    act(() => { vi.advanceTimersByTime(5_000) })
    expect(result.current.remainingSec).toBe(25)
  })

  it('serverOffsetMs를 적용해 서버 시각으로 보정한다', () => {
    const now = Date.now()
    // 클라이언트 시계가 서버보다 2초 빠름 → offsetMs = -2000
    const deadlineAt = now + 30_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, -2000))
    // 실제 남은 시간 = deadlineAt - (now + (-2000)) = 30000 + 2000 = 32초
    expect(result.current.remainingSec).toBe(32)
  })

  it('remainingSec이 1~5 사이이면 isWarning=true', () => {
    const now = Date.now()
    const deadlineAt = now + 5_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, 0))
    expect(result.current.isWarning).toBe(true)
  })

  it('remainingSec이 6 이상이면 isWarning=false', () => {
    const now = Date.now()
    const deadlineAt = now + 10_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, 0))
    expect(result.current.isWarning).toBe(false)
  })

  it('deadline 경과 후 remainingSec=0, isWarning=false', () => {
    const now = Date.now()
    const deadlineAt = now + 3_000
    vi.setSystemTime(now)

    const { result } = renderHook(() => useCountdown(deadlineAt, 0))
    act(() => { vi.advanceTimersByTime(5_000) })
    expect(result.current.remainingSec).toBe(0)
    expect(result.current.isWarning).toBe(false)
  })
})
