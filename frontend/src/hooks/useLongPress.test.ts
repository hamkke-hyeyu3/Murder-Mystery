import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useLongPress } from './useLongPress'

describe('useLongPress', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('500ms 유지 후 onLongPress 가 호출된다', () => {
    const onLongPress = vi.fn()
    const { result } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
    })
    expect(onLongPress).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(500)
    })
    expect(onLongPress).toHaveBeenCalledTimes(1)
  })

  it('300ms 후 pointerUp 하면 onLongPress 가 호출되지 않는다', () => {
    const onLongPress = vi.fn()
    const { result } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
      vi.advanceTimersByTime(300)
      result.current.onPointerUp()
      vi.advanceTimersByTime(500)
    })
    expect(onLongPress).not.toHaveBeenCalled()
  })

  it('pointerMove 가 10px 초과하면 취소된다', () => {
    const onLongPress = vi.fn()
    const { result } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
      result.current.onPointerMove({ clientX: 115, clientY: 100 } as React.PointerEvent)
      vi.advanceTimersByTime(500)
    })
    expect(onLongPress).not.toHaveBeenCalled()
  })

  it('pointerCancel 이면 취소된다', () => {
    const onLongPress = vi.fn()
    const { result } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
      result.current.onPointerCancel()
      vi.advanceTimersByTime(500)
    })
    expect(onLongPress).not.toHaveBeenCalled()
  })

  it('onContextMenu 이벤트에서 e.preventDefault가 호출된다', () => {
    const { result } = renderHook(() => useLongPress(vi.fn()))
    const e = { preventDefault: vi.fn() } as unknown as React.MouseEvent
    result.current.onContextMenu(e)
    expect(e.preventDefault).toHaveBeenCalledTimes(1)
  })

  it('pointerLeave 이면 취소된다', () => {
    const onLongPress = vi.fn()
    const { result } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
      result.current.onPointerLeave()
      vi.advanceTimersByTime(500)
    })
    expect(onLongPress).not.toHaveBeenCalled()
  })

  it('언마운트 시 보류 중인 타이머가 취소되어 stale 콜백이 호출되지 않는다', () => {
    const onLongPress = vi.fn()
    const { result, unmount } = renderHook(() => useLongPress(onLongPress))

    act(() => {
      result.current.onPointerDown({ clientX: 100, clientY: 100 } as React.PointerEvent)
    })
    unmount()
    act(() => {
      vi.advanceTimersByTime(600)
    })
    expect(onLongPress).not.toHaveBeenCalled()
  })
})
