import { useState } from 'react'
import { useSessionStore } from '@/stores/sessionStore'
import { useCountdown } from '@/hooks/useCountdown'
import { useTimerStore } from '@/stores/timerStore'

interface VotePanelProps {
  onSubmit: (targetCharacterId: string, roundNo: number) => void
}

export function VotePanel({ onSubmit }: VotePanelProps) {
  const vote = useSessionStore((s) => s.vote)
  const playerId = useSessionStore((s) => s.playerId)
  const serverOffsetMs = useTimerStore((s) => s.serverOffsetMs)
  const [selected, setSelected] = useState<string | null>(null)

  const deadlineAt = vote?.deadlineAt ?? 0
  const { remainingSec: secondsLeft } = useCountdown(deadlineAt, serverOffsetMs)

  if (!vote) return null
  if (vote.outcome != null) return null  // RevealPanel takes over once vote concludes

  const { candidates, submittedCount, totalCount, myVote, roundNo } = vote

  // ── Active voting screen ────────────────────────────────────────────

  const isRunoff = roundNo === 1

  const handleSubmit = () => {
    if (!selected) return
    onSubmit(selected, roundNo)
    setSelected(null)
  }

  return (
    <div data-testid="vote-panel" className="flex flex-col gap-6 p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold">
          {isRunoff ? '재투표' : '투표'} — 범인을 지목하세요
        </h2>
        <div className="flex gap-4 text-sm text-muted-foreground">
          <span data-testid="vote-count">{submittedCount} / {totalCount} 제출</span>
          {secondsLeft > 0 && (
            <span data-testid="vote-countdown">{secondsLeft}초</span>
          )}
        </div>
      </div>

      {isRunoff && (
        <div data-testid="vote-runoff-banner"
             className="rounded-md bg-yellow-50 border border-yellow-300 p-3 text-sm text-yellow-800">
          동점으로 재투표가 시작되었습니다.
        </div>
      )}

      <div className="grid grid-cols-2 gap-3" data-testid="vote-candidates">
        {candidates.map((c) => {
          const isMe = c.playerId === playerId
          const isSelected = selected === c.characterId
          const alreadyVoted = myVote === c.characterId
          return (
            <button
              key={c.characterId}
              data-testid={`vote-candidate-${c.characterId}`}
              onClick={() => setSelected(c.characterId)}
              className={[
                'rounded-lg border p-4 text-left transition-colors',
                isSelected
                  ? 'border-primary bg-primary/10 ring-2 ring-primary'
                  : alreadyVoted
                  ? 'border-muted bg-muted/30'
                  : 'border-border hover:bg-muted/50',
              ].join(' ')}
            >
              <p className="font-medium">
                {c.name}
                {isMe && <span className="ml-1 text-xs text-muted-foreground">(나)</span>}
              </p>
              {c.playerNickname && (
                <p className="text-xs text-muted-foreground">{c.playerNickname}</p>
              )}
              {alreadyVoted && !isSelected && (
                <p className="text-xs text-primary mt-1">현재 선택</p>
              )}
            </button>
          )
        })}
      </div>

      <button
        data-testid="vote-submit-btn"
        disabled={!selected}
        onClick={handleSubmit}
        className="w-full rounded-lg bg-primary px-4 py-3 text-sm font-medium text-primary-foreground disabled:opacity-40"
      >
        {myVote ? '다시 제출' : '제출'}
      </button>
    </div>
  )
}
