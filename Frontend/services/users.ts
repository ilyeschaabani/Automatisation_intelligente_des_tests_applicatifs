import type { AuthUserSummary } from '@/types/auth'

const parseError = async (response: Response) => {
  const text = await response.text().catch(() => '')
  if (!text) return `Request failed (${response.status})`
  try {
    const data = JSON.parse(text) as Record<string, unknown>
    const message = data.message ?? data.error
    return typeof message === 'string' ? message : `Request failed (${response.status})`
  } catch {
    return text
  }
}

export const userDirectoryService = {
  getAll: async (): Promise<AuthUserSummary[]> => {
    const res = await fetch('/api/auth/users', { cache: 'no-store' })
    if (!res.ok) throw new Error(await parseError(res))
    const data = (await res.json()) as AuthUserSummary[]
    return Array.isArray(data) ? data : []
  },
}
