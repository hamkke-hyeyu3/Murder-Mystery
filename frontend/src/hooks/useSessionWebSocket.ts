import { useEffect } from 'react'
import { useStompClient } from '@/hooks/useStompClient'
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
  const wsUrl = `ws://${window.location.host}/ws`
  const { client, connected } = useStompClient({
    brokerURL: wsUrl,
    inviteCode: inviteCode ?? undefined,
    nickname: nickname ?? undefined,
    playerId: playerId ?? undefined,
  })
  const setSession = useSessionStore((s) => s.setSession)

  useEffect(() => {
    if (!connected || !client.current || !sessionId) return

    const subscription = client.current.subscribe(
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
        }
      }
    )
    return () => subscription.unsubscribe()
  }, [connected, sessionId, setSession])

  const publishLeave = () => {
    if (!client.current || !sessionId) return
    client.current.publish({
      destination: `/app/session/${sessionId}/leave`,
      body: '',
    })
  }

  return { connected, publishLeave }
}
