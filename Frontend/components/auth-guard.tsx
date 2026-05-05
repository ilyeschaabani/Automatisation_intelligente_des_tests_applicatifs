'use client'

import { useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'

import { getAccessToken } from '@/lib/auth-storage'

type AuthGuardProps = {
	children: React.ReactNode
}

const PUBLIC_PATHS = new Set(['/login', '/signup'])

const hasReadableCookie = (name: string): boolean => {
	if (typeof document === 'undefined') return false
	return document.cookie.split(';').some((part) => part.trim().startsWith(`${name}=`))
}

export function AuthGuard({ children }: AuthGuardProps) {
	const router = useRouter()
	const pathname = usePathname()
	const [ready, setReady] = useState(false)
	const isPublic = useMemo(() => PUBLIC_PATHS.has(pathname), [pathname])

	useEffect(() => {
		if (isPublic) {
			setReady(true)
			return
		}

		const token = getAccessToken()
		const hasCookie = hasReadableCookie('access_token') || hasReadableCookie('refresh_token')

		if (!token && !hasCookie) {
			router.replace('/login')
			return
		}

		setReady(true)
	}, [isPublic, router])

	if (!ready) {
		return (
			<div className="min-h-screen flex items-center justify-center text-sm text-muted-foreground">
				Checking session...
			</div>
		)
	}

	return <>{children}</>
}
