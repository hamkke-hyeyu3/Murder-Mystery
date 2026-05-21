import type { PlayerSummary } from '@/stores/sessionStore'

interface RequestPrivateTalkPanelProps {
  players: PlayerSummary[]
  myPlayerId: string | null
  leftPlayerIds: string[]
  disabled: boolean
  onRequest: (targetPlayerId: string) => void
}

export function RequestPrivateTalkPanel({
  players,
  myPlayerId,
  leftPlayerIds,
  disabled,
  onRequest,
}: RequestPrivateTalkPanelProps) {
  const targets = players.filter(
    (p) => myPlayerId !== null && p.playerId !== myPlayerId && !leftPlayerIds.includes(p.playerId)
  )

  if (targets.length === 0) return null

  return (
    <div data-testid="private-talk-request-panel" className="rounded-lg border p-4">
      <p className="text-sm font-medium mb-3">밀담 신청</p>
      <div className="flex flex-col gap-2">
        {targets.map((p) => (
          <div key={p.playerId} className="flex items-center justify-between">
            <span className="text-sm">{p.nickname}</span>
            <button
              data-testid={`private-talk-request-${p.playerId}`}
              disabled={disabled}
              onClick={() => onRequest(p.playerId)}
              className="rounded-md border px-3 py-1 text-sm disabled:opacity-40 disabled:cursor-not-allowed"
            >
              밀담
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
