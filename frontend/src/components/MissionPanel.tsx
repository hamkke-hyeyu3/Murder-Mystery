import { useCardStore } from '@/stores/cardStore'

export function MissionPanel() {
  const missions = useCardStore((s) => s.missions)

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
    </div>
  )
}
