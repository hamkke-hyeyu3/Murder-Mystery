import { useEffect } from 'react'
import { useStompClient } from '@/hooks/useStompClient'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
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

  useEffect(() => {
    if (!connected || !client.current || !sessionId) return

    const topicSub = client.current.subscribe(
      `/topic/session/${sessionId}/event`,
      (msg) => {
        const envelope = JSON.parse(msg.body) as SessionEvent
        if (envelope.type === 'PLAYER_JOINED') {
          const current = useSessionStore.getState().players
          setSession({
            players: [
              ...current,
              { nickname: envelope.payload.nickname, isHost: envelope.payload.isHost },
            ],
          })
        } else if (envelope.type === 'PLAYER_LEFT') {
          const current = useSessionStore.getState().players
          setSession({
            players: current.filter((p) => p.nickname !== envelope.payload.nickname),
          })
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
        }
      }
    )

    const privateSub = client.current.subscribe(
      `/user/queue/session/${sessionId}/private`,
      (msg) => {
        const envelope = JSON.parse(msg.body) as SessionEvent
        if (envelope.type === 'CHARACTER_CARD_DEALT') {
          setCharacterCard(envelope.payload)
        }
      }
    )

    return () => {
      topicSub.unsubscribe()
      privateSub.unsubscribe()
    }
  }, [connected, sessionId, playerId, setSession, setCharacterCard])

  const publishLeave = () => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/leave`,
      body: '',
    })
  }

  return { connected, publishLeave }
}
