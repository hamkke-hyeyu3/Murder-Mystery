interface TutorialProps {
  ackedCount: number
  totalCount: number
  isAcked: boolean
  onAck: () => Promise<void>
}

export function Tutorial({ ackedCount, totalCount, isAcked, onAck }: TutorialProps) {
  return (
    <div data-testid="tutorial" className="flex flex-col gap-6 p-6 max-w-lg mx-auto">
      <h2 className="text-2xl font-bold">단계 3 — 게임 규칙</h2>

      <div className="rounded-lg border p-4 bg-muted">
        <p className="font-semibold mb-1">거짓말 허용 범위</p>
        <p>
          이 게임은 <strong>전원 거짓말 가능</strong> 정책입니다.
          모든 플레이어가 자신에게 유리하게 거짓말할 수 있습니다.
        </p>
      </div>

      <ul className="list-disc pl-5 space-y-1 text-sm text-muted-foreground">
        <li>본인의 캐릭터 카드에 적힌 정보만 알고 있습니다.</li>
        <li>라운드마다 장소를 조사해 단서를 수집합니다.</li>
        <li>아이템은 교환하거나 공유할 수 있습니다.</li>
        <li>밀담으로 특정 플레이어와 대화할 수 있습니다.</li>
        <li>마지막에 투표로 범인을 지목합니다.</li>
      </ul>

      {!isAcked ? (
        <button
          data-testid="tutorial-confirm"
          onClick={onAck}
          className="w-full rounded-md bg-primary text-primary-foreground py-3 font-semibold"
        >
          확인
        </button>
      ) : (
        <div className="space-y-2">
          <button
            data-testid="tutorial-confirm"
            disabled
            className="w-full rounded-md bg-primary text-primary-foreground py-3 font-semibold opacity-50 cursor-not-allowed"
          >
            확인 완료
          </button>
          <p data-testid="tutorial-wait" className="text-center text-sm text-muted-foreground">
            {ackedCount} / {totalCount} 통과 — 다른 플레이어를 기다리는 중…
          </p>
        </div>
      )}
    </div>
  )
}
