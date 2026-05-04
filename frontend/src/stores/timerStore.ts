import { create } from 'zustand'

export interface TimerState {
  deadlineAt: number | null
  serverOffsetMs: number
}

interface TimerActions {
  setDeadline: (deadlineAt: number | null) => void
  setServerOffset: (serverOffsetMs: number) => void
  reset: () => void
}

const initialState: TimerState = {
  deadlineAt: null,
  serverOffsetMs: 0,
}

export const useTimerStore = create<TimerState & TimerActions>((set) => ({
  ...initialState,
  setDeadline: (deadlineAt) => set({ deadlineAt }),
  setServerOffset: (serverOffsetMs) => set({ serverOffsetMs }),
  reset: () => set(initialState),
}))
