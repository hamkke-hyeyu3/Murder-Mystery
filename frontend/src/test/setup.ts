import '@testing-library/jest-dom'
import { server } from '@/mocks/server'
import { useSessionStore } from '@/stores/sessionStore'
import { useTransientStore } from '@/stores/transientStore'
import { useTimerStore } from '@/stores/timerStore'
import { useCardStore } from '@/stores/cardStore'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  useSessionStore.getState().reset()
  useTransientStore.getState().reset()
  useTimerStore.getState().reset()
  useCardStore.getState().reset()
  localStorage.clear()
})
afterAll(() => server.close())
