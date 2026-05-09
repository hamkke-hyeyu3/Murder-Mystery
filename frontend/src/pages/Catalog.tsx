import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { apiFetch } from '@/lib/api'
import { createSession } from '@/lib/sessionApi'
import { useResumeSession } from '@/hooks/useResumeSession'
import { useSessionStore } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'
import type { ScenarioSummary } from '@/types/scenario'

type CatalogState =
  | { status: 'loading' }
  | { status: 'loaded'; scenarios: ScenarioSummary[] }
  | { status: 'error' }

type CreateState = {
  scenarioId: string | null
  nickname: string
  submitting: boolean
  error: string | null
}

const initialCreate: CreateState = {
  scenarioId: null,
  nickname: '',
  submitting: false,
  error: null,
}

export default function Catalog() {
  useResumeSession()
  const [catalog, setCatalog] = useState<CatalogState>({ status: 'loading' })
  const [creating, setCreating] = useState<CreateState>(initialCreate)
  const navigate = useNavigate()
  const setSession = useSessionStore((s) => s.setSession)

  useEffect(() => {
    apiFetch<ScenarioSummary[]>('/api/scenarios')
      .then((scenarios) => setCatalog({ status: 'loaded', scenarios }))
      .catch(() => setCatalog({ status: 'error' }))
  }, [])

  const handleStartClick = (scenarioId: string) => {
    setCreating({ scenarioId, nickname: '', submitting: false, error: null })
  }

  const handleCancel = () => {
    setCreating(initialCreate)
  }

  const handleConfirm = async () => {
    const trimmed = creating.nickname.trim()
    if (!trimmed || !creating.scenarioId) return

    setCreating((c) => ({ ...c, submitting: true, error: null }))
    try {
      const res = await createSession(creating.scenarioId, trimmed)
      const last = {
        sessionId: res.sessionId,
        inviteCode: res.inviteCode,
        nickname: res.hostNickname,
        isHost: true,
        playerId: res.playerId,
        scenarioId: res.scenarioId,
        savedAt: Date.now(),
      }
      try {
        localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(last))
      } catch {
        // quota exceeded or storage disabled — store is still hydrated below
      }
      setSession({
        sessionId: res.sessionId,
        inviteCode: res.inviteCode,
        nickname: res.hostNickname,
        isHost: true,
        isHostConfirmed: false,
        playerId: res.playerId,
        phase: res.phase,
        requiredCharacterCount: null,
        joinedCount: null,
      })
      navigate(`/lobby/${res.inviteCode}`)
    } catch {
      setCreating((c) => ({ ...c, submitting: false, error: '세션 생성 실패' }))
    }
  }

  return (
    <div data-testid="page-catalog" className="min-h-screen p-6">
      <h1 className="text-2xl font-bold mb-6">시나리오</h1>

      {catalog.status === 'loading' && <p>불러오는 중…</p>}

      {catalog.status === 'error' && (
        <p role="alert">시나리오를 불러오지 못했습니다</p>
      )}

      {catalog.status === 'loaded' && catalog.scenarios.length === 0 && (
        <p>출시 시나리오가 없습니다</p>
      )}

      {catalog.status === 'loaded' && catalog.scenarios.length > 0 && (
        <ul className="flex flex-col gap-4">
          {catalog.scenarios.map((scenario) => (
            <li
              key={scenario.id}
              className="border rounded-xl p-5 flex flex-col gap-3"
            >
              <div className="flex items-center gap-2">
                {scenario.icon && (
                  <span className="text-2xl">{scenario.icon}</span>
                )}
                <h2 className="text-xl font-semibold">{scenario.title}</h2>
              </div>
              <p className="text-sm text-muted-foreground">{scenario.summary}</p>
              <div className="flex gap-4 text-sm text-muted-foreground">
                <span>{scenario.playerCount}명</span>
                <span>약 {scenario.estimatedMinutes}분</span>
              </div>

              {creating.scenarioId === scenario.id ? (
                <div className="flex flex-col gap-2">
                  <div className="flex gap-2 items-center">
                    <input
                      className="border rounded px-3 py-2 text-sm flex-1"
                      placeholder="닉네임"
                      value={creating.nickname}
                      onChange={(e) =>
                        setCreating((c) => ({ ...c, nickname: e.target.value }))
                      }
                      autoFocus
                    />
                    <Button
                      onClick={handleConfirm}
                      disabled={!creating.nickname.trim() || creating.submitting}
                    >
                      확인
                    </Button>
                    <Button variant="outline" onClick={handleCancel} disabled={creating.submitting}>
                      취소
                    </Button>
                  </div>
                  {creating.error && (
                    <p role="alert" className="text-sm text-destructive">
                      {creating.error}
                    </p>
                  )}
                </div>
              ) : (
                <Button
                  onClick={() => handleStartClick(scenario.id)}
                  className="self-start"
                >
                  세션 만들기
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
