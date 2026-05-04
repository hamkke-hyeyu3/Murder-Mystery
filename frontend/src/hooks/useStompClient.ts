import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

interface UseStompClientOptions {
  brokerURL: string
  inviteCode?: string
  nickname?: string
}

export function useStompClient({ brokerURL, inviteCode, nickname }: UseStompClientOptions) {
  const clientRef = useRef<Client | null>(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const headers: Record<string, string> = {}
    if (inviteCode) headers['X-Invite-Code'] = inviteCode
    if (nickname) headers['X-Nickname'] = nickname

    const client = new Client({
      brokerURL,
      connectHeaders: headers,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    })

    clientRef.current = client
    client.activate()

    return () => {
      void client.deactivate()
      clientRef.current = null
    }
  }, [brokerURL, inviteCode, nickname])

  return { client: clientRef, connected }
}
