import { useEffect } from 'react'
import { useStompClient } from '@/hooks/useStompClient'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useTimerStore } from '@/stores/timerStore'
import { useTransientStore } from '@/stores/transientStore'
import type { SessionEvent } from '@/types/session'

interface UseSessionWebSocketOptions {
  sessionId: string | null
  inviteCode: string | null
  nickname: string | null
  playerId: string | null
}

type EventHandlers = {
  [K in SessionEvent['type']]?: (e: Extract<SessionEvent, { type: K }>) => void
}

export function useSessionWebSocket({
  sessionId,
  inviteCode,
  nickname,
  playerId,
}: UseSessionWebSocketOptions) {
  const { client, connected } = useStompClient({
    inviteCode: inviteCode ?? undefined,
    nickname: nickname ?? undefined,
    playerId: playerId ?? undefined,
  })
  const setSession = useSessionStore((s) => s.setSession)
  const setCharacterCard = useCardStore((s) => s.setCharacterCard)
  const setObjective = useCardStore((s) => s.setObjective)
  const addClue = useCardStore((s) => s.addClue)
  const setMissions = useCardStore((s) => s.setMissions)

  useEffect(() => {
    if (!connected || !client.current || !client.current.connected || !sessionId) return

    const handleLocationOccupancy = (
      payload: { locationId: string; playerId: string; characterId: string; roundNumber: number },
      autoSelected: boolean
    ) => {
      useSessionStore.setState((state) => {
        // 재연결 등으로 이전 라운드 이벤트가 늦게 도달한 경우 무시
        if (payload.roundNumber !== state.roundNumber) return state
        return {
          ...state,
          locationOccupancy: [
            ...state.locationOccupancy.filter((o) => o.locationId !== payload.locationId),
            {
              locationId: payload.locationId,
              playerId: payload.playerId,
              characterId: payload.characterId,
              autoSelected,
            },
          ],
        }
      })
    }

    const topicHandlers: EventHandlers = {
      PLAYER_JOINED: (e) => {
        const { playerId: joinedId, nickname: joinedNickname, isHost: joinedIsHost } = e.payload
        useSessionStore.setState((state) => {
          if (state.players.some((p) => p.playerId === joinedId)) return state
          return {
            ...state,
            players: [...state.players, { playerId: joinedId, nickname: joinedNickname, isHost: joinedIsHost }],
          }
        })
      },
      PLAYER_LEFT: (e) => {
        const leftId = e.payload.playerId
        if (!leftId) return
        useSessionStore.setState((state) => ({
          ...state,
          players: state.players.filter((p) => p.playerId !== leftId),
          leftPlayerIds: [...state.leftPlayerIds, leftId],
        }))
      },
      LOBBY_COUNT_CHANGED: (e) => {
        setSession({ joinedCount: e.payload.joined, requiredCharacterCount: e.payload.required })
      },
      SESSION_STATE_CHANGED: (e) => {
        const patch: Parameters<typeof setSession>[0] = {
          phase: 'in_progress',
          state: e.payload.state,
          ...(e.payload.turnOrder ? { turnOrder: e.payload.turnOrder } : {}),
        }
        if (e.payload.state === 'ending') patch.forceProgressAvailable = null
        setSession(patch)
      },
      TUTORIAL_ACKED: (e) => {
        const patch: Parameters<typeof setSession>[0] = {
          tutorialAckedCount: e.payload.acked,
          tutorialTotalCount: e.payload.total,
        }
        if (e.payload.playerId === playerId) patch.myTutorialAcked = true
        setSession(patch)
      },
      SERVER_TIME_SYNC: (e) => {
        useTimerStore.getState().setServerOffset(e.payload.serverNow - Date.now())
      },
      ROUND_STARTED: (e) => {
        useSessionStore.setState((s) => ({
          ...s,
          state: 'round',
          roundNumber: e.payload.roundNumber,
          roundPrompt: e.payload.prompt,
          roundCommonHint: e.payload.commonHint,
          // 실제로 새 라운드로 전진할 때만 점유 초기화 (재연결로 중복 수신 방어)
          ...(e.payload.roundNumber !== s.roundNumber ? { locationOccupancy: [] } : {}),
        }))
        useTimerStore.getState().setRoundDeadline(e.payload.deadlineAt)
      },
      TURN_STARTED: (e) => {
        const p = e.payload
        setSession({
          currentTurnIndex: p.turnIndex,
          currentTurnPlayerId: p.playerId,
          currentTurnCharacterId: p.characterId,
          currentTurnDeadlineAt: p.deadlineAt,
          currentRoundCandidateLocationIds: p.candidateLocationIds,
        })
        useTimerStore.getState().setTurnDeadline(p.deadlineAt)
      },
      LOCATION_SELECTED: (e) => handleLocationOccupancy(e.payload, false),
      LOCATION_AUTO_SELECTED: (e) => handleLocationOccupancy(e.payload, true),
      ROUND_TURNS_COMPLETE: () => {
        useTimerStore.getState().setTurnDeadline(null)
        setSession({
          currentTurnIndex: null,
          currentTurnPlayerId: null,
          currentTurnCharacterId: null,
          currentTurnDeadlineAt: null,
          currentRoundCandidateLocationIds: [],
        })
      },
      ROUND_ENDED: (e) => {
        useTransientStore.getState().pushBanner({
          id: `round-${e.payload.roundNumber}-ended`,
          message: `라운드 ${e.payload.roundNumber} 종료`,
        })
        useTimerStore.getState().setTurnDeadline(null)
        setSession({
          currentTurnIndex: null,
          currentTurnPlayerId: null,
          currentTurnCharacterId: null,
          currentTurnDeadlineAt: null,
          currentRoundCandidateLocationIds: [],
        })
      },
      ITEM_EXCHANGED: (e) => {
        const p = e.payload
        useTransientStore.getState().pushBanner({
          id: p.actionId,
          message: `${p.actorNickname}님과 ${p.partnerNickname}님이 단서를 교환했습니다`,
        })
        useCardStore.setState((state) => {
          if (state.ownedClues.length === 0) return state
          return {
            ownedClues: state.ownedClues.map((c) => {
              if (c.id === p.actorClueId) return { ...c, ownerPlayerId: p.partnerPlayerId }
              if (c.id === p.partnerClueId) return { ...c, ownerPlayerId: p.actorPlayerId }
              return c
            }),
          }
        })
      },
      ITEM_SHARED_FULL: (e) => {
        const p = e.payload
        useTransientStore.getState().pushBanner({
          id: p.actionId,
          message: `${p.actorNickname}님이 단서를 전체 공개했습니다`,
        })
      },
      ITEM_SHARED_PARTIAL: (e) => {
        const p = e.payload
        useTransientStore.getState().pushBanner({
          id: p.actionId,
          message: `${p.actorNickname}님이 단서를 일부에게 공유했습니다 (총 ${p.recipients.length}명)`,
        })
      },
      PRIVATE_TALK_STARTED: (e) => {
        const p = e.payload
        useSessionStore.setState({ currentPrivateTalk: { requestId: p.requestId, participants: p.participants, startedAt: p.startedAt } })
        useTransientStore.getState().setPendingPrivateTalk(null)
      },
      PRIVATE_TALK_ENDED: (_e) => {
        useSessionStore.setState({ currentPrivateTalk: null })
      },
      VOTE_STARTED: (e) => {
        const p = e.payload
        useSessionStore.setState((state) => ({
          ...state,
          vote: {
            ...(state.vote ?? { myVote: null, outcome: null, winnerCharacterId: null, tiedCharacterIds: null, tally: null }),
            roundNo: p.roundNo,
            deadlineAt: p.deadlineAt,
            candidates: p.candidates,
            submittedCount: 0,
            totalCount: state.players.length,
          },
        }))
      },
      VOTE_PROGRESS: (e) => {
        const p = e.payload
        useSessionStore.setState((state) => {
          if (state.vote == null) return state
          return { ...state, vote: { ...state.vote, submittedCount: p.submittedCount, totalCount: p.totalCount } }
        })
      },
      VOTE_RESULT: (e) => {
        const p = e.payload
        useSessionStore.setState((state) => {
          if (state.vote == null) return state
          return {
            ...state,
            vote: {
              ...state.vote,
              outcome: p.outcome,
              winnerCharacterId: p.winnerCharacterId,
              tiedCharacterIds: p.tiedCharacterIds,
              tally: p.tally,
            },
          }
        })
      },
      RUNOFF_STARTED: (e) => {
        const p = e.payload
        useSessionStore.setState((state) => {
          if (state.vote == null) return state
          return {
            ...state,
            vote: {
              ...state.vote,
              roundNo: p.roundNo,
              deadlineAt: p.deadlineAt,
              candidates: p.candidates,
              submittedCount: 0,
              myVote: null,
              outcome: null,
              tiedCharacterIds: null,
              tally: null,
            },
          }
        })
      },
      CULPRIT_REVEAL_STARTED: (e) => setSession({ reveal: e.payload }),
      MISSION_PHASE_STARTED: () => {},
      MISSION_CHECK_COMPLETE: (e) => {
        const patch: Parameters<typeof setSession>[0] = {
          missionCheckedCount: e.payload.checkedCount,
          missionTotalCount: e.payload.totalCount,
        }
        if (e.payload.playerId === playerId) patch.myMissionChecked = true
        setSession(patch)
      },
      FORCE_PROGRESS_AVAILABLE: (e) => {
        setSession({ forceProgressAvailable: e.payload })
      },
      ENDING_STARTED: () => {},
      DEBRIEF_STARTED: () => {},
      SURVEY_AVAILABLE: () => {},
    }

    const privateHandlers: EventHandlers = {
      CHARACTER_CARD_DEALT: (e) => setCharacterCard(e.payload),
      OBJECTIVE_UPDATED: (e) => setObjective(e.payload),
      CLUE_DELIVERED: (e) => {
        addClue(e.payload)
        if (e.payload.source === 'location' && playerId) {
          const { id, itemId, title, roundNumberDiscovered } = e.payload
          useCardStore.setState((state) => {
            if (state.ownedClues.some((c) => c.id === id)) return state
            return {
              ownedClues: [...state.ownedClues, { id, itemId, title, ownerPlayerId: playerId, roundNumberDiscovered }],
            }
          })
        }
      },
      PRIVATE_TALK_REQUESTED: (e) => {
        const p = e.payload
        const role = p.requesterPlayerId === playerId ? 'requester' : 'target'
        const partnerPlayerId = role === 'requester' ? p.targetPlayerId : p.requesterPlayerId
        const partnerNickname = role === 'requester' ? p.targetNickname : p.requesterNickname
        useTransientStore.getState().setPendingPrivateTalk({
          requestId: p.requestId,
          partnerPlayerId,
          partnerNickname,
          role,
          expiresAt: p.expiresAt,
        })
      },
      MISSION_REVEALED: (e) => setMissions(e.payload.missions),
    }

    const dispatch = (handlers: EventHandlers, envelope: SessionEvent) => {
      const handler = (handlers as Record<string, (e: SessionEvent) => void>)[envelope.type]
      handler?.(envelope)
    }

    const topicSub = client.current.subscribe(
      `/topic/session/${sessionId}/event`,
      (msg) => dispatch(topicHandlers, JSON.parse(msg.body) as SessionEvent)
    )

    const privateSub = client.current.subscribe(
      `/user/queue/session/${sessionId}/private`,
      (msg) => dispatch(privateHandlers, JSON.parse(msg.body) as SessionEvent)
    )

    const safeUnsubscribe = (sub: { unsubscribe: () => void }) => {
      try { sub.unsubscribe() } catch (e) {
        if (process.env.NODE_ENV !== 'production') console.error('[ws] unsubscribe failed', e)
      }
    }

    return () => {
      safeUnsubscribe(topicSub)
      safeUnsubscribe(privateSub)
    }
  }, [connected, sessionId, playerId, setSession, setCharacterCard, setObjective, addClue])

  const sendToSession = (path: string, body: string = '') => {
    if (!client.current?.connected || !sessionId) return
    client.current.publish({ destination: `/app/session/${sessionId}/${path}`, body })
  }

  const publishLeave = () => sendToSession('leave')

  const publishSelectLocation = (locationId: string, roundNumber: number, turnIndex: number) =>
    sendToSession('select-location', JSON.stringify({ locationId, roundNumber, turnIndex }))

  const publishItemExchange = (partnerPlayerId: string, requesterClueId: string, partnerClueId: string) =>
    sendToSession('item-exchange', JSON.stringify({ partnerPlayerId, requesterClueId, partnerClueId }))

  const publishItemShareFull = (clueId: string) =>
    sendToSession('item-share-full', JSON.stringify({ clueId }))

  const publishItemSharePartial = (clueId: string, recipientPlayerIds: string[]) =>
    sendToSession('item-share-partial', JSON.stringify({ clueId, recipientPlayerIds }))

  const publishPrivateTalkRequest = (targetPlayerId: string) =>
    sendToSession('private-talk/request', JSON.stringify({ targetPlayerId }))

  const publishPrivateTalkAccept = (requestId: string) =>
    sendToSession('private-talk/accept', JSON.stringify({ requestId }))

  const publishPrivateTalkReject = (requestId: string) =>
    sendToSession('private-talk/reject', JSON.stringify({ requestId }))

  const publishPrivateTalkEnd = (requestId: string) =>
    sendToSession('private-talk/end', JSON.stringify({ requestId }))

  const publishVoteSubmit = (targetCharacterId: string, roundNo: number) =>
    sendToSession('vote-submit', JSON.stringify({ targetCharacterId, roundNo }))

  const publishMissionCheckComplete = () =>
    sendToSession('mission/check-complete')

  const publishForceProgress = () =>
    sendToSession('host/force-progress')

  return {
    connected,
    publishLeave,
    publishSelectLocation,
    publishItemExchange,
    publishItemShareFull,
    publishItemSharePartial,
    publishPrivateTalkRequest,
    publishPrivateTalkAccept,
    publishPrivateTalkReject,
    publishPrivateTalkEnd,
    publishVoteSubmit,
    publishMissionCheckComplete,
    publishForceProgress,
  }
}
