'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Loader2 } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { createFunctionalEvaluation, executeFunctionalEvaluation, uploadApk } from '@/lib/api-client'

export default function CreateEvaluationPage() {
  const router = useRouter()
  const [url, setUrl] = useState('')
  const [description, setDescription] = useState('')
  const [platform, setPlatform] = useState<'WEB' | 'WEB_MOBILE' | 'MOBILE_APP'>('WEB')
  const [apkFile, setApkFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [state, setState] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    if (platform === 'MOBILE_APP') {
      if (!apkFile) {
        setState('error')
        setError('Veuillez sélectionner un fichier APK.')
        return
      }
    } else {
      if (!url.trim()) {
        setState('error')
        setError("L'URL est requise.")
        return
      }
    }

    setState('submitting')
    setError(null)

    try {
      if (platform === 'MOBILE_APP') {
        setUploading(true)
        const { path } = await uploadApk(apkFile!)
        setUploading(false)
        const created = await createFunctionalEvaluation({
          platform: 'MOBILE_APP',
          url: apkFile!.name,
          description: description.trim() || undefined,
          apkPath: path,
        })
        await executeFunctionalEvaluation(created.id)
        router.push(`/functional-evaluation/${created.id}`)
      } else {
        const created = await createFunctionalEvaluation({
          platform,
          url: url.trim(),
          description: description.trim() || undefined,
        })
        await executeFunctionalEvaluation(created.id)
        router.push(`/functional-evaluation/${created.id}`)
      }
    } catch (err) {
      setUploading(false)
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
          <div className="mx-auto w-full max-w-2xl">

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
                Fournissez une URL ou un fichier APK. Le système explore l'application de façon autonome,
                prend des captures à chaque étape, et génère un rapport UX complet.
              </p>

              <div className="space-y-5">
                <div className="space-y-2">
                  <Label>Mode d'évaluation</Label>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => setPlatform('WEB')}
                      className={`flex-1 flex items-center gap-3 rounded-xl border-2 p-3 transition-all text-left
                        ${platform === 'WEB'
                          ? 'border-primary bg-primary/5 shadow-sm'
                          : 'border-border/60 hover:border-border'}`}
                    >
                      <div>
                        <p className="text-sm font-medium">Desktop</p>
                        <p className="text-[11px] text-muted-foreground">1280×800</p>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => setPlatform('WEB_MOBILE')}
                      className={`flex-1 flex items-center gap-3 rounded-xl border-2 p-3 transition-all text-left
                        ${platform === 'WEB_MOBILE'
                          ? 'border-primary bg-primary/5 shadow-sm'
                          : 'border-border/60 hover:border-border'}`}
                    >
                      <div>
                        <p className="text-sm font-medium">Mobile Web</p>
                        <p className="text-[11px] text-muted-foreground">iPhone 14 — 390×844</p>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => setPlatform('MOBILE_APP')}
                      className={`flex-1 flex items-center gap-3 rounded-xl border-2 p-3 transition-all text-left
                        ${platform === 'MOBILE_APP'
                          ? 'border-primary bg-primary/5 shadow-sm'
                          : 'border-border/60 hover:border-border'}`}
                    >
                      <div>
                        <p className="text-sm font-medium">App Android</p>
                        <p className="text-[11px] text-muted-foreground">Fichier APK</p>
                      </div>
                    </button>
                  </div>
                </div>

                {platform === 'MOBILE_APP' ? (
                  <div className="space-y-2">
                    <Label>Fichier APK</Label>
                    <div
                      className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-border/60 p-8 hover:border-primary/40 transition-colors cursor-pointer"
                      onClick={() => document.getElementById('apk-input')?.click()}
                      onDragOver={(e) => { e.preventDefault(); e.stopPropagation() }}
                      onDrop={(e) => {
                        e.preventDefault()
                        const file = e.dataTransfer.files[0]
                        if (file?.name.endsWith('.apk')) setApkFile(file)
                      }}
                    >
                      <input
                        id="apk-input"
                        type="file"
                        accept=".apk"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0]
                          if (file) setApkFile(file)
                        }}
                      />
                      {apkFile ? (
                        <div className="text-center">
                          <p className="text-sm font-medium">{apkFile.name}</p>
                          <p className="text-xs text-muted-foreground mt-1">{(apkFile.size / 1024 / 1024).toFixed(1)} MB</p>
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); setApkFile(null) }}
                            className="mt-2 text-xs text-destructive hover:underline"
                          >
                            Supprimer
                          </button>
                        </div>
                      ) : (
                        <div className="text-center">
                          <p className="text-sm text-muted-foreground">Glissez votre fichier APK ici</p>
                          <p className="text-xs text-muted-foreground mt-1">ou cliquez pour sélectionner</p>
                        </div>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <Label htmlFor="url">URL à tester</Label>
                    <Input
                      id="url"
                      placeholder="https://example.com"
                      value={url}
                      onChange={(e) => setUrl(e.target.value)}
                    />
                  </div>
                )}

                <div className="space-y-2">
                  <Label htmlFor="description">
                    Instructions supplémentaires <span className="text-muted-foreground text-xs">(optionnel)</span>
                  </Label>
                  <Textarea
                    id="description"
                    rows={3}
                    placeholder="Ex: Concentre-toi sur le parcours d'inscription et les messages d'erreur"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>

                {state === 'error' && error && (
                  <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                    {error}
                  </div>
                )}

                <Button onClick={submit} disabled={state === 'submitting' || uploading || (platform === 'MOBILE_APP' ? !apkFile : !url.trim())} className="w-full">
                  {uploading ? (
                    <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Upload de l'APK…</>
                  ) : state === 'submitting' ? (
                    <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Lancement en cours…</>
                  ) : (
                    <>Lancer l'évaluation</>
                  )}
                </Button>
              </div>
            </section>

          </div>
        </main>
      </div>
    </div>
  )
}
