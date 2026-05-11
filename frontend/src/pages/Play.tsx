import { useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useTimerStore } from '@/stores/timerStore'
import { Tutorial } from '@/components/Tutorial'
import { RoundPanel } from '@/components/RoundPanel'
import { CharacterCard } from '@/components/CharacterCard'
import { postTutorialAck, getSession } from '@/lib/sessionApi'

export default function Play() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()

  const inviteCode = useSessionStore((s) => s.inviteCode)
  const nickname = useSessionStore((s) => s.nickname)
  const playerId = useSessionStore((s) => s.playerId)
  const state = useSessionStore((s) => s.state)
  const players = useSessionStore((s) => s.players)
  const tutorialAckedCount = useSessionStore((s) => s.tutorialAckedCount)
  const tutorialTotalCount = useSessionStore((s) => s.tutorialTotalCount)
  const myTutorialAcked = useSessionStore((s) => s.myTutorialAcked)
  const roundNumber = useSessionStore((s) => s.roundNumber)
  const roundPrompt = useSessionStore((s) => s.roundPrompt)
  const roundCommonHint = useSessionStore((s) => s.roundCommonHint)
  const setSession = useSessionStore((s) => s.setSession)
  const storeSessionId = useSessionStore((s) => s.sessionId)
  const hydrateFromSnapshot = useSessionStore((s) => s.hydrateFromSnapshot)
  const characterCard = useCardStore((s) => s.characterCard)
  const setCharacterCard = useCardStore((s) => s.setCharacterCard)
  const setObjective = useCardStore((s) => s.setObjective)
  const resetCard = useCardStore((s) => s.reset)
  const deadlineAt = useTimerStore((s) => s.deadlineAt)
  const serverOffsetMs = useTimerStore((s) => s.serverOffsetMs)
  const setDeadline = useTimerStore((s) => s.setDeadline)

  useEffect(() => {
    if (!sessionId || (storeSessionId === sessionId && state && characterCard)) return
    if (storeSessionId !== null && storeSessionId !== sessionId) resetCard()
    let cancelled = false
    getSession(sessionId)
      .then((snap) => {
        if (cancelled) return
        if (!snap.me) { navigate('/'); return }
        hydrateFromSnapshot(snap)
        if (snap.me.character) setCharacterCard(snap.me.character)
        if (snap.me.objective) setObjective(snap.me.objective)
        if (snap.round) setDeadline(snap.round.deadlineAt)
      })
      .catch(() => { if (!cancelled) navigate('/') })
    return () => { cancelled = true }
  }, [sessionId]) // eslint-disable-line react-hooks/exhaustive-deps

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
      <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
        <CharacterCard />
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
      <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
        <CharacterCard />
        <RoundPanel
          roundNumber={roundNumber ?? 1}
          prompt={roundPrompt ?? ''}
          commonHint={roundCommonHint}
          deadlineAt={deadlineAt}
          serverOffsetMs={serverOffsetMs}
        />
      </div>
    )
  }

  return (
    <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
      {characterCard ? (
        <CharacterCard />
      ) : (
        <div data-testid="play-waiting-card">
          <p>캐릭터 배정 중…</p>
        </div>
      )}
    </div>
  )
}
