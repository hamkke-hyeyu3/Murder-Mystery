import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getResumeSession, getSession } from '@/lib/sessionApi'
import { useSessionStore } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession, ResumeResponse } from '@/types/session'

async function getStoredResumeSession(): Promise<ResumeResponse | null> {
  const raw = localStorage.getItem(LAST_SESSION_KEY)
  if (!raw) return null

  const last = JSON.parse(raw) as Partial<LastSession>
  if (
    typeof last.sessionId !== 'string' ||
    typeof last.inviteCode !== 'string' ||
    typeof last.nickname !== 'string' ||
    typeof last.playerId !== 'string' ||
    typeof last.scenarioId !== 'string'
  ) {
    return null
  }

  const view = await getSession(last.sessionId)
  if (view.inviteCode !== last.inviteCode) return null

  const me = view.players.find((p) => p.playerId === last.playerId)
  if (!me) return null

  return {
    sessionId: view.sessionId,
    inviteCode: view.inviteCode,
    scenarioId: view.scenarioId,
    phase: view.phase,
    nickname: last.nickname,
    playerId: last.playerId,
    isHost: me.isHost,
    players: view.players,
  }
}

export function useResumeSession({ skip = false }: { skip?: boolean } = {}) {
  const navigate = useNavigate()
  const setSession = useSessionStore((s) => s.setSession)

  useEffect(() => {
    if (skip) return
    let cancelled = false

    void (async () => {
      let resumed: ResumeResponse | null = null
      try {
        resumed = await getResumeSession()
      } catch {
        // Fall back to the verified local session below.
      }

      try {
        const r = resumed ?? await getStoredResumeSession()
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
      } catch {
        // No resumable session; render the entry page normally.
      }
    })()

    return () => { cancelled = true }
  }, [navigate, setSession, skip])
}
