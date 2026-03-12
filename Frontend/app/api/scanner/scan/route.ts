import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const API_SCANNER_SERVICE_URL =
  process.env.API_SCANNER_SERVICE_URL ??
  process.env.API_SCANNER_URL ??
  'http://localhost:8099'

// This is the existing backend the app already uses for GitHub linkup.
// We will use it server-side (with the user's cookies) to retrieve a linked token,
// without ever exposing it to the browser.
const BACKEND_API_URL =
  process.env.GITHUB_BACKEND_API_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  ''

type ScanPayload =
  | { projectPath: string; repoUrl?: never }
  | { repoUrl: string; projectPath?: never }

function normalizeBaseUrl(url: string): string {
  return String(url || '').trim().replace(/\/+$/, '')
}

function forwardSetCookie(upstream: Response, response: NextResponse) {
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
    return
  }

  const maybeCookie = upstream.headers.get('set-cookie')
  if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
}

async function readUpstreamError(upstream: Response): Promise<string> {
  const contentType = upstream.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    const data = (await upstream.json().catch(() => null)) as unknown
    const text = data ? JSON.stringify(data).slice(0, 4000) : ''
    return text || `${upstream.status} ${upstream.statusText}`
  }
  const text = await upstream.text().catch(() => '')
  return text || `${upstream.status} ${upstream.statusText}`
}

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const cookieHeader = cookieStore.toString()

  const raw = await request.text().catch(() => '')
  let parsed: unknown = null
  try {
    parsed = raw ? JSON.parse(raw) : null
  } catch {
    return NextResponse.json({ message: 'Invalid JSON body' }, { status: 400 })
  }

  if (!parsed || typeof parsed !== 'object') {
    return NextResponse.json({ message: 'Body is required' }, { status: 400 })
  }

  const body = parsed as Record<string, unknown>
  const repoUrl = typeof body.repoUrl === 'string' ? body.repoUrl.trim() : ''
  const projectPath = typeof body.projectPath === 'string' ? body.projectPath.trim() : ''

  const payload: ScanPayload | null = repoUrl
    ? { repoUrl }
    : projectPath
      ? { projectPath }
      : null

  if (!payload) {
    return NextResponse.json(
      { message: 'Provide either repoUrl or projectPath.' },
      { status: 400 },
    )
  }

  const scannerBase = normalizeBaseUrl(API_SCANNER_SERVICE_URL)
  const scannerUrl = `${scannerBase}/scan`

  const backendBase = normalizeBaseUrl(BACKEND_API_URL)
  const githubScanUrl = backendBase ? `${backendBase}/api/github/scan` : ''

  const controller = new AbortController()
  const timeoutMs = 5 * 60 * 1000
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const debugHeaders: Record<string, string> = {
      'cache-control': 'no-store',
    }

    // Option A:
    // - Git repo scans go through the auth microservice, which decrypts the stored GitHub token
    //   and calls api-scanner-service server-to-server.
    // - Local path scans call api-scanner-service directly.

    const accept = request.headers.get('accept') ?? 'application/json'

    const upstream =
      'repoUrl' in payload
        ? await fetch(githubScanUrl, {
            method: 'POST',
            headers: {
              cookie: cookieHeader,
              'content-type': 'application/json',
              accept,
            },
            body: JSON.stringify({ repoUrl: payload.repoUrl }),
            cache: 'no-store',
            signal: controller.signal,
          })
        : await fetch(scannerUrl, {
            method: 'POST',
            headers: {
              'content-type': 'application/json',
              accept,
            },
            body: JSON.stringify(payload),
            signal: controller.signal,
          })

    if (!upstream.ok) {
      const message = await readUpstreamError(upstream)
      const response = NextResponse.json(
        {
          message: `Scan failed (${upstream.status} ${upstream.statusText})`,
          details: message,
        },
        { status: upstream.status },
      )

      forwardSetCookie(upstream, response)
      response.headers.set('cache-control', 'no-store')
      response.headers.set('x-scan-mode', 'repoUrl' in payload ? 'git-via-auth' : 'local-direct')
      return response
    }

    const response = new NextResponse(upstream.body, {
      status: upstream.status,
      headers: {
        'content-type': upstream.headers.get('content-type') ?? 'application/json',
        'cache-control': 'no-store',
        'x-scan-mode': 'repoUrl' in payload ? 'git-via-auth' : 'local-direct',
      },
    })

    forwardSetCookie(upstream, response)
    return response
  } catch (error) {
    const isAbort = error instanceof DOMException && error.name === 'AbortError'
    return NextResponse.json(
      {
        message: isAbort ? 'Scan timed out.' : 'Scan failed.',
      },
      { status: isAbort ? 504 : 500, headers: { 'cache-control': 'no-store' } },
    )
  } finally {
    clearTimeout(timer)
  }
}
