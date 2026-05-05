import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'

import { getAccessToken } from '@/lib/auth-storage'

const API_BASE_URL =
  process.env.NEXT_PUBLIC_MS_GESTION_URL ?? 'http://localhost:8082'

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Intercepteur pour ajouter le token JWT à chaque requête
axiosClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Intercepteur de réponse pour normaliser les erreurs
const toErrorMessage = (error: AxiosError): string => {
  if (error.response) {
    const data = error.response.data as unknown
    if (typeof data === 'string' && data.trim()) return data
    if (data && typeof data === 'object') {
      const record = data as Record<string, unknown>
      const candidate =
        record.message ??
        record.error ??
        (record.details &&
        typeof record.details === 'object' &&
        (record.details as Record<string, unknown>).message)

      if (typeof candidate === 'string' && candidate.trim()) return candidate
    }
    return `Request failed (${error.response.status})`
  }
  if (error.message && error.message.trim()) return error.message
  return 'Network error. Please try again.'
}

axiosClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => Promise.reject(new Error(toErrorMessage(error))),
)

export default axiosClient