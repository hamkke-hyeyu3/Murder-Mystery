import { useState } from 'react'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'

interface EndScreenProps {
  onSurveySubmit: (platformScore: number | null, workScore: number | null, freeText: string | null) => void
}

export function EndScreen({ onSurveySubmit }: EndScreenProps) {
  const mySurveyResponded = useSessionStore((s) => s.mySurveyResponded)
  const respondedCount = useSessionStore((s) => s.surveyRespondedCount)
  const totalCount = useSessionStore((s) => s.surveyTotalCount)
  const missions = useCardStore((s) => s.missions)

  const [dismissed, setDismissed] = useState(false)
  const [platformScore, setPlatformScore] = useState<number>(3)
  const [workScore, setWorkScore] = useState<number>(3)
  const [freeText, setFreeText] = useState('')
  const [platformChecked, setPlatformChecked] = useState(false)
  const [workChecked, setWorkChecked] = useState(false)

  const showSurveyCard = !dismissed && !mySurveyResponded

  const handleRespond = () => {
    onSurveySubmit(
      platformChecked ? platformScore : null,
      workChecked ? workScore : null,
      freeText.trim() || null
    )
    setDismissed(true)
  }

  const handleSkip = () => {
    onSurveySubmit(null, null, null)
    setDismissed(true)
  }

  return (
    <div data-testid="end-screen" className="flex flex-col gap-6 p-6">
      {showSurveyCard && (
        <div data-testid="survey-card" className="flex flex-col gap-6 rounded-lg border p-6">
          <h2 className="text-xl font-bold">잠깐, 소감을 알려주세요!</h2>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <label className="flex items-center gap-2 font-medium">
                <input
                  type="checkbox"
                  data-testid="platform-score-check"
                  checked={platformChecked}
                  onChange={(e) => setPlatformChecked(e.target.checked)}
                />
                플랫폼 점수
              </label>
              <div className="flex items-center gap-3">
                <span className="text-sm text-muted-foreground">1</span>
                <input
                  type="range"
                  data-testid="platform-score-slider"
                  min={1}
                  max={5}
                  value={platformScore}
                  onChange={(e) => { setPlatformScore(Number(e.target.value)); setPlatformChecked(true) }}
                  className="flex-1"
                  disabled={!platformChecked}
                />
                <span className="text-sm text-muted-foreground">5</span>
                <span data-testid="platform-score-value" className="w-4 text-center text-sm font-medium">
                  {platformChecked ? platformScore : '-'}
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label className="flex items-center gap-2 font-medium">
                <input
                  type="checkbox"
                  data-testid="work-score-check"
                  checked={workChecked}
                  onChange={(e) => setWorkChecked(e.target.checked)}
                />
                작품 점수
              </label>
              <div className="flex items-center gap-3">
                <span className="text-sm text-muted-foreground">1</span>
                <input
                  type="range"
                  data-testid="work-score-slider"
                  min={1}
                  max={5}
                  value={workScore}
                  onChange={(e) => { setWorkScore(Number(e.target.value)); setWorkChecked(true) }}
                  className="flex-1"
                  disabled={!workChecked}
                />
                <span className="text-sm text-muted-foreground">5</span>
                <span data-testid="work-score-value" className="w-4 text-center text-sm font-medium">
                  {workChecked ? workScore : '-'}
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="font-medium">한 줄 후기 (선택)</label>
              <textarea
                data-testid="free-text-input"
                value={freeText}
                onChange={(e) => setFreeText(e.target.value)}
                maxLength={80}
                rows={2}
                placeholder="자유롭게 남겨주세요 (80자 이내)"
                className="w-full resize-none rounded border p-2 text-sm"
              />
              <span className="text-right text-xs text-muted-foreground">{freeText.length}/80</span>
            </div>
          </div>

          <div className="flex gap-3">
            <button
              data-testid="survey-respond-button"
              onClick={handleRespond}
              className="flex-1 rounded bg-primary px-4 py-2 text-primary-foreground"
            >
              응답하기
            </button>
            <button
              data-testid="survey-skip-button"
              onClick={handleSkip}
              className="flex-1 rounded border px-4 py-2"
            >
              건너뛰기
            </button>
          </div>

          {respondedCount != null && totalCount != null && (
            <p data-testid="survey-count" className="text-center text-sm text-muted-foreground">
              {respondedCount} / {totalCount} 응답 완료
            </p>
          )}
        </div>
      )}

      <div data-testid="end-message" className="flex flex-col items-center gap-4 p-6 text-center">
        <h2 className="text-2xl font-bold">수고했어요!</h2>
        {missions && missions.length > 0 && (
          <div data-testid="mission-summary" className="flex flex-col gap-2">
            <p className="text-sm text-muted-foreground">나의 미션 결과</p>
            <ul className="flex flex-col gap-1">
              {missions.map((m) => (
                <li key={m.label} data-testid={`mission-result-${m.label}`} className="text-sm">
                  {m.label}
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="text-sm text-muted-foreground">게임이 모두 끝났습니다. 즐거운 시간이었나요?</p>
      </div>
    </div>
  )
}
