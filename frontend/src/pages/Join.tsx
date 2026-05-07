import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { joinSession } from '@/lib/sessionApi'
import { useSessionStore } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'

type JoinState = {
  inviteCode: string
  nickname: string
  submitting: boolean
  error: string | null
}

export default function Join() {
  const [searchParams] = useSearchParams()
  const [state, setState] = useState<JoinState>({
    inviteCode: searchParams.get('invite') ?? '',
    nickname: '',
    submitting: false,
    error: null,
  })
  const navigate = useNavigate()
  const setSession = useSessionStore((s) => s.setSession)

  const handleSubmit = async () => {
    const trimmedInvite = state.inviteCode.trim()
    const trimmedNickname = state.nickname.trim()
    if (!trimmedInvite || !trimmedNickname) return

    setState((s) => ({ ...s, submitting: true, error: null }))
    try {
      const res = await joinSession(trimmedInvite, trimmedNickname)
      const last = {
        sessionId: res.sessionId,
        inviteCode: res.inviteCode,
        nickname: res.nickname,
        isHost: false,
        playerId: res.playerId,
        scenarioId: res.scenarioId,
        savedAt: Date.now(),
      }
      try {
        localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(last))
      } catch {
        // quota exceeded or storage disabled
      }
      setSession({
        sessionId: res.sessionId,
        inviteCode: res.inviteCode,
        nickname: res.nickname,
        isHost: false,
        isHostConfirmed: false,
        playerId: res.playerId,
        phase: res.phase,
        players: res.players.map((p) => ({ nickname: p.nickname, isHost: p.isHost })),
        requiredCharacterCount: null,
        joinedCount: null,
      })
      navigate(`/lobby/${res.inviteCode}`)
    } catch (err) {
      const status = (err as { status?: number }).status
      let error = '합류 실패'
      if (status === 409) error = '이미 사용 중인 닉네임입니다'
      else if (status === 400) error = '초대 번호를 다시 확인하세요'
      setState((s) => ({ ...s, submitting: false, error }))
    }
  }

  const canSubmit = state.inviteCode.trim().length > 0 && state.nickname.trim().length > 0

  return (
    <div data-testid="page-join" className="min-h-screen flex flex-col items-center justify-center p-6">
      <div className="w-full max-w-sm flex flex-col gap-4">
        <h1 className="text-2xl font-bold">게임 합류</h1>

        <input
          className="border rounded px-3 py-2 text-sm"
          placeholder="초대 코드"
          value={state.inviteCode}
          onChange={(e) => setState((s) => ({ ...s, inviteCode: e.target.value }))}
        />

        <input
          className="border rounded px-3 py-2 text-sm"
          placeholder="닉네임"
          value={state.nickname}
          onChange={(e) => setState((s) => ({ ...s, nickname: e.target.value }))}
        />

        <Button onClick={handleSubmit} disabled={!canSubmit || state.submitting}>
          합류
        </Button>

        {state.error && (
          <p role="alert" className="text-sm text-destructive">
            {state.error}
          </p>
        )}
      </div>
    </div>
  )
}
