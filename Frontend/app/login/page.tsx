'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { storeTokensFromPayload } from '@/lib/auth-storage'

export default function LoginPage() {
  const router = useRouter()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)
    setError(null)

    const form = new FormData(e.currentTarget)
    const email = String(form.get('email') ?? '')
    const password = String(form.get('password') ?? '')

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

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6">
      <Card className="w-full max-w-md p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-foreground">Connexion</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Utilisez vos identifiants pour accéder à la plateforme.
          </p>
        </div>

        <form className="space-y-4" onSubmit={onSubmit}>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <div className="space-y-2">
            <Label htmlFor="email">E-mail</Label>
            <Input id="email" name="email" type="email" placeholder="admin@bankingcorp.com" required />
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Mot de passe</Label>
            <Input id="password" name="password" type="password" placeholder="••••••••" required />
          </div>

          <Button className="w-full" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Connexion…' : 'Se connecter'}
          </Button>
        </form>

        <p className="mt-6 text-xs text-muted-foreground text-center">
          Contactez votre administrateur pour obtenir un compte.
        </p>
      </Card>
    </div>
  )
}
