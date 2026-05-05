import '@testing-library/jest-dom'
import { server } from '@/mocks/server'
import { useSessionStore } from '@/stores/sessionStore'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  useSessionStore.getState().reset()
  localStorage.clear()
})
afterAll(() => server.close())
