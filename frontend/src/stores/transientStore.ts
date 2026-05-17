import { create } from 'zustand'

export interface Banner {
  id: string
  message: string
}

export interface TransientState {
  banners: Banner[]
  inlineError: string | null
}

interface TransientActions {
  pushBanner: (banner: Banner) => void
  dismissBanner: (id: string) => void
  setInlineError: (message: string | null) => void
  reset: () => void
}

const initialState: TransientState = {
  banners: [],
  inlineError: null,
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
  reset: () => set(initialState),
}))
