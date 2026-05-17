import { useEffect, useLayoutEffect, useRef } from 'react'

export function useLongPress(onLongPress: () => void, delayMs = 500) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const onLongPressRef = useRef(onLongPress)

  useLayoutEffect(() => {
    onLongPressRef.current = onLongPress
  })

  const cancel = () => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  // Clear any pending timer on unmount to prevent calling stale callbacks.
  useEffect(() => () => cancel(), [])

  const onPointerDown = (e: React.PointerEvent) => {
    startXRef.current = e.clientX
    startYRef.current = e.clientY
    // Capture the pointer so we receive leave/cancel even if the pointer moves outside.
    if (e.pointerType === 'touch') e.currentTarget.setPointerCapture(e.pointerId)
    timerRef.current = setTimeout(() => onLongPressRef.current(), delayMs)
  }

  const onPointerUp = () => cancel()

  const onPointerCancel = () => cancel()

  const onPointerLeave = () => cancel()

  const onPointerMove = (e: React.PointerEvent) => {
    const dx = e.clientX - startXRef.current
    const dy = e.clientY - startYRef.current
    if (Math.sqrt(dx * dx + dy * dy) > 10) cancel()
  }

  const onContextMenu = (e: React.MouseEvent) => e.preventDefault()

  return { onPointerDown, onPointerUp, onPointerCancel, onPointerLeave, onPointerMove, onContextMenu }
}
