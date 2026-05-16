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

  useEffect(() => {
    if (!connected || !client.current || !sessionId) return

    const topicSub = client.current.subscribe(
      `/topic/session/${sessionId}/event`,
      (msg) => {
        const envelope = JSON.parse(msg.body) as SessionEvent
        if (envelope.type === 'PLAYER_JOINED') {
          const { playerId: joinedId, nickname: joinedNickname, isHost: joinedIsHost } = envelope.payload
          useSessionStore.setState((state) => {
            if (state.players.some((p) => p.playerId === joinedId)) return state
            return { ...state, players: [...state.players, { playerId: joinedId, nickname: joinedNickname, isHost: joinedIsHost }] }
          })
        } else if (envelope.type === 'PLAYER_LEFT') {
          const leftId = envelope.payload.playerId
          if (!leftId) return
          useSessionStore.setState((state) => ({
            ...state,
            players: state.players.filter((p) => p.playerId !== leftId),
            leftPlayerIds: [...state.leftPlayerIds, leftId],
          }))
        } else if (envelope.type === 'LOBBY_COUNT_CHANGED') {
          setSession({
            joinedCount: envelope.payload.joined,
            requiredCharacterCount: envelope.payload.required,
          })
        } else if (envelope.type === 'SESSION_STATE_CHANGED') {
          setSession({
            phase: 'in_progress',
            state: envelope.payload.state,
            ...(envelope.payload.turnOrder ? { turnOrder: envelope.payload.turnOrder } : {}),
          })
        } else if (envelope.type === 'TUTORIAL_ACKED') {
          const patch: Parameters<typeof setSession>[0] = {
            tutorialAckedCount: envelope.payload.acked,
            tutorialTotalCount: envelope.payload.total,
          }
          if (envelope.payload.playerId === playerId) {
            patch.myTutorialAcked = true
          }
          setSession(patch)
        } else if (envelope.type === 'SERVER_TIME_SYNC') {
          useTimerStore.getState().setServerOffset(envelope.payload.serverNow - Date.now())
        } else if (envelope.type === 'ROUND_STARTED') {
          setSession({
            state: 'round',
            roundNumber: envelope.payload.roundNumber,
            roundPrompt: envelope.payload.prompt,
            roundCommonHint: envelope.payload.commonHint,
          })
          useTimerStore.getState().setRoundDeadline(envelope.payload.deadlineAt)
        } else if (envelope.type === 'TURN_STARTED') {
          const p = envelope.payload
          setSession({
            currentTurnIndex: p.turnIndex,
            currentTurnPlayerId: p.playerId,
            currentTurnCharacterId: p.characterId,
            currentTurnDeadlineAt: p.deadlineAt,
            currentRoundCandidateLocationIds: p.candidateLocationIds,
          })
          useTimerStore.getState().setTurnDeadline(p.deadlineAt)
        } else if (envelope.type === 'LOCATION_SELECTED' || envelope.type === 'LOCATION_AUTO_SELECTED') {
          const p = envelope.payload
          useSessionStore.setState((state) => ({
            ...state,
            locationOccupancy: [
              ...state.locationOccupancy.filter((o) => o.locationId !== p.locationId),
              {
                locationId: p.locationId,
                playerId: p.playerId,
                characterId: p.characterId,
                autoSelected: envelope.type === 'LOCATION_AUTO_SELECTED',
              },
            ],
          }))
        } else if (envelope.type === 'ROUND_TURNS_COMPLETE') {
          useTimerStore.getState().setTurnDeadline(null)
          setSession({
            currentTurnIndex: null,
            currentTurnPlayerId: null,
            currentTurnCharacterId: null,
            currentTurnDeadlineAt: null,
            currentRoundCandidateLocationIds: [],
          })
        } else if (envelope.type === 'ROUND_ENDED') {
          useTransientStore.getState().pushBanner({
            id: `round-${envelope.payload.roundNumber}-ended`,
            message: `라운드 ${envelope.payload.roundNumber} 종료`,
          })
          useTimerStore.getState().setTurnDeadline(null)
          setSession({
            currentTurnIndex: null,
            currentTurnPlayerId: null,
            currentTurnCharacterId: null,
            currentTurnDeadlineAt: null,
            currentRoundCandidateLocationIds: [],
          })
        } else if (envelope.type === 'ITEM_EXCHANGED') {
          const p = envelope.payload
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
        } else if (envelope.type === 'ITEM_SHARED_FULL') {
          const p = envelope.payload
          useTransientStore.getState().pushBanner({
            id: p.actionId,
            message: `${p.actorNickname}님이 단서를 전체 공개했습니다`,
          })
        } else if (envelope.type === 'ITEM_SHARED_PARTIAL') {
          const p = envelope.payload
          useTransientStore.getState().pushBanner({
            id: p.actionId,
            message: `${p.actorNickname}님이 단서를 일부에게 공유했습니다 (총 ${p.recipients.length}명)`,
          })
        }
      }
    )

    const privateSub = client.current.subscribe(
      `/user/queue/session/${sessionId}/private`,
      (msg) => {
        const envelope = JSON.parse(msg.body) as SessionEvent
        if (envelope.type === 'CHARACTER_CARD_DEALT') {
          setCharacterCard(envelope.payload)
        } else if (envelope.type === 'OBJECTIVE_UPDATED') {
          setObjective(envelope.payload)
        } else if (envelope.type === 'CLUE_DELIVERED') {
          addClue(envelope.payload)
        }
      }
    )

    return () => {
      topicSub.unsubscribe()
      privateSub.unsubscribe()
    }
  }, [connected, sessionId, playerId, setSession, setCharacterCard, setObjective, addClue])

  const publishLeave = () => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/leave`,
      body: '',
    })
  }

  const publishSelectLocation = (locationId: string, roundNumber: number, turnIndex: number) => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/select-location`,
      body: JSON.stringify({ locationId, roundNumber, turnIndex }),
    })
  }

  const publishItemExchange = (partnerPlayerId: string, requesterClueId: string, partnerClueId: string) => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/item-exchange`,
      body: JSON.stringify({ partnerPlayerId, requesterClueId, partnerClueId }),
    })
  }

  const publishItemShareFull = (clueId: string) => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/item-share-full`,
      body: JSON.stringify({ clueId }),
    })
  }

  const publishItemSharePartial = (clueId: string, recipientPlayerIds: string[]) => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/item-share-partial`,
      body: JSON.stringify({ clueId, recipientPlayerIds }),
    })
  }

  return { connected, publishLeave, publishSelectLocation, publishItemExchange, publishItemShareFull, publishItemSharePartial }
}
