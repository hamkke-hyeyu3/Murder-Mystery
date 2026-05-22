import { useSessionStore } from '@/stores/sessionStore'

export function EndingPanel() {
  const reveal = useSessionStore((s) => s.reveal)
  const vote = useSessionStore((s) => s.vote)

  const candidates = vote?.candidates ?? []
  const culprit = reveal ? candidates.find((c) => c.characterId === reveal.culpritCharacterId) : null
  const culpritName = culprit?.name ?? reveal?.culpritCharacterId

  return (
    <div data-testid="ending-panel" className="flex flex-col items-center gap-6 p-6">
      <h2 className="text-3xl font-bold">게임 종료</h2>
      {reveal ? (
        reveal.outcome === 'single_winner' ? (
          <div className="flex flex-col items-center gap-2">
            <p className="text-lg">
              범인은 <span className="font-semibold">{culpritName}</span>였습니다
            </p>
            <p className="text-sm text-muted-foreground">여러분이 범인을 찾아냈습니다!</p>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2">
            <p className="text-xl font-semibold text-destructive">색출 실패</p>
            <p className="text-lg">
              진범은 <span className="font-semibold">{culpritName}</span>였습니다
            </p>
            <p className="text-sm text-muted-foreground">범인이 도주에 성공했습니다.</p>
          </div>
        )
      ) : (
        <p className="text-muted-foreground">엔딩 준비 중…</p>
      )}
    </div>
  )
}
