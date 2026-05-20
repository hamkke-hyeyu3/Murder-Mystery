import { useSessionStore } from '@/stores/sessionStore'

export function RevealPanel() {
  const reveal = useSessionStore((s) => s.reveal)
  const vote = useSessionStore((s) => s.vote)

  if (!reveal) return null

  const { outcome, culpritCharacterId } = reveal
  const candidates = vote?.candidates ?? []
  const culprit = candidates.find((c) => c.characterId === culpritCharacterId)
  const culpritName = culprit?.name ?? culpritCharacterId

  if (outcome === 'single_winner') {
    const tally = vote?.tally ?? []
    return (
      <div data-testid="reveal-result-winner" className="flex flex-col items-center gap-4 p-6">
        <h2 className="text-2xl font-bold">범인 공개</h2>
        <p className="text-lg">
          범인은 <span className="font-semibold">{culpritName}</span>였다
        </p>
        {tally.length > 0 && (
          <ul className="text-sm text-muted-foreground">
            {tally.map((t) => {
              const c = candidates.find((cd) => cd.characterId === t.characterId)
              return (
                <li key={t.characterId}>
                  {c?.name ?? t.characterId}: {t.count}표
                </li>
              )
            })}
          </ul>
        )}
        <p className="text-sm text-muted-foreground">잠시 후 미션이 공개됩니다…</p>
      </div>
    )
  }

  const tiedCharacterIds = vote?.tiedCharacterIds ?? []
  return (
    <div data-testid="reveal-result-failed" className="flex flex-col items-center gap-4 p-6">
      <h2 className="text-2xl font-bold">색출 실패</h2>
      <p className="text-lg">
        진범은 <span className="font-semibold">{culpritName}</span>였다
      </p>
      {tiedCharacterIds.length > 0 && (
        <p className="text-sm text-muted-foreground">
          동점 후보: {tiedCharacterIds.map((id) => {
            const c = candidates.find((cd) => cd.characterId === id)
            return c?.name ?? id
          }).join(', ')}
        </p>
      )}
      <p className="text-sm text-muted-foreground">잠시 후 미션이 공개됩니다…</p>
    </div>
  )
}
