import { useCardStore } from '@/stores/cardStore'
import { LocationLabel } from './LocationLabel'

export function CharacterCard() {
  const characterCard = useCardStore((s) => s.characterCard)
  const currentObjective = useCardStore((s) => s.currentObjective)

  if (!characterCard) return null

  const { name, speechStyle, background, motive, alibi, secret, relationships, alibiLocation, items } = characterCard

  return (
    <div data-testid="character-card" className="flex flex-col gap-4 p-4 border rounded-lg">
      {/* 헤더: 이름 + 말투(선택) + 라운드 진행 */}
      <div data-testid="card-section-header" className="flex flex-col gap-1">
        <p className="text-xl font-bold">{name}</p>
        {speechStyle && (
          <p data-testid="card-speech-style" className="text-sm italic text-muted-foreground">
            말투: {speechStyle}
          </p>
        )}
        {currentObjective && (
          <p className="text-xs text-muted-foreground">
            라운드 {currentObjective.roundNumber} / {currentObjective.totalRounds}
          </p>
        )}
      </div>

      {/* 라운드 목표 */}
      <div data-testid="card-section-objective" className="rounded bg-muted p-3">
        <p className="text-xs font-semibold uppercase tracking-wide mb-1">이번 라운드 목표</p>
        <p className="text-sm">
          {currentObjective === null
            ? '라운드 시작 대기 중'
            : currentObjective.objective ?? '목표 없음'}
        </p>
      </div>

      {/* 미션 자리표시자 */}
      <div data-testid="card-section-mission" className="rounded bg-muted p-3">
        <p className="text-xs font-semibold uppercase tracking-wide mb-1">미션</p>
        <p className="text-sm text-muted-foreground">미션은 단계 9-B에서 공개됩니다</p>
      </div>

      {/* 아이템 */}
      <div data-testid="card-section-items" className="flex flex-col gap-1">
        <p className="text-xs font-semibold uppercase tracking-wide">아이템</p>
        {!items || items.length === 0 ? (
          <p className="text-sm text-muted-foreground">아이템 없음</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {items.map((item) => (
              <li key={item.id} className="flex items-center gap-2 text-sm">
                <span>{item.title}</span>
                {item.originLocation && (
                  <LocationLabel icon={item.originLocation.icon} name={item.originLocation.name} />
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* 본문: 배경·동기·알리바이·비밀·관계 */}
      <div data-testid="card-section-body" className="flex flex-col gap-2">
        {background && (
          <div>
            <p className="text-xs font-semibold">배경</p>
            <p className="text-sm">{background}</p>
          </div>
        )}
        {motive && (
          <div>
            <p className="text-xs font-semibold">동기</p>
            <p className="text-sm">{motive}</p>
          </div>
        )}
        {alibi && (
          <div>
            <p className="text-xs font-semibold">알리바이</p>
            <p className="text-sm">{alibi}</p>
          </div>
        )}
        {secret && (
          <div>
            <p className="text-xs font-semibold">비밀</p>
            <p className="text-sm">{secret}</p>
          </div>
        )}
        {relationships && (
          <div>
            <p className="text-xs font-semibold">관계</p>
            <p className="text-sm">{relationships}</p>
          </div>
        )}
      </div>

      {/* 알리바이 장소 라벨 */}
      <div data-testid="card-section-alibi-location" className="flex items-center gap-2">
        <p className="text-xs font-semibold">알리바이 장소</p>
        {alibiLocation ? (
          <LocationLabel icon={alibiLocation.icon} name={alibiLocation.name} />
        ) : (
          <span className="text-sm text-muted-foreground">—</span>
        )}
      </div>
    </div>
  )
}
