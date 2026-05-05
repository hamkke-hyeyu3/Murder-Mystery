import { useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession } from '@/types/session'

export default function Lobby() {
  const { inviteCode } = useParams<{ inviteCode: string }>()
  const storeInviteCode = useSessionStore((s) => s.inviteCode)
  const setSession = useSessionStore((s) => s.setSession)

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

  return (
    <div data-testid="page-lobby" className="min-h-screen p-6 flex flex-col gap-6">
      <h1 className="text-2xl font-bold">로비</h1>
      <div>
        <p className="text-sm text-muted-foreground mb-1">초대 번호</p>
        <p className="text-5xl font-mono tracking-widest">{inviteCode}</p>
      </div>
      <section>
        <h2 className="text-lg font-semibold mb-2">합류자</h2>
        <p className="text-sm text-muted-foreground">아직 합류자 없음</p>
      </section>
      <Button disabled>게임 시작</Button>
    </div>
  )
}
