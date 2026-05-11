import { create } from 'zustand'

export interface TimerState {
  roundDeadlineAt: number | null
  turnDeadlineAt: number | null
  serverOffsetMs: number
}

interface TimerActions {
  setRoundDeadline: (deadlineAt: number | null) => void
  setTurnDeadline: (deadlineAt: number | null) => void
  setServerOffset: (serverOffsetMs: number) => void
  reset: () => void
}

const initialState: TimerState = {
  roundDeadlineAt: null,
  turnDeadlineAt: null,
  serverOffsetMs: 0,
}

export const useTimerStore = create<TimerState & TimerActions>((set) => ({
  ...initialState,
  setRoundDeadline: (roundDeadlineAt) => set({ roundDeadlineAt }),
  setTurnDeadline: (turnDeadlineAt) => set({ turnDeadlineAt }),
  setServerOffset: (serverOffsetMs) => set({ serverOffsetMs }),
  reset: () => set(initialState),
}))
