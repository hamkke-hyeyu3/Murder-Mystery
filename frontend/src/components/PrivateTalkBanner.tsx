import { useSessionStore } from '@/stores/sessionStore'

interface PrivateTalkBannerProps {
  myPlayerId: string
}

export function PrivateTalkBanner({ myPlayerId }: PrivateTalkBannerProps) {
  const currentPrivateTalk = useSessionStore((s) => s.currentPrivateTalk)

  if (!currentPrivateTalk) return null

  const isParticipant = currentPrivateTalk.participants.some((p) => p.playerId === myPlayerId)
  if (isParticipant) return null
  if (currentPrivateTalk.participants.length < 2) return null

  const [a, b] = currentPrivateTalk.participants
  return (
    <div data-testid="private-talk-banner" className="rounded-lg border p-3 bg-muted text-sm text-center">
      <strong>{a.nickname}</strong>, <strong>{b.nickname}</strong> 밀담 중
    </div>
  )
}
