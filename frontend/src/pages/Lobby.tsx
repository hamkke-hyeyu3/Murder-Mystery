import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { apiFetch } from '@/lib/api'
import { getSession } from '@/lib/sessionApi'
import { useSessionStore } from '@/stores/sessionStore'
import type { SessionState } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession } from '@/types/session'

export default function Lobby() {
  const { inviteCode } = useParams<{ inviteCode: string }>()
  const navigate = useNavigate()

  const storeInviteCode = useSessionStore((s) => s.inviteCode)
  const sessionId = useSessionStore((s) => s.sessionId)
  const nickname = useSessionStore((s) => s.nickname)
  const playerId = useSessionStore((s) => s.playerId)
  const isHost = useSessionStore((s) => s.isHost)
  const players = useSessionStore((s) => s.players)
  const requiredCharacterCount = useSessionStore((s) => s.requiredCharacterCount)
  const joinedCount = useSessionStore((s) => s.joinedCount)
  const phase = useSessionStore((s) => s.phase)
  const setSession = useSessionStore((s) => s.setSession)
  const reset = useSessionStore((s) => s.reset)

  const [startError, setStartError] = useState<string | null>(null)

  const joined = joinedCount !== null ? joinedCount : players.length

  useEffect(() => {
    if (storeInviteCode === inviteCode) return
    const raw = localStorage.getItem(LAST_SESSION_KEY)
    if (!raw) return
    try {
      const last = JSON.parse(raw) as Partial<LastSession>
      if (
        typeof last?.inviteCode === 'string' &&
        last.inviteCode === inviteCode &&
        typeof last.sessionId === 'string' &&
        typeof last.nickname === 'string' &&
        typeof last.isHost === 'boolean' &&
        typeof last.playerId === 'string'
      ) {
        setSession({
          sessionId: last.sessionId,
          inviteCode: last.inviteCode,
          nickname: last.nickname,
          isHost: last.isHost,
          isHostConfirmed: false,
          playerId: last.playerId,
          phase: 'lobby',
          requiredCharacterCount: null,
          joinedCount: null,
        })
      }
    } catch {
      // ignore malformed JSON
    }
  }, [inviteCode, storeInviteCode, setSession])

  useEffect(() => {
    if (!sessionId) return
    getSession(sessionId)
      .then((view) => {
        const state = useSessionStore.getState()
        const patch: Partial<SessionState> = {}

        if (state.requiredCharacterCount === null) {
          patch.requiredCharacterCount = view.requiredCharacterCount
          patch.joinedCount = view.joinedCount
          patch.players = view.players.map((p) => ({ playerId: p.playerId, nickname: p.nickname, isHost: p.isHost }))
        }

        if (!state.isHostConfirmed) {
          const me = view.players.find((p) => p.playerId === state.playerId)
          patch.isHost = me?.isHost ?? false
          patch.isHostConfirmed = true
        }

        if (Object.keys(patch).length > 0) setSession(patch)
      })
      .catch(() => {
        // LOBBY_COUNT_CHANGED via STOMP will populate counts when WS connects
      })
  }, [sessionId, setSession])

  useEffect(() => {
    if (phase === 'in_progress' && sessionId) {
      navigate(`/play/${sessionId}`)
    }
  }, [phase, sessionId, navigate])

  const { publishLeave } = useSessionWebSocket({ sessionId, inviteCode: storeInviteCode, nickname, playerId })

  const handleStart = async () => {
    if (!sessionId) return
    setStartError(null)
    try {
      await apiFetch(`/api/sessions/${sessionId}/start`, { method: 'POST' })
    } catch {
      setStartError('게임 시작에 실패했습니다. 다시 시도해주세요.')
    }
  }

  const handleLeave = async () => {
    publishLeave()
    await Promise.resolve()
    localStorage.removeItem(LAST_SESSION_KEY)
    reset()
    navigate('/')
  }

  return (
    <div data-testid="page-lobby" className="min-h-screen p-6 flex flex-col gap-6">
      <h1 className="text-2xl font-bold">로비</h1>
      <div>
        <p className="text-sm text-muted-foreground mb-1">초대 번호</p>
        <p className="text-5xl font-mono tracking-widest">{inviteCode}</p>
      </div>
      <section>
        <h2 className="text-lg font-semibold mb-2">합류자</h2>
        {players.length === 0 ? (
          <p className="text-sm text-muted-foreground">아직 합류자 없음</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {[...players].sort((a, b) => Number(b.isHost) - Number(a.isHost)).map((p) => (
              <li key={p.playerId} className="text-sm">
                {p.nickname}
                {p.nickname === nickname && <span className="ml-1 text-primary">(나)</span>}
                {p.isHost && <span className="ml-1 text-muted-foreground">(호스트)</span>}
              </li>
            ))}
          </ul>
        )}
      </section>
      {isHost && requiredCharacterCount !== null && joined < requiredCharacterCount && (
        <p data-testid="lobby-reason-short">{requiredCharacterCount - joined}명 더 필요</p>
      )}
      {isHost && requiredCharacterCount !== null && joined > requiredCharacterCount && (
        <p data-testid="lobby-reason-excess">
          {joined - requiredCharacterCount}명 초과 — 누군가 나가야 합니다
        </p>
      )}
      {isHost && (
        <>
          <Button
            disabled={requiredCharacterCount === null || joined !== requiredCharacterCount}
            onClick={handleStart}
          >
            게임 시작
          </Button>
          {startError && <p role="alert" className="text-sm text-destructive">{startError}</p>}
        </>
      )}
      {!isHost && (
        <Button variant="outline" onClick={handleLeave}>
          나가기
        </Button>
      )}
    </div>
  )
}
