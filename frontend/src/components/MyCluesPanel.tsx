import { useCardStore } from '@/stores/cardStore'
import type { ClueView } from '@/types/session'

function groupByRound(clues: ClueView[]): Map<number, ClueView[]> {
  const map = new Map<number, ClueView[]>()
  for (const clue of clues) {
    const list = map.get(clue.roundNumberDiscovered) ?? []
    list.push(clue)
    map.set(clue.roundNumberDiscovered, list)
  }
  return map
}

export function MyCluesPanel() {
  const clues = useCardStore((s) => s.clues)

  if (clues.length === 0) {
    return (
      <div data-testid="my-clues-panel" className="rounded-lg border p-4 text-sm text-muted-foreground">
        아직 발견한 단서가 없습니다.
      </div>
    )
  }

  const grouped = groupByRound(clues)
  const rounds = Array.from(grouped.keys()).sort((a, b) => a - b)

  return (
    <div data-testid="my-clues-panel" className="rounded-lg border p-4 flex flex-col gap-4">
      {rounds.map((round) => (
        <section key={round} data-testid={`clue-section-round-${round}`}>
          <h3 className="font-semibold text-sm mb-2">라운드 {round}</h3>
          <ul className="flex flex-col gap-1">
            {grouped.get(round)!.map((clue) => (
              <li key={clue.id} data-testid={`clue-item-${clue.id}`} className="text-sm">
                {clue.title}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
