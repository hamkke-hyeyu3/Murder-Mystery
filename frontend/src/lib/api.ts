export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    throw new Error(`API error ${res.status} ${res.statusText}`)
  }
  return res.json() as Promise<T>
}
