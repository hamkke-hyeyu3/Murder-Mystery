import { useRef } from 'react'

export function useLongPress(onLongPress: () => void, delayMs = 500) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const startXRef = useRef(0)
  const startYRef = useRef(0)

  const cancel = () => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  const onPointerDown = (e: React.PointerEvent) => {
    startXRef.current = e.clientX
    startYRef.current = e.clientY
    timerRef.current = setTimeout(onLongPress, delayMs)
  }

  const onPointerUp = () => cancel()

  const onPointerCancel = () => cancel()

  const onPointerMove = (e: React.PointerEvent) => {
    const dx = e.clientX - startXRef.current
    const dy = e.clientY - startYRef.current
    if (Math.sqrt(dx * dx + dy * dy) > 10) cancel()
  }

  const onContextMenu = (e: React.MouseEvent) => e.preventDefault()

  return { onPointerDown, onPointerUp, onPointerCancel, onPointerMove, onContextMenu }
}
