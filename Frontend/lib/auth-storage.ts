import { extractTokens, type ExtractedTokens } from '@/lib/auth-tokens'

const ACCESS_TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

const isBrowser = () => typeof window !== 'undefined'

const safeGetItem = (key: string): string | null => {
	if (!isBrowser()) return null
	try {
		return window.localStorage.getItem(key)
	} catch {
		return null
	}
}

const safeSetItem = (key: string, value: string) => {
	if (!isBrowser()) return
	try {
		window.localStorage.setItem(key, value)
	} catch {
		return
	}
}

const safeRemoveItem = (key: string) => {
	if (!isBrowser()) return
	try {
		window.localStorage.removeItem(key)
	} catch {
		return
	}
}

export const getAccessToken = (): string | null => safeGetItem(ACCESS_TOKEN_KEY)

export const getRefreshToken = (): string | null => safeGetItem(REFRESH_TOKEN_KEY)

export const setAccessToken = (token?: string | null) => {
	if (!token) {
		safeRemoveItem(ACCESS_TOKEN_KEY)
		return
	}
	safeSetItem(ACCESS_TOKEN_KEY, token)
}

export const setRefreshToken = (token?: string | null) => {
	if (!token) {
		safeRemoveItem(REFRESH_TOKEN_KEY)
		return
	}
	safeSetItem(REFRESH_TOKEN_KEY, token)
}

export const setTokens = (tokens: ExtractedTokens) => {
	if (tokens.accessToken) setAccessToken(tokens.accessToken)
	if (tokens.refreshToken) setRefreshToken(tokens.refreshToken)
}

export const clearTokens = () => {
	safeRemoveItem(ACCESS_TOKEN_KEY)
	safeRemoveItem(REFRESH_TOKEN_KEY)
}

export const isAuthenticated = (): boolean => Boolean(getAccessToken())

export const storeTokensFromPayload = (payload: unknown): ExtractedTokens => {
	const tokens = extractTokens(payload)
	setTokens(tokens)
	return tokens
}
