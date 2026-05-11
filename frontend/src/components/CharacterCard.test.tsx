import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { useCardStore } from '@/stores/cardStore'
import { CharacterCard } from './CharacterCard'
import type { CharacterCardPayload, ObjectiveUpdatedPayload } from '@/types/session'

const baseCard: CharacterCardPayload = {
  characterId: 'alice',
  name: '앨리스 · 가정부',
  turnOrderIndex: 0,
  speechStyle: '공손한 존댓말',
  background: '10년째 저택을 관리해 온 가정부.',
  motive: '월급을 6개월째 받지 못했다.',
  alibi: '저녁 8시 도서관 서고에 있었다.',
  secret: '유언장을 몰래 읽었다.',
  relationships: '집사 밥을 신뢰하지 않는다.',
  alibiLocation: { id: 'library', name: '도서관 서고', icon: '📚' },
  items: [],
}

const objective: ObjectiveUpdatedPayload = {
  roundNumber: 1,
  totalRounds: 3,
  objective: '자기소개를 하세요.',
}

beforeEach(() => {
  useCardStore.getState().reset()
})

describe('CharacterCard', () => {
  it('6 영역 모두 도달 가능하다', () => {
    useCardStore.getState().setCharacterCard(baseCard)
    useCardStore.getState().setObjective(objective)
    render(<CharacterCard />)

    expect(screen.getByTestId('card-section-header')).toBeInTheDocument()
    expect(screen.getByTestId('card-section-objective')).toBeInTheDocument()
    expect(screen.getByTestId('card-section-mission')).toBeInTheDocument()
    expect(screen.getByTestId('card-section-items')).toBeInTheDocument()
    expect(screen.getByTestId('card-section-body')).toBeInTheDocument()
    expect(screen.getByTestId('card-section-alibi-location')).toBeInTheDocument()
  })

  it('speechStyle 미선언 시 말투 줄이 보이지 않는다', () => {
    const cardWithoutSpeech: CharacterCardPayload = { ...baseCard, speechStyle: undefined }
    useCardStore.getState().setCharacterCard(cardWithoutSpeech)
    render(<CharacterCard />)

    expect(screen.queryByTestId('card-speech-style')).not.toBeInTheDocument()
  })

  it('currentObjective 갱신 시 헤더 진행 표시와 목표 텍스트가 교체된다', () => {
    useCardStore.getState().setCharacterCard(baseCard)
    useCardStore.getState().setObjective({ roundNumber: 2, totalRounds: 3, objective: '알리바이를 비교하세요.' })
    render(<CharacterCard />)

    expect(screen.getByTestId('card-section-header')).toHaveTextContent('라운드 2 / 3')
    expect(screen.getByTestId('card-section-objective')).toHaveTextContent('알리바이를 비교하세요.')
  })

  it('items 빈 배열 시 "아이템 없음"을 보인다', () => {
    useCardStore.getState().setCharacterCard({ ...baseCard, items: [] })
    render(<CharacterCard />)

    expect(screen.getByTestId('card-section-items')).toHaveTextContent('아이템 없음')
  })

  it('루트에 data-character-id가 characterId로 노출된다', () => {
    useCardStore.getState().setCharacterCard(baseCard)
    render(<CharacterCard />)

    expect(screen.getByTestId('character-card')).toHaveAttribute('data-character-id', 'alice')
  })
})
