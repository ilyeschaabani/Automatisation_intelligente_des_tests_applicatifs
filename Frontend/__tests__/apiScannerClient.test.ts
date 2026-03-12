import { describe, expect, it, vi } from 'vitest'

import { scanProject, ApiScannerError } from '@/api/apiScannerClient'

function makeJsonResponse(body: unknown, init?: { status?: number; headers?: Record<string, string> }) {
  const status = init?.status ?? 200
  const headers = new Headers({ 'content-type': 'application/json', ...(init?.headers ?? {}) })
  return new Response(JSON.stringify(body), { status, headers })
}

describe('apiScannerClient.scanProject', () => {
  it('returns ApiContract on success', async () => {
    const fetchMock = vi.fn(async () =>
      makeJsonResponse({
        source: 'repo',
        metadata: { framework: 'SPRING', hints: {} },
        generatedAt: new Date().toISOString(),
        endpoints: [{ method: 'GET', path: '/api/x', controller: 'C', handler: 'h' }],
        issues: [],
      }),
    )

    ;(globalThis as any).fetch = fetchMock

    const contract = await scanProject({ repoUrl: 'https://github.com/user/repo.git' })

    expect(contract.metadata.framework).toBe('SPRING')
    expect(contract.endpoints).toHaveLength(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const [url, init] = fetchMock.mock.calls[0] as any
    expect(String(url)).toBe('/api/scanner/scan')
    expect(init?.method).toBe('POST')
  })

  it('throws ApiScannerError with status/body on HTTP error', async () => {
    const fetchMock = vi.fn(async () =>
      makeJsonResponse({ message: 'No scanner found for framework: UNKNOWN' }, { status: 500 }),
    )

    ;(globalThis as any).fetch = fetchMock

    await expect(
      scanProject({ projectPath: 'C:\\path\\to\\project' }),
    ).rejects.toBeInstanceOf(ApiScannerError)

    try {
      await scanProject({ projectPath: 'C:\\path\\to\\project' })
    } catch (e) {
      const err = e as ApiScannerError
      expect(err.status).toBe(500)
      expect(err.body).toEqual({ message: 'No scanner found for framework: UNKNOWN' })
    }
  })
})
