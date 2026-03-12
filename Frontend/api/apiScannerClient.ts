import type { ApiContract } from '@/types/apiContract'

export type ScanProjectPayload =
  | { projectPath: string; repoUrl?: never }
  | { repoUrl: string; projectPath?: never }

export class ApiScannerError extends Error {
  readonly status?: number
  readonly body?: unknown

  constructor(message: string, opts?: { status?: number; body?: unknown }) {
    super(message)
    this.name = 'ApiScannerError'
    this.status = opts?.status
    this.body = opts?.body
  }
}

function readScanUrl(): string {
  // Prefer calling our server-side proxy so GitHub tokens never reach the browser.
  return '/api/scanner/scan'
}

function mergeAbortSignals(signals: Array<AbortSignal | undefined>): AbortSignal | undefined {
  const defined = signals.filter(Boolean) as AbortSignal[]
  if (defined.length === 0) return undefined
  if (defined.length === 1) return defined[0]

  const controller = new AbortController()
  const onAbort = () => controller.abort()

  for (const s of defined) {
    if (s.aborted) {
      controller.abort()
      break
    }
    s.addEventListener('abort', onAbort, { once: true })
  }

  return controller.signal
}

async function readErrorBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    return await response.json().catch(() => null)
  }
  return await response.text().catch(() => '')
}

export async function scanProject(
  payload: ScanProjectPayload,
  opts?: {
    timeoutMs?: number
    signal?: AbortSignal
    baseUrl?: string
  },
): Promise<ApiContract> {
  const url = opts?.baseUrl
    ? `${String(opts.baseUrl).trim().replace(/\/+$/, '')}/scan`
    : readScanUrl()

  const timeoutMs = opts?.timeoutMs ?? 5 * 60 * 1000

  const timeoutController = new AbortController()
  const timer = setTimeout(() => timeoutController.abort(), timeoutMs)

  const mergedSignal = mergeAbortSignals([opts?.signal, timeoutController.signal])

  try {
    const headers: Record<string, string> = {
      'content-type': 'application/json',
      accept: 'application/json',
    }

    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: mergedSignal,
    })

    if (!res.ok) {
      const body = await readErrorBody(res)
      const msg = `Scan failed (${res.status} ${res.statusText})`
      throw new ApiScannerError(msg, { status: res.status, body })
    }

    const data = (await res.json().catch(() => null)) as unknown
    if (!data || typeof data !== 'object') {
      throw new ApiScannerError('Unexpected response from api-scanner-service.')
    }

    return data as ApiContract
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiScannerError('Scan cancelled or timed out.')
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
}
