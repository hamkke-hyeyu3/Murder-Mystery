const KEY = 'mm:deviceId'
let cached: string | null = null

export function getDeviceId(): string {
  if (cached) return cached
  try {
    const existing = localStorage.getItem(KEY)
    if (existing) { cached = existing; return existing }
    const id = crypto.randomUUID()
    localStorage.setItem(KEY, id)
    cached = id
    return id
  } catch {
    if (!cached) cached = crypto.randomUUID()
    return cached
  }
}
