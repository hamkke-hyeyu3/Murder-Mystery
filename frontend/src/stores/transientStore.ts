import { create } from 'zustand'

export interface Banner {
  id: string
  message: string
}

export type PendingPrivateTalk = {
  requestId: string
  partnerPlayerId: string
  partnerNickname: string
  role: 'requester' | 'target'
  expiresAt: number
}

export interface TransientState {
  banners: Banner[]
  inlineError: string | null
  pendingPrivateTalk: PendingPrivateTalk | null
}

interface TransientActions {
  pushBanner: (banner: Banner) => void
  dismissBanner: (id: string) => void
  setInlineError: (message: string | null) => void
  setPendingPrivateTalk: (talk: PendingPrivateTalk | null) => void
  reset: () => void
}

const initialState: TransientState = {
  banners: [],
  inlineError: null,
  pendingPrivateTalk: null,
}

export const useTransientStore = create<TransientState & TransientActions>((set) => ({
  ...initialState,
  pushBanner: (banner) =>
    set((state) => {
      if (state.banners.some((b) => b.id === banner.id)) return state
      return { banners: [...state.banners, banner].slice(-10) }
    }),
  dismissBanner: (id) =>
    set((state) => ({ banners: state.banners.filter((b) => b.id !== id) })),
  setInlineError: (inlineError) => set({ inlineError }),
  setPendingPrivateTalk: (pendingPrivateTalk) => set({ pendingPrivateTalk }),
  reset: () => set(initialState),
}))
