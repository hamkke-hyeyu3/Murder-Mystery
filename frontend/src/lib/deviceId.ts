const KEY = 'mm:deviceId'
let cached: string | null = null

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function applyDevOverride(): void {
  if (!import.meta.env.DEV) return
  const param = new URLSearchParams(location.search).get('deviceId')
  if (param && UUID_RE.test(param)) {
    try {
      localStorage.setItem(KEY, param)
    } catch { /* ignore */ }
    cached = param
    const url = new URL(location.href)
    url.searchParams.delete('deviceId')
    history.replaceState(null, '', url.toString())
  }
}

export function getDeviceId(): string {
  if (cached) return cached
  applyDevOverride()
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
