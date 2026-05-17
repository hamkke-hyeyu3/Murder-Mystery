import { useState } from 'react'
import { Button } from '@/components/ui/button'
import type { ClueView, OwnedClueView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

interface Props {
  clue: ClueView
  players: PlayerSummary[]
  myPlayerId: string | null
  ownedClues: OwnedClueView[]
  onClose: () => void
  onExchange: (partnerPlayerId: string, requesterClueId: string, partnerClueId: string) => void
  onShareFull: (clueId: string) => void
  onSharePartial: (clueId: string, recipientPlayerIds: string[]) => void
}

type Mode = 'menu' | 'partial' | 'exchange-partner' | 'exchange-clue'

export function ClueActionSheet({ clue, players, myPlayerId, ownedClues, onClose, onExchange, onShareFull, onSharePartial }: Props) {
  const [mode, setMode] = useState<Mode>('menu')
  const [selected, setSelected] = useState<string[]>([])
  const [partnerPlayerId, setPartnerPlayerId] = useState<string | null>(null)
  const [requesterClueId, setRequesterClueId] = useState<string | null>(null)
  const [partnerClueId, setPartnerClueId] = useState<string | null>(null)

  const others = players.filter((p) => p.playerId !== myPlayerId)

  // client filter is UX-only; server enforces ownership
  const myOwnedClues = ownedClues.filter((c) => c.ownerPlayerId === myPlayerId)

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

  const handleSelectPartner = (playerId: string) => {
    setPartnerPlayerId(playerId)
    setRequesterClueId(null)
    setPartnerClueId(null)
    setMode('exchange-clue')
  }

  const handleConfirmExchange = () => {
    if (!partnerPlayerId || !requesterClueId || !partnerClueId) return
    if (requesterClueId === partnerClueId) return
    onExchange(partnerPlayerId, requesterClueId, partnerClueId)
    onClose()
  }

  const partnerOwnedClues = partnerPlayerId
    ? ownedClues.filter((c) => c.ownerPlayerId === partnerPlayerId)
    : []

  return (
    <div
      data-testid="clue-action-sheet"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-background rounded-xl shadow-lg p-5 w-80 flex flex-col gap-3">
        <p className="text-sm font-semibold text-center">{clue.title}</p>

        {mode === 'menu' && (
          <>
            <Button data-testid="action-share-full" variant="default" onClick={handleShareFull}>
              전체 공유
            </Button>
            <Button data-testid="action-share-partial" variant="outline" onClick={handleEnterPartial}>
              부분 공유
            </Button>
            <Button
              data-testid="action-exchange"
              variant="outline"
              disabled={myOwnedClues.length === 0}
              onClick={() => setMode('exchange-partner')}
            >
              교환
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

        {mode === 'exchange-partner' && (
          <>
            <p className="text-xs text-muted-foreground">교환할 상대를 선택하세요</p>
            <ul className="flex flex-col gap-1 max-h-40 overflow-y-auto">
              {others.map((p) => {
                const partnerClues = ownedClues.filter((c) => c.ownerPlayerId === p.playerId)
                return (
                  <li key={p.playerId}>
                    <button
                      data-testid={`exchange-partner-${p.playerId}`}
                      className="w-full text-left text-sm px-2 py-1 rounded hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed"
                      disabled={partnerClues.length === 0}
                      onClick={() => handleSelectPartner(p.playerId)}
                    >
                      {p.nickname}
                      {partnerClues.length === 0 && <span className="text-xs text-muted-foreground ml-1">(단서 없음)</span>}
                    </button>
                  </li>
                )
              })}
            </ul>
            <Button data-testid="exchange-back" variant="ghost" onClick={() => setMode('menu')}>
              뒤로
            </Button>
          </>
        )}

        {mode === 'exchange-clue' && (
          <>
            <p className="text-xs text-muted-foreground">내 단서와 상대 단서를 각각 선택하세요</p>
            <div className="flex gap-3">
              <div className="flex-1">
                <p className="text-xs font-medium mb-1">내 단서</p>
                {myOwnedClues.length === 0 ? (
                  <p data-testid="exchange-no-my-clues" className="text-xs text-muted-foreground">소유한 단서 없음</p>
                ) : (
                  <ul className="flex flex-col gap-1 max-h-36 overflow-y-auto">
                    {myOwnedClues.map((c) => (
                      <li key={c.id}>
                        <label className="flex items-center gap-1 text-xs cursor-pointer">
                          <input
                            type="radio"
                            name="requester-clue"
                            data-testid={`exchange-my-clue-${c.id}`}
                            checked={requesterClueId === c.id}
                            onChange={() => setRequesterClueId(c.id)}
                          />
                          {c.title}
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="flex-1">
                <p className="text-xs font-medium mb-1">상대 단서</p>
                <ul className="flex flex-col gap-1 max-h-36 overflow-y-auto">
                  {partnerOwnedClues.map((c) => (
                    <li key={c.id}>
                      <label className="flex items-center gap-1 text-xs cursor-pointer">
                        <input
                          type="radio"
                          name="partner-clue"
                          data-testid={`exchange-partner-clue-${c.id}`}
                          checked={partnerClueId === c.id}
                          onChange={() => setPartnerClueId(c.id)}
                        />
                        {c.title}
                      </label>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
            <Button
              data-testid="confirm-exchange"
              variant="default"
              disabled={!requesterClueId || !partnerClueId || requesterClueId === partnerClueId}
              onClick={handleConfirmExchange}
            >
              교환 확정
            </Button>
            <Button data-testid="exchange-clue-back" variant="ghost" onClick={() => setMode('exchange-partner')}>
              뒤로
            </Button>
          </>
        )}
      </div>
    </div>
  )
}
