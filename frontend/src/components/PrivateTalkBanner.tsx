import { useSessionStore } from '@/stores/sessionStore'

export function PrivateTalkBanner() {
  const currentPrivateTalk = useSessionStore((s) => s.currentPrivateTalk)

  if (!currentPrivateTalk) return null
  if (currentPrivateTalk.participants.length < 2) return null

  const [a, b] = currentPrivateTalk.participants
  return (
    <div data-testid="private-talk-banner" className="rounded-lg border p-3 bg-muted text-sm text-center">
      <strong>{a.nickname}</strong>, <strong>{b.nickname}</strong> 밀담 중
    </div>
  )
}
