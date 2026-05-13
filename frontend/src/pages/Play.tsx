import { useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useTimerStore } from '@/stores/timerStore'
import { Tutorial } from '@/components/Tutorial'
import { RoundPanel } from '@/components/RoundPanel'
import { CharacterCard } from '@/components/CharacterCard'
import { LocationGrid } from '@/components/LocationGrid'
import { MyCluesPanel } from '@/components/MyCluesPanel'
import { VotePlaceholder } from '@/components/VotePlaceholder'
import { BannerStack } from '@/components/BannerStack'
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
  const setClues = useCardStore((s) => s.setClues)
  const resetCard = useCardStore((s) => s.reset)
  const currentTurnIndex = useSessionStore((s) => s.currentTurnIndex)
  const currentTurnPlayerId = useSessionStore((s) => s.currentTurnPlayerId)
  const currentRoundCandidateLocationIds = useSessionStore((s) => s.currentRoundCandidateLocationIds)
  const locationOccupancy = useSessionStore((s) => s.locationOccupancy)
  const scenarioLocations = useSessionStore((s) => s.scenarioLocations)
  const roundDeadlineAt = useTimerStore((s) => s.roundDeadlineAt)
  const turnDeadlineAt = useTimerStore((s) => s.turnDeadlineAt)
  const serverOffsetMs = useTimerStore((s) => s.serverOffsetMs)
  const setRoundDeadline = useTimerStore((s) => s.setRoundDeadline)

  // storeSessionId가 다른 세션을 가리키면 store 값을 사용하지 않음 (stale 노출 방지)
  const sessionMatches = storeSessionId === null || storeSessionId === sessionId
  const effectiveState = sessionMatches ? state : null
  const effectiveCard = sessionMatches ? characterCard : null

  useEffect(() => {
    if (!sessionId || (sessionMatches && effectiveState && effectiveCard)) return
    if (storeSessionId !== null && storeSessionId !== sessionId) resetCard()
    let cancelled = false
    getSession(sessionId)
      .then((snap) => {
        if (cancelled) return
        if (!snap.me) { navigate('/'); return }
        hydrateFromSnapshot(snap)
        if (snap.me.character) setCharacterCard(snap.me.character)
        if (snap.me.objective) setObjective(snap.me.objective)
        if (snap.me.myClues !== undefined) setClues(snap.me.myClues)
        if (snap.round) setRoundDeadline(snap.round.deadlineAt)
        if (snap.currentTurn) useTimerStore.getState().setTurnDeadline(snap.currentTurn.deadlineAt)
      })
      .catch(() => { if (!cancelled) navigate('/') })
    return () => { cancelled = true }
  }, [sessionId]) // eslint-disable-line react-hooks/exhaustive-deps

  // publishItemExchange/ShareFull/SharePartial은 S7에서 액션 시트 UI 연결 예정
  const { publishSelectLocation } = useSessionWebSocket({ sessionId: sessionId ?? null, inviteCode, nickname, playerId })

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

  if (effectiveState === 'vote') {
    return (
      <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
        <BannerStack />
        <VotePlaceholder />
      </div>
    )
  }

  if (effectiveState === 'tutorial') {
    return (
      <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
        <BannerStack />
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

  if (effectiveState === 'round') {
    return (
      <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
        <BannerStack />
        <CharacterCard />
        <RoundPanel
          roundNumber={roundNumber ?? 1}
          prompt={roundPrompt ?? ''}
          commonHint={roundCommonHint}
          deadlineAt={roundDeadlineAt}
          serverOffsetMs={serverOffsetMs}
        />
        <MyCluesPanel />
        <LocationGrid
          locations={scenarioLocations}
          candidateLocationIds={currentRoundCandidateLocationIds}
          occupancy={locationOccupancy}
          players={players}
          myPlayerId={playerId}
          currentTurnPlayerId={currentTurnPlayerId}
          currentTurnIndex={currentTurnIndex}
          turnDeadlineAt={turnDeadlineAt}
          serverOffsetMs={serverOffsetMs}
          onSelectLocation={(locationId) =>
            publishSelectLocation(locationId, roundNumber ?? 1, currentTurnIndex ?? 0)
          }
        />
      </div>
    )
  }

  return (
    <div data-testid="page-play" className="min-h-screen p-6 flex flex-col gap-6">
      <BannerStack />
      {effectiveCard ? (
        <CharacterCard />
      ) : (
        <div data-testid="play-waiting-card">
          <p>캐릭터 배정 중…</p>
        </div>
      )}
    </div>
  )
}
