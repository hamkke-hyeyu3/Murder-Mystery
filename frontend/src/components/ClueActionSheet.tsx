import { useState } from 'react'
import { Button } from '@/components/ui/button'
import type { ClueView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

interface Props {
  clue: ClueView | null
  players: PlayerSummary[]
  myPlayerId: string | null
  onClose: () => void
  onShareFull: (clueId: string) => void
  onSharePartial: (clueId: string, recipientPlayerIds: string[]) => void
}

export function ClueActionSheet({ clue, players, myPlayerId, onClose, onShareFull, onSharePartial }: Props) {
  const [mode, setMode] = useState<'menu' | 'partial'>('menu')
  const [selected, setSelected] = useState<string[]>([])

  if (!clue) return null

  const others = players.filter((p) => p.playerId !== myPlayerId)

  const toggleRecipient = (playerId: string) => {
    setSelected((prev) =>
      prev.includes(playerId) ? prev.filter((id) => id !== playerId) : [...prev, playerId]
    )
  }

  const handleShareFull = () => {
    onShareFull(clue.id)
    onClose()
  }

  const handleConfirmPartial = () => {
    if (selected.length === 0) return
    onSharePartial(clue.id, selected)
    onClose()
  }

  const handleEnterPartial = () => {
    setSelected([])
    setMode('partial')
  }

  return (
    <div
      data-testid="clue-action-sheet"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-background rounded-xl shadow-lg p-5 w-72 flex flex-col gap-3">
        <p className="text-sm font-semibold text-center">{clue.title}</p>

        {mode === 'menu' && (
          <>
            <Button data-testid="action-share-full" variant="default" onClick={handleShareFull}>
              전체 공유
            </Button>
            <Button data-testid="action-share-partial" variant="outline" onClick={handleEnterPartial}>
              부분 공유
            </Button>
            <Button data-testid="action-exchange" variant="outline" disabled title="곧 지원 예정">
              교환 (곧 지원)
            </Button>
            <Button data-testid="action-cancel" variant="ghost" onClick={onClose}>
              취소
            </Button>
          </>
        )}

        {mode === 'partial' && (
          <>
            <p className="text-xs text-muted-foreground">공유할 플레이어를 선택하세요</p>
            <ul className="flex flex-col gap-1 max-h-40 overflow-y-auto">
              {others.map((p) => (
                <li key={p.playerId}>
                  <label className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      data-testid={`recipient-${p.playerId}`}
                      checked={selected.includes(p.playerId)}
                      onChange={() => toggleRecipient(p.playerId)}
                    />
                    {p.nickname}
                  </label>
                </li>
              ))}
            </ul>
            <Button
              data-testid="confirm-share-partial"
              variant="default"
              disabled={selected.length === 0}
              onClick={handleConfirmPartial}
            >
              확정
            </Button>
            <Button data-testid="action-cancel" variant="ghost" onClick={onClose}>
              취소
            </Button>
          </>
        )}
      </div>
    </div>
  )
}
