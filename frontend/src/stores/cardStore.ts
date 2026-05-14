import { create } from 'zustand'
import type { CharacterCardPayload, ClueView, ObjectiveUpdatedPayload, OwnedClueView } from '@/types/session'

export interface CardState {
  characterCard: CharacterCardPayload | null
  currentObjective: ObjectiveUpdatedPayload | null
  clues: ClueView[]
  accessibleClueIds: string[]
  ownedClues: OwnedClueView[]
}

interface CardActions {
  setCharacterCard: (card: CharacterCardPayload) => void
  setObjective: (objective: ObjectiveUpdatedPayload) => void
  addClue: (clue: ClueView) => void
  setClues: (clues: ClueView[]) => void
  setAccessibleClueIds: (ids: string[]) => void
  setOwnedClues: (clues: OwnedClueView[]) => void
  reset: () => void
}

const initialState: CardState = {
  characterCard: null,
  currentObjective: null,
  clues: [],
  accessibleClueIds: [],
  ownedClues: [],
}

export const useCardStore = create<CardState & CardActions>((set) => ({
  ...initialState,
  setCharacterCard: (characterCard) => set({ characterCard }),
  setObjective: (currentObjective) => set({ currentObjective }),
  addClue: (clue) => set((state) => ({ clues: [...state.clues, clue] })),
  setClues: (clues) => set({ clues }),
  setAccessibleClueIds: (accessibleClueIds) => set({ accessibleClueIds }),
  setOwnedClues: (ownedClues) => set({ ownedClues }),
  reset: () => set(initialState),
}))
