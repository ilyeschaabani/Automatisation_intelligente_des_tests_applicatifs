'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { KeyRound, Mail, ArrowLeft, Send } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { storeTokensFromPayload, clearTokens } from '@/lib/auth-storage'

export default function LoginPage() {
  const router = useRouter()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')

  const [showForgot, setShowForgot] = useState(false)
  const [forgotEmail, setForgotEmail] = useState('')
  const [forgotSubmitting, setForgotSubmitting] = useState(false)
  const [forgotMsg, setForgotMsg] = useState<{ text: string; success: boolean } | null>(null)

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)
    setError(null)

    const email = loginEmail.trim()
    const password = loginPassword

    try {
      const res = await fetch('/api/auth/signin', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ email, username: email, password }),
      })

      const text = await res.text().catch(() => '')
      let payload: unknown = null
      try {
        payload = text ? JSON.parse(text) : null
      } catch {
        payload = text
      }

      if (!res.ok) {
        const record = payload && typeof payload === 'object' ? (payload as Record<string, unknown>) : null
        const message =
          (record?.details && typeof record.details === 'object'
            ? (record.details as Record<string, unknown>).message
            : null) ??
          record?.message ??
          (typeof payload === 'string' ? payload : null) ??
          'Identifiants invalides'

        setError(typeof message === 'string' ? message : 'Identifiants invalides')
        return
      }

      storeTokensFromPayload(payload)
      router.push('/dashboard')
      router.refresh()
    } finally {
      setIsSubmitting(false)
    }
  }

  const returnToLogin = async () => {
    // Clear any active session (e.g. admin testing the flow) so we land on a
    // clean login form instead of bouncing back to the previous dashboard.
    await fetch('/api/auth/logout', { method: 'POST' }).catch(() => null)
    clearTokens()
    setShowForgot(false)
    setForgotMsg(null)
    setError(null)
    setLoginEmail('')
    setLoginPassword('')
    router.refresh()
  }

  const onForgotSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!forgotEmail.trim()) return
    setForgotSubmitting(true)
    setForgotMsg(null)

    try {
      const res = await fetch('/api/auth/password-reset/request', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail.trim() }),
      })
      const data = await res.json().catch(() => ({})) as any
      setForgotMsg({
        text: data.message || 'Demande envoyée.',
        success: res.ok,
      })
    } catch {
      setForgotMsg({ text: 'Erreur réseau. Réessayez.', success: false })
    } finally {
      setForgotSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6">
      <Card className="w-full max-w-md p-8">
        {!showForgot ? (
          <>
            {/* ─── Login Form ─── */}
            <div className="mb-6 text-center">
              <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-primary/10 text-primary mx-auto mb-4">
                <KeyRound size={28} />
              </div>
              <h1 className="text-2xl font-bold text-foreground">Connexion</h1>
              <p className="text-sm text-muted-foreground mt-1">
                Accédez à la plateforme d'automatisation des tests
              </p>
            </div>

            <form className="space-y-4" onSubmit={onSubmit}>
              {error && (
                <div className="rounded-lg bg-destructive/10 border border-destructive/20 px-4 py-3">
                  <p className="text-sm text-destructive">{error}</p>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="email">E-mail</Label>
                <Input id="email" type="email" value={loginEmail} onChange={(e) => setLoginEmail(e.target.value)} placeholder="votre.email@banque.com" required />
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">Mot de passe</Label>
                  <button
                    type="button"
                    onClick={() => { setShowForgot(true); setForgotMsg(null); setForgotEmail('') }}
                    className="text-xs text-primary hover:underline"
                  >
                    Mot de passe oublié ?
                  </button>
                </div>
                <Input id="password" type="password" value={loginPassword} onChange={(e) => setLoginPassword(e.target.value)} placeholder="••••••••" required />
              </div>

              <Button className="w-full" type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Connexion…' : 'Se connecter'}
              </Button>
            </form>

            <p className="mt-6 text-xs text-muted-foreground text-center">
              Contactez votre administrateur pour obtenir un compte.
            </p>
          </>
        ) : (
          <>
            {/* ─── Forgot Password Form ─── */}
            <div className="mb-6 text-center">
              <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-orange-100 dark:bg-orange-950/40 text-orange-600 mx-auto mb-4">
                <Mail size={28} />
              </div>
              <h1 className="text-2xl font-bold text-foreground">Mot de passe oublié</h1>
              <p className="text-sm text-muted-foreground mt-1">
                Saisissez votre e-mail. L'administrateur recevra votre demande de réinitialisation.
              </p>
            </div>

            <form className="space-y-4" onSubmit={onForgotSubmit}>
              {forgotMsg && (
                <div className={`rounded-lg border px-4 py-3 ${
                  forgotMsg.success
                    ? 'bg-green-50 border-green-200 dark:bg-green-950/30 dark:border-green-800'
                    : 'bg-destructive/10 border-destructive/20'
                }`}>
                  <p className={`text-sm ${forgotMsg.success ? 'text-green-700 dark:text-green-300' : 'text-destructive'}`}>
                    {forgotMsg.text}
                  </p>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="forgotEmail">Adresse e-mail</Label>
                <Input
                  id="forgotEmail"
                  type="email"
                  value={forgotEmail}
                  onChange={(e) => setForgotEmail(e.target.value)}
                  placeholder="votre.email@banque.com"
                  required
                />
              </div>

              <Button className="w-full gap-2" type="submit" disabled={forgotSubmitting || !forgotEmail.trim()}>
                <Send size={16} />
                {forgotSubmitting ? 'Envoi…' : 'Envoyer la demande'}
              </Button>

              <Button
                type="button"
                variant="ghost"
                className="w-full gap-2"
                onClick={returnToLogin}
              >
                <ArrowLeft size={16} />
                Retour à la connexion
              </Button>
            </form>
          </>
        )}
      </Card>
    </div>
  )
}
