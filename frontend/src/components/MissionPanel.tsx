import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'

interface MissionPanelProps {
  onCheckComplete: () => void
  onForceProgress: () => void
}

export function MissionPanel({ onCheckComplete, onForceProgress }: MissionPanelProps) {
  const missions = useCardStore((s) => s.missions)
  const myMissionChecked = useSessionStore((s) => s.myMissionChecked)
  const checkedCount = useSessionStore((s) => s.missionCheckedCount)
  const totalCount = useSessionStore((s) => s.missionTotalCount)
  const isHost = useSessionStore((s) => s.isHost)
  const forceProgressAvailable = useSessionStore((s) => s.forceProgressAvailable)

  const showForceProgress =
    forceProgressAvailable != null &&
    (forceProgressAvailable.scope === 'all' || (forceProgressAvailable.scope === 'host' && isHost))

  if (!missions) {
    return (
      <div data-testid="mission-panel" className="p-6">
        <p className="text-muted-foreground">미션 준비 중…</p>
      </div>
    )
  }

  return (
    <div data-testid="mission-panel" className="flex flex-col gap-4 p-6">
      <h2 className="text-xl font-bold">나의 미션</h2>
      <ul className="flex flex-col gap-3">
        {missions.map((m) => (
          <li key={m.label} className="rounded-lg border p-4">
            <p className="font-semibold" data-testid={`mission-label-${m.label}`}>{m.label}</p>
            <p className="text-sm text-muted-foreground mt-1">{m.description}</p>
          </li>
        ))}
      </ul>
      <div className="flex items-center gap-4">
        <button
          data-testid="mission-check-button"
          onClick={onCheckComplete}
          disabled={myMissionChecked}
          className="px-4 py-2 rounded bg-primary text-primary-foreground disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {myMissionChecked ? '✓ 체크 완료' : '체크 완료'}
        </button>
        {checkedCount != null && totalCount != null && (
          <span data-testid="mission-check-count" className="text-sm text-muted-foreground">
            {checkedCount} / {totalCount} 완료
          </span>
        )}
      </div>
      {showForceProgress && (
        <button
          data-testid="force-progress-button"
          onClick={onForceProgress}
          className="mt-2 px-4 py-2 rounded bg-destructive text-destructive-foreground"
        >
          강제 진행
        </button>
      )}
    </div>
  )
}
