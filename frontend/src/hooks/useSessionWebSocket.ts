import { useEffect } from 'react'
import { useStompClient } from '@/hooks/useStompClient'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useTimerStore } from '@/stores/timerStore'
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
          useTimerStore.getState().setDeadline(envelope.payload.deadlineAt)
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
        }
      }
    )

    return () => {
      topicSub.unsubscribe()
      privateSub.unsubscribe()
    }
  }, [connected, sessionId, playerId, setSession, setCharacterCard, setObjective])

  const publishLeave = () => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/leave`,
      body: '',
    })
  }

  return { connected, publishLeave }
}
