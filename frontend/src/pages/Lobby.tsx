import { useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useSessionStore } from '@/stores/sessionStore'
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
  const setSession = useSessionStore((s) => s.setSession)
  const reset = useSessionStore((s) => s.reset)

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
          playerId: last.playerId,
          phase: 'lobby',
        })
      }
    } catch {
      // ignore malformed JSON
    }
  }, [inviteCode, storeInviteCode, setSession])

  const { publishLeave } = useSessionWebSocket({ sessionId, inviteCode: storeInviteCode, nickname, playerId })

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
            {players.map((p) => (
              <li key={p.nickname} className="text-sm">
                {p.nickname}
                {p.isHost && <span className="ml-1 text-muted-foreground">(호스트)</span>}
              </li>
            ))}
          </ul>
        )}
      </section>
      <Button disabled>게임 시작</Button>
      {!isHost && (
        <Button variant="outline" onClick={handleLeave}>
          나가기
        </Button>
      )}
    </div>
  )
}
