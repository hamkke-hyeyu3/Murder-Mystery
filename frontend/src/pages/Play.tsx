import { useParams } from 'react-router-dom'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'

export default function Play() {
  const { sessionId } = useParams<{ sessionId: string }>()

  const inviteCode = useSessionStore((s) => s.inviteCode)
  const nickname = useSessionStore((s) => s.nickname)
  const playerId = useSessionStore((s) => s.playerId)
  const characterCard = useCardStore((s) => s.characterCard)

  useSessionWebSocket({ sessionId: sessionId ?? null, inviteCode, nickname, playerId })

  return (
    <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
      {characterCard ? (
        <div data-testid="character-card">
          <p className="text-xl font-bold">{characterCard.name}</p>
          <p className="text-sm text-muted-foreground">
            조사 순서 #{characterCard.turnOrderIndex + 1}
          </p>
        </div>
      ) : (
        <div data-testid="play-waiting-card">
          <p>캐릭터 배정 중…</p>
        </div>
      )}
    </div>
  )
}
