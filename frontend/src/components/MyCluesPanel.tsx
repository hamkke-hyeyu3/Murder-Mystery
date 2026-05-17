import { useEffect, useState } from 'react'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import { useLongPress } from '@/hooks/useLongPress'
import { ClueActionSheet } from '@/components/ClueActionSheet'
import type { ClueView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

interface MyCluesPanelProps {
  players: PlayerSummary[]
  myPlayerId: string | null
  onExchange: (partnerPlayerId: string, requesterClueId: string, partnerClueId: string) => void
  onShareFull: (clueId: string) => void
  onSharePartial: (clueId: string, recipientPlayerIds: string[]) => void
}

function groupByRound(clues: ClueView[]): Map<number, ClueView[]> {
  const map = new Map<number, ClueView[]>()
  for (const clue of clues) {
    const list = map.get(clue.roundNumberDiscovered) ?? []
    list.push(clue)
    map.set(clue.roundNumberDiscovered, list)
  }
  return map
}

function ClueItem({
  clue,
  onLongPress,
}: {
  clue: ClueView
  onLongPress: () => void
}) {
  const longPress = useLongPress(onLongPress)
  return (
    <li
      data-testid={`clue-item-${clue.id}`}
      className="text-sm select-none cursor-pointer"
      {...longPress}
    >
      {clue.title}
    </li>
  )
}

export function MyCluesPanel({ players, myPlayerId, onExchange, onShareFull, onSharePartial }: MyCluesPanelProps) {
  const clues = useCardStore((s) => s.clues)
  const ownedClues = useCardStore((s) => s.ownedClues)
  const roundNumber = useSessionStore((s) => s.roundNumber)
  const [activeClue, setActiveClue] = useState<ClueView | null>(null)

  useEffect(() => {
    setActiveClue(null)
  }, [roundNumber])

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
    <>
      <div data-testid="my-clues-panel" className="rounded-lg border p-4 flex flex-col gap-4">
        {rounds.map((round) => (
          <section key={round} data-testid={`clue-section-round-${round}`}>
            <h3 className="font-semibold text-sm mb-2">라운드 {round}</h3>
            <ul className="flex flex-col gap-1">
              {grouped.get(round)!.map((clue) => (
                <ClueItem
                  key={clue.id}
                  clue={clue}
                  onLongPress={() => setActiveClue(clue)}
                />
              ))}
            </ul>
          </section>
        ))}
      </div>
      {activeClue && (
        <ClueActionSheet
          key={activeClue.id}
          clue={activeClue}
          players={players}
          myPlayerId={myPlayerId}
          ownedClues={ownedClues}
          onClose={() => setActiveClue(null)}
          onExchange={onExchange}
          onShareFull={onShareFull}
          onSharePartial={onSharePartial}
        />
      )}
    </>
  )
}
