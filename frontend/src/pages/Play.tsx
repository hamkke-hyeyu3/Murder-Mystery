import { useParams } from 'react-router-dom'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { Tutorial } from '@/components/Tutorial'
import { postTutorialAck } from '@/lib/sessionApi'

export default function Play() {
  const { sessionId } = useParams<{ sessionId: string }>()

  const inviteCode = useSessionStore((s) => s.inviteCode)
  const nickname = useSessionStore((s) => s.nickname)
  const playerId = useSessionStore((s) => s.playerId)
  const state = useSessionStore((s) => s.state)
  const players = useSessionStore((s) => s.players)
  const tutorialAckedCount = useSessionStore((s) => s.tutorialAckedCount)
  const tutorialTotalCount = useSessionStore((s) => s.tutorialTotalCount)
  const myTutorialAcked = useSessionStore((s) => s.myTutorialAcked)
  const setSession = useSessionStore((s) => s.setSession)
  const characterCard = useCardStore((s) => s.characterCard)

  useSessionWebSocket({ sessionId: sessionId ?? null, inviteCode, nickname, playerId })

  const handleTutorialAck = async () => {
    if (!sessionId) return
    const response = await postTutorialAck(sessionId)
    setSession({
      tutorialAckedCount: response.acked,
      tutorialTotalCount: response.total,
      myTutorialAcked: true,
      ...(response.state === 'round' ? { state: 'round' } : {}),
    })
  }

  if (state === 'tutorial') {
    return (
      <div data-testid="page-play" className="min-h-screen">
        <Tutorial
          ackedCount={tutorialAckedCount ?? 0}
          totalCount={tutorialTotalCount ?? players.length}
          isAcked={myTutorialAcked}
          onAck={handleTutorialAck}
        />
      </div>
    )
  }

  if (state === 'round') {
    return (
      <div data-testid="page-play" className="min-h-screen p-6">
        <p data-testid="play-round-placeholder">라운드 시작 중…</p>
      </div>
    )
  }

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
