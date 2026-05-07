export function buildMsGestionHeaders(
  cookieStore: { toString: () => string; get: (name: string) => { value: string } | undefined },
  request: Request,
): Record<string, string> {
  const headers: Record<string, string> = {
    cookie: cookieStore.toString(),
    accept: request.headers.get('accept') ?? 'application/json',
  }

  const accessToken = cookieStore.get('access_token')?.value
  if (accessToken) {
    headers.authorization = `Bearer ${accessToken}`
  }

  return headers
}