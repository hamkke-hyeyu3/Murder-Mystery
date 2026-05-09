import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getResumeSession } from '@/lib/sessionApi'
import { useSessionStore } from '@/stores/sessionStore'

export function useResumeSession({ skip = false }: { skip?: boolean } = {}) {
  const navigate = useNavigate()
  const setSession = useSessionStore((s) => s.setSession)

  useEffect(() => {
    if (skip) return
    let cancelled = false
    getResumeSession()
      .then((r) => {
        if (cancelled || !r) return
        setSession({
          sessionId: r.sessionId,
          inviteCode: r.inviteCode,
          nickname: r.nickname,
          isHost: r.isHost,
          isHostConfirmed: true,
          playerId: r.playerId,
          phase: r.phase,
          players: r.players.map((p) => ({ nickname: p.nickname, isHost: p.isHost })),
        })
        navigate(`/lobby/${r.inviteCode}`, { replace: true })
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [navigate, setSession, skip])
}
