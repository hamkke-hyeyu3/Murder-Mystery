import { create } from 'zustand'

export interface CardState {
  characterCard: unknown | null
  clues: unknown[]
  accessibleClueIds: string[]
}

interface CardActions {
  setCharacterCard: (card: unknown) => void
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
