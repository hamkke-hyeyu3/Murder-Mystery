import { create } from 'zustand'
import type { CharacterCardPayload } from '@/types/session'

export interface CardState {
  characterCard: CharacterCardPayload | null
  clues: unknown[]
  accessibleClueIds: string[]
}

interface CardActions {
  setCharacterCard: (card: CharacterCardPayload) => void
  addClue: (clue: unknown) => void
  setAccessibleClueIds: (ids: string[]) => void
  reset: () => void
}

const initialState: CardState = {
  characterCard: null,
  clues: [],
  accessibleClueIds: [],
}

export const useCardStore = create<CardState & CardActions>((set) => ({
  ...initialState,
  setCharacterCard: (characterCard) => set({ characterCard }),
  addClue: (clue) => set((state) => ({ clues: [...state.clues, clue] })),
  setAccessibleClueIds: (accessibleClueIds) => set({ accessibleClueIds }),
  reset: () => set(initialState),
}))
