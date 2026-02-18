export type ExtractedTokens = {
  accessToken?: string
  refreshToken?: string
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const walk = (
  value: unknown,
  visitor: (key: string, val: unknown) => void,
  seen: WeakSet<object>,
) => {
  if (!isRecord(value)) return
  if (seen.has(value)) return
  seen.add(value)

  for (const [key, val] of Object.entries(value)) {
    visitor(key, val)
    if (isRecord(val)) walk(val, visitor, seen)
    if (Array.isArray(val)) {
      for (const item of val) walk(item, visitor, seen)
    }
  }
}

export function extractTokens(payload: unknown): ExtractedTokens {
  const found: Record<string, string> = {}

  walk(
    payload,
    (key, val) => {
      if (typeof val !== 'string') return
      const lower = key.toLowerCase()
      if (!lower.includes('token') && lower !== 'jwt') return
      found[lower] = val
    },
    new WeakSet<object>(),
  )

  const accessToken =
    found['accesstoken'] ||
    found['access_token'] ||
    found['jwt'] ||
    found['token']

  const refreshToken = found['refreshtoken'] || found['refresh_token']

  return { accessToken, refreshToken }
}
