import { useCountdown } from '@/hooks/useCountdown'
import { LocationLabel } from '@/components/LocationLabel'
import { Button } from '@/components/ui/button'
import type { OccupancyView, ScenarioLocationView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

interface LocationGridProps {
  locations: ScenarioLocationView[]
  candidateLocationIds: string[]
  occupancy: OccupancyView[]
  players: PlayerSummary[]
  myPlayerId: string | null
  currentTurnPlayerId: string | null
  currentTurnIndex: number | null
  turnDeadlineAt: number | null
  serverOffsetMs: number
  onSelectLocation: (locationId: string) => void
}

export function LocationGrid({
  locations,
  candidateLocationIds,
  occupancy,
  players,
  myPlayerId,
  currentTurnPlayerId,
  currentTurnIndex,
  turnDeadlineAt,
  serverOffsetMs,
  onSelectLocation,
}: LocationGridProps) {
  const { remainingSec, isWarning } = useCountdown(turnDeadlineAt, serverOffsetMs)

  const deadlineExpired = turnDeadlineAt !== null && remainingSec === 0

  if (currentTurnIndex === null) {
    return (
      <div data-testid="location-grid-complete" className="rounded-lg border p-4 text-center text-sm text-muted-foreground">
        이번 라운드 조사 완료
      </div>
    )
  }

  const isMyTurn = currentTurnPlayerId === myPlayerId
  const currentTurnNickname = players.find((p) => p.playerId === currentTurnPlayerId)?.nickname ?? '?'

  return (
    <div data-testid="location-grid" className="rounded-lg border p-4 flex flex-col gap-3">
      <div className="flex items-center justify-between text-sm">
        {isMyTurn ? (
          <span className="font-semibold">내 차례 — 장소를 선택하세요</span>
        ) : (
          <span className="text-muted-foreground" data-testid="others-turn-label">
            {currentTurnNickname} 조사 중
          </span>
        )}
        <span
          data-testid="turn-countdown"
          className={isWarning ? 'text-destructive font-bold' : 'text-muted-foreground'}
        >
          {remainingSec !== null ? `${remainingSec}s` : '…'}
        </span>
      </div>

      <div className="grid grid-cols-3 gap-2">
        {locations.map((loc) => {
          const occupiedBy = occupancy.find((o) => o.locationId === loc.id)
          const isCandidate = candidateLocationIds.includes(loc.id)
          const isOccupied = !!occupiedBy
          const occupierNickname = occupiedBy
            ? players.find((p) => p.playerId === occupiedBy.playerId)?.nickname
            : undefined

          if (isMyTurn && !isOccupied) {
            return (
              <Button
                key={loc.id}
                variant={isCandidate ? 'default' : 'outline'}
                disabled={!isCandidate || deadlineExpired}
                data-testid={`location-cell-${loc.id}`}
                onClick={() => onSelectLocation(loc.id)}
                className="h-auto py-2 flex flex-col gap-1"
              >
                <LocationLabel icon={loc.icon} name={loc.name} />
              </Button>
            )
          }

          return (
            <div
              key={loc.id}
              data-testid={`location-cell-${loc.id}`}
              {...(occupierNickname ? { 'data-occupied-by': occupierNickname } : {})}
              className={[
                'rounded-md border p-2 flex flex-col gap-1 text-sm text-center',
                isOccupied ? 'bg-muted text-muted-foreground' : '',
              ].join(' ')}
            >
              <LocationLabel icon={loc.icon} name={loc.name} />
              {occupierNickname && (
                <span className="text-xs">{occupierNickname}</span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
