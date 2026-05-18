import { useEffect } from 'react'
import { useTransientStore } from '@/stores/transientStore'

interface PrivateTalkInlineCardProps {
  onAccept: (requestId: string) => void
  onReject: (requestId: string) => void
}

export function PrivateTalkInlineCard({ onAccept, onReject }: PrivateTalkInlineCardProps) {
  const pending = useTransientStore((s) => s.pendingPrivateTalk)
  const setPendingPrivateTalk = useTransientStore((s) => s.setPendingPrivateTalk)

  useEffect(() => {
    if (!pending) return
    const delay = Math.max(0, pending.expiresAt - Date.now())
    const id = setTimeout(() => setPendingPrivateTalk(null), delay)
    return () => clearTimeout(id)
  }, [pending, setPendingPrivateTalk])

  if (!pending) return null

  const handleReject = () => {
    onReject(pending.requestId)
    setPendingPrivateTalk(null)
  }

  const handleAccept = () => {
    onAccept(pending.requestId)
    setPendingPrivateTalk(null)
  }

  if (pending.role === 'target') {
    return (
      <div data-testid="private-talk-incoming" className="rounded-lg border p-4 bg-muted">
        <p className="text-sm mb-3">
          <strong>{pending.partnerNickname}</strong>님이 밀담을 신청했습니다
        </p>
        <div className="flex gap-2">
          <button
            data-testid="private-talk-accept"
            onClick={handleAccept}
            className="flex-1 rounded-md border p-2 text-sm font-medium"
          >
            수락
          </button>
          <button
            data-testid="private-talk-reject"
            onClick={handleReject}
            className="flex-1 rounded-md border p-2 text-sm font-medium"
          >
            거절
          </button>
        </div>
      </div>
    )
  }

  return (
    <div data-testid="private-talk-pending" className="rounded-lg border p-4 bg-muted text-sm text-center">
      <strong>{pending.partnerNickname}</strong>님에게 밀담 신청 중…
      <span className="ml-2 text-muted-foreground">
        ({Math.max(0, Math.ceil((pending.expiresAt - Date.now()) / 1000))}초)
      </span>
    </div>
  )
}
