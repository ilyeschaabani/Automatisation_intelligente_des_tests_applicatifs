'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Loader2, Sparkles } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { createFunctionalEvaluation, executeFunctionalEvaluation } from '@/lib/api-client'

export default function CreateEvaluationPage() {
  const router = useRouter()
  const [url, setUrl] = useState('')
  const [description, setDescription] = useState('')
  const [mobileMode, setMobileMode] = useState(false)
  const [state, setState] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    if (!url.trim()) {
      setState('error')
      setError('L\'URL est requise.')
      return
    }

    setState('submitting')
    setError(null)

    try {
      const desc = [
        description.trim(),
        mobileMode ? '[mobile]' : '',
      ].filter(Boolean).join('\n')

      const created = await createFunctionalEvaluation({
        platform: 'WEB',
        url: url.trim(),
        description: desc || undefined,
      })

      // Auto-start execution
      await executeFunctionalEvaluation(created.id)

      // Redirect to detail page — user will see live steps
      router.push(`/functional-evaluation/${created.id}`)
    } catch (err) {
      setState('error')
      setError(err instanceof Error ? err.message : 'Échec de la création.')
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-6 lg:p-8">
          <div className="mx-auto grid w-full max-w-5xl gap-6 xl:grid-cols-[1.2fr_0.8fr]">

            {/* ── Left: Form ──────────────────────────────────── */}
            <section className="rounded-2xl border border-border/60 bg-card/90 p-6 shadow-sm">
              <div className="mb-6 flex items-center gap-3">
                <Button variant="ghost" size="icon" asChild>
                  <Link href="/functional-evaluation"><ArrowLeft className="h-4 w-4" /></Link>
                </Button>
                <div>
                  <Badge variant="secondary" className="mb-1">Évaluation UX</Badge>
                  <h1 className="text-2xl font-semibold">Nouvelle évaluation</h1>
                </div>
              </div>

              <p className="mb-6 text-sm text-muted-foreground">
                Fournissez juste une URL. L'IA va explorer l'application de façon autonome,
                prendre des screenshots à chaque étape, et rédiger un rapport UX complet.
              </p>

              <div className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="url">URL à tester</Label>
                  <Input
                    id="url"
                    placeholder="https://example.com"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="description">
                    Instructions pour l'IA <span className="text-muted-foreground text-xs">(optionnel)</span>
                  </Label>
                  <Textarea
                    id="description"
                    rows={3}
                    placeholder="Ex: Concentre-toi sur le parcours d'inscription et les messages d'erreur"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>

                <div className="flex items-center gap-3">
                  <Checkbox
                    id="mobile"
                    checked={mobileMode}
                    onCheckedChange={(v) => setMobileMode(v === true)}
                  />
                  <Label htmlFor="mobile" className="text-sm cursor-pointer">
                    Simuler un écran mobile (iPhone 14 — 390×844)
                  </Label>
                </div>

                {state === 'error' && error && (
                  <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                    {error}
                  </div>
                )}

                <Button onClick={submit} disabled={state === 'submitting' || !url.trim()} className="w-full">
                  {state === 'submitting' ? (
                    <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Lancement en cours…</>
                  ) : (
                    <><Sparkles className="mr-2 h-4 w-4" /> Lancer l'exploration IA</>
                  )}
                </Button>
              </div>
            </section>

            {/* ── Right: Explanation ──────────────────────────── */}
            <aside className="space-y-4">
              <Card className="border-border/60 bg-card/85">
                <CardHeader>
                  <CardTitle>Comment ça marche ?</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm text-muted-foreground">
                  <div className="flex gap-3">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary text-xs font-bold">1</div>
                    <p>L'IA ouvre votre URL dans un vrai navigateur Chrome</p>
                  </div>
                  <div className="flex gap-3">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary text-xs font-bold">2</div>
                    <p>Elle regarde le screenshot et décide quoi faire ensuite (cliquer, remplir un formulaire…)</p>
                  </div>
                  <div className="flex gap-3">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary text-xs font-bold">3</div>
                    <p>Elle prend un screenshot à chaque étape — vous voyez tout en temps réel</p>
                  </div>
                  <div className="flex gap-3">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary text-xs font-bold">4</div>
                    <p>À la fin, elle rédige un rapport UX complet avec score /10</p>
                  </div>
                </CardContent>
              </Card>

              <Card className="border-dashed border-border/70 bg-card/70">
                <CardHeader>
                  <CardTitle className="text-sm">Propulsé par</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground space-y-1">
                  <p>🧠 <strong>Gemini 1.5 Flash</strong> — vision + raisonnement</p>
                  <p>🌐 <strong>Chrome headless</strong> — rendu JS/CSS complet</p>
                  <p>📸 <strong>Screenshots temps réel</strong> — suivi étape par étape</p>
                </CardContent>
              </Card>
            </aside>
          </div>
        </main>
      </div>
    </div>
  )
}
