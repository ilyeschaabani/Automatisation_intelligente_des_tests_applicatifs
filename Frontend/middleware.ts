import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const PUBLIC_PATHS = ['/login', '/signup']

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl

  // Allow Next internals and static files.
  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/favicon') ||
    pathname.startsWith('/assets')
  ) {
    return NextResponse.next()
  }

  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'))
  const hasAccessToken = Boolean(request.cookies.get('access_token')?.value)
  const hasRefreshToken = Boolean(request.cookies.get('refresh_token')?.value)
  const isAuthed = hasAccessToken || hasRefreshToken

  // If not logged in, force to login for all non-public routes.
  if (!isAuthed && !isPublic) {
    const url = request.nextUrl.clone()
    url.pathname = '/login'
    return NextResponse.redirect(url)
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/((?!api).*)'],
}
