import { useCountdown } from '@/hooks/useCountdown'

interface RoundPanelProps {
  roundNumber: number
  prompt: string
  commonHint: string | null
  deadlineAt: number | null
  serverOffsetMs: number
}

export function RoundPanel({ roundNumber, prompt, commonHint, deadlineAt, serverOffsetMs }: RoundPanelProps) {
  const { remainingSec, isWarning } = useCountdown(deadlineAt, serverOffsetMs)

  return (
    <div data-testid="round-panel" className="flex flex-col gap-4">
      <p data-testid="round-number" className="text-sm text-muted-foreground">
        라운드 {roundNumber}
      </p>
      <p data-testid="round-prompt" className="text-lg font-semibold">
        {prompt}
      </p>
      {commonHint && (
        <p data-testid="round-common-hint" className="text-sm border rounded p-3">
          {commonHint}
        </p>
      )}
      <p
        data-testid="round-countdown"
        aria-live={isWarning ? 'assertive' : 'off'}
        className={isWarning ? 'text-red-500 font-bold' : ''}
      >
        {remainingSec}초
      </p>
    </div>
  )
}
