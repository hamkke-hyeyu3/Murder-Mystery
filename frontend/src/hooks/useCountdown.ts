import { useEffect, useState } from 'react'

interface CountdownResult {
  remainingSec: number
  isWarning: boolean
}

export function useCountdown(
  deadlineAt: number | null,
  serverOffsetMs: number,
): CountdownResult {
  const [remainingSec, setRemainingSec] = useState(0)

  useEffect(() => {
    if (deadlineAt === null) {
      setRemainingSec(0)
      return
    }

    const tick = () => {
      const ms = deadlineAt - (Date.now() + serverOffsetMs)
      setRemainingSec(Math.max(0, Math.ceil(ms / 1000)))
    }

    tick()
    const id = setInterval(tick, 250)
    return () => clearInterval(id)
  }, [deadlineAt, serverOffsetMs])

  return { remainingSec, isWarning: remainingSec <= 5 && remainingSec > 0 }
}
