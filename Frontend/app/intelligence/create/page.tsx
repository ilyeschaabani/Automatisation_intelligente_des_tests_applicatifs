 'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Loader2, Sparkles } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { generateUxScript } from '@/lib/api-client'

type Platform = 'WEB' | 'MOBILE'

type FormState = {
  platform: Platform
  target: string
  description: string
}

const defaultFormState: FormState = {
  platform: 'WEB',
  target: '',
  description: '',
}

function toFunctionalType(platform: Platform): string {
  return platform === 'WEB' ? 'FUNCTIONAL_WEB' : 'FUNCTIONAL_MOBILE'
}

function buildTitle(platform: Platform, target: string) {
  const prefix = platform === 'WEB' ? 'Évaluation fonctionnelle Web' : 'Évaluation fonctionnelle Mobile'
  return target.trim() ? `${prefix} - ${target.trim()}` : prefix
}

export default function IntelligenceCreatePage() {
  // no project context required
  const router = useRouter()
  const [form, setForm] = useState<FormState>(defaultFormState)
  const [state, setState] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)

  const functionalType = useMemo(() => toFunctionalType(form.platform), [form.platform])
  const helperText = form.platform === 'WEB'
    ? 'Renseignez l’URL de départ ou la page cible de l’interface.'
    : 'Renseignez le package Android ou iOS ciblé par l’évaluation.'

  const submit = async () => {
    setState('submitting')
    setError(null)

    if (!form.target.trim()) {
      setState('error')
      setError(form.platform === 'WEB' ? 'L’URL cible est requise.' : 'Le package cible est requis.')
      return
    }
    const description = form.description.trim()
    const uxDescription = [
      `Plateforme: ${form.platform}`,
      `Cible: ${form.target.trim()}`,
      description ? `Description: ${description}` : null,
    ]
      .filter(Boolean)
      .join('\n')

    try {
      setState('submitting')
      const script = await generateUxScript({
        platform: form.platform,
        url: form.target.trim(),
        description: uxDescription,
      })
      // store script + payload in sessionStorage for preview page
      const previewData = {
        platform: form.platform,
        url: form.target,
        description: uxDescription,
        script,
      }
      try {
        sessionStorage.setItem('functional_preview_data', JSON.stringify(previewData))
      } catch (e) {
        // ignore
      }
      router.push('/functional-evaluation/create/preview')
    } catch (err) {
      setState('error')
      setError(err instanceof Error ? err.message : 'Impossible de générer le script LLM.')
    }
  }

  return (
    <div className="flex min-h-screen bg-[radial-gradient(circle_at_top_right,_rgba(59,130,246,0.08),_transparent_28%),linear-gradient(to_bottom_left,_rgba(16,185,129,0.08),_transparent_34%)]">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-6 lg:p-8">
          <div className="mx-auto grid w-full max-w-6xl gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <section className="rounded-3xl border border-border/60 bg-card/90 p-6 shadow-sm md:p-8">
              <div className="mb-6 flex items-center gap-3">
                <Button variant="ghost" size="icon" asChild>
                  <Link href="/functional-evaluation">
                    <ArrowLeft className="h-4 w-4" />
                  </Link>
                </Button>
                <div>
                  <Badge variant="success" className="mb-2 w-fit uppercase tracking-wide">
                    Création fonctionnelle
                  </Badge>
                  <h1 className="text-3xl font-semibold tracking-tight">Nouvelle évaluation fonctionnelle</h1>
                </div>
              </div>

              <p className="mb-8 max-w-2xl text-sm leading-6 text-muted-foreground md:text-base">
                Créez un cas de test fonctionnel indépendant en choisissant la suite cible, la plateforme, la cible à
                tester et la consigne d’analyse. Le backend générera ensuite le test correspondant.
              </p>

              <div className="space-y-6">
                <div className="space-y-3">
                  <Label>Plateforme</Label>
                  <RadioGroup
                    value={form.platform}
                    onValueChange={(value) => setForm((current) => ({ ...current, platform: value as Platform }))}
                    className="grid gap-3 md:grid-cols-2"
                  >
                    {[
                      { value: 'WEB', title: 'Fonctionnel Web', description: 'Navigation, lisibilité, parcours et validation des règles métier.' },
                      { value: 'MOBILE', title: 'Fonctionnel Mobile', description: 'Gestes, densité d’information et ergonomie mobile.' },
                    ].map((option) => (
                      <Label
                        key={option.value}
                        htmlFor={`platform-${option.value}`}
                        className={cn(
                          'flex cursor-pointer items-start gap-3 rounded-2xl border p-4 transition-colors',
                          form.platform === option.value ? 'border-primary bg-primary/5' : 'border-border/60 bg-muted/20',
                        )}
                      >
                        <RadioGroupItem id={`platform-${option.value}`} value={option.value} className="mt-1" />
                        <span>
                          <span className="block font-medium text-foreground">{option.title}</span>
                          <span className="mt-1 block text-sm text-muted-foreground">{option.description}</span>
                        </span>
                      </Label>
                    ))}
                  </RadioGroup>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="target">{form.platform === 'WEB' ? 'URL cible' : 'Package cible'}</Label>
                  <Input
                    id="target"
                    placeholder={form.platform === 'WEB' ? 'https://example.com/login' : 'com.example.app'}
                    value={form.target}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        target: event.target.value,
                      }))
                    }
                  />
                  <p className="text-xs text-muted-foreground">{helperText}</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="description">Décris les fonctionnalités à tester</Label>
                  <Textarea
                    id="description"
                    rows={5}
                    placeholder="Décrivez le parcours, les points d’attention ou le scénario fonctionnel à vérifier."
                    value={form.description}
                    onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                  />
                </div>

                {state === 'error' && error ? (
                  <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
                    {error}
                  </div>
                ) : null}

                <div className="flex flex-wrap gap-3">
                  <Button onClick={submit} disabled={state === 'submitting' || !form.target.trim() || !form.description.trim()}>
                    {state === 'submitting' ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Sparkles className="mr-2 h-4 w-4" />
                    )}
                    Lancer l’évaluation fonctionnelle
                  </Button>
                  <Button variant="outline" asChild>
                    <Link href="/intelligence">Retour à la liste</Link>
                  </Button>
                </div>
              </div>
            </section>

            <aside className="space-y-6">
              <Card className="border-border/60 bg-card/85 shadow-sm">
                <CardHeader>
                  <CardTitle>Aperçu de la requête</CardTitle>
                  <CardDescription>Le backend recevra un payload fonctionnel isolé du reste des tests.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4 text-sm">
                  <div className="rounded-2xl border border-border/60 bg-muted/30 p-4">
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">Type</div>
                    <div className="mt-2 font-medium">{functionalType}</div>
                  </div>
                  <div className="rounded-2xl border border-border/60 bg-muted/30 p-4">
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">Titre généré</div>
                    <div className="mt-2 font-medium">{buildTitle(form.platform, form.target)}</div>
                  </div>
                  <div className="rounded-2xl border border-border/60 bg-muted/30 p-4">
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">Description IA</div>
                    <div className="mt-2 whitespace-pre-wrap text-muted-foreground">
                      {form.description.trim() || 'Aucune consigne détaillée pour le moment.'}
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card className="border-dashed border-border/70 bg-card/70">
                <CardHeader>
                  <CardTitle>Ce qui est envoyé</CardTitle>
                </CardHeader>
                  <CardContent className="space-y-3 text-sm text-muted-foreground">
                    <p>• Aucun projet requis : la création fonctionnelle est indépendante.</p>
                    <p>• La première étape appelle le LLM puis ouvre l’aperçu avant validation.</p>
                  </CardContent>
              </Card>
            </aside>
          </div>
        </main>
      </div>
    </div>
  )
}