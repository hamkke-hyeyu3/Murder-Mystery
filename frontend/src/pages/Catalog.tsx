import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { apiFetch } from '@/lib/api'
import type { ScenarioSummary } from '@/types/scenario'

type State =
  | { status: 'loading' }
  | { status: 'loaded'; scenarios: ScenarioSummary[] }
  | { status: 'error' }

export default function Catalog() {
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    apiFetch<ScenarioSummary[]>('/api/scenarios')
      .then((scenarios) => setState({ status: 'loaded', scenarios }))
      .catch(() => setState({ status: 'error' }))
  }, [])

  return (
    <div data-testid="page-catalog" className="min-h-screen p-6">
      <h1 className="text-2xl font-bold mb-6">시나리오</h1>

      {state.status === 'loading' && <p>불러오는 중…</p>}

      {state.status === 'error' && (
        <p role="alert">시나리오를 불러오지 못했습니다</p>
      )}

      {state.status === 'loaded' && state.scenarios.length === 0 && (
        <p>출시 시나리오가 없습니다</p>
      )}

      {state.status === 'loaded' && state.scenarios.length > 0 && (
        <ul className="flex flex-col gap-4">
          {state.scenarios.map((scenario) => (
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
              <Button
                onClick={() => console.log('세션 만들기:', scenario.id)}
                className="self-start"
              >
                세션 만들기
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
