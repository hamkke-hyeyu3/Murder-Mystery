import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getDeviceId } from '@/lib/deviceId'

interface UseStompClientOptions {
  inviteCode?: string
  nickname?: string
  playerId?: string
}

export function useStompClient({ inviteCode, nickname, playerId }: UseStompClientOptions) {
  const clientRef = useRef<Client | null>(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const headers: Record<string, string> = {}
    headers['X-Device-Id'] = getDeviceId()
    if (inviteCode) headers['X-Invite-Code'] = inviteCode
    if (nickname) headers['X-Nickname'] = nickname
    if (playerId) headers['X-Player-Id'] = playerId

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: headers,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => { if (clientRef.current === client) setConnected(true) },
      onDisconnect: () => { if (clientRef.current === client) setConnected(false) },
      onWebSocketClose: () => { if (clientRef.current === client) setConnected(false) },
    })

    clientRef.current = client
    client.activate()

    return () => {
      void client.deactivate()
      clientRef.current = null
    }
  }, [inviteCode, nickname, playerId])

  return { client: clientRef, connected }
}
