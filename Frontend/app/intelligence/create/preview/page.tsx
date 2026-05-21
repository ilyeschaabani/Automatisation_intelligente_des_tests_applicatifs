 'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { generateUxScript, createUxEvaluation, executeUxEvaluation } from '@/lib/api-client'
import { Loader2, RotateCcw } from 'lucide-react'

export default function CreateUxPreviewPage() {
  const router = useRouter()
  const [script, setScript] = useState<string>('')
  const [platform, setPlatform] = useState<'WEB' | 'MOBILE'>('WEB')
  const [url, setUrl] = useState<string>('')
  const [description, setDescription] = useState<string>('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem('functional_preview_data')
      if (!raw) {
        router.push('/functional-evaluation/create')
        return
      }
      const parsed = JSON.parse(raw)
      setPlatform(parsed.platform ?? 'WEB')
      setUrl(parsed.url ?? '')
      setDescription(parsed.description ?? '')
      setScript(parsed.script ?? '')
    } catch (e) {
      router.push('/functional-evaluation/create')
    }
  }, [router])

  const handleRegenerate = async () => {
    setLoading(true)
    try {
      const newScript = await generateUxScript({ platform, url, description })
      setScript(newScript)
      // update session storage
      sessionStorage.setItem('functional_preview_data', JSON.stringify({ platform, url, description, script: newScript, runImmediately: true }))
    } catch (err) {
      console.warn('Regenerate failed', err)
      alert('Impossible de régénérer le script.')
    } finally {
      setLoading(false)
    }
  }

  const handleValidateAndExecute = async () => {
    setLoading(true)
    try {
      const payload: Partial<any> = {
        platform,
        url,
        description,
        generatedScript: script,
      }
      const created = await createUxEvaluation(payload)
      try {
        await executeUxEvaluation(created.id)
      } catch (e) {
        // continue to detail page; execution runs async
        console.warn('Execution trigger failed', e)
      }
      // cleanup preview data
      try { sessionStorage.removeItem('functional_preview_data') } catch (e) {}
      router.push(`/functional-evaluation/${created.id}`)
    } catch (err) {
      console.error('Validate failed', err)
      alert('Impossible de créer / exécuter l\'évaluation.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-6">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Aperçu du script généré</h2>
          <div className="text-sm text-muted-foreground">Vous pouvez modifier le script avant validation.</div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push('/functional-evaluation/create')}>Annuler</Button>
          <Button onClick={handleRegenerate} disabled={loading}>
            {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RotateCcw className="mr-2 h-4 w-4" />}Régénérer
          </Button>
          <Button onClick={handleValidateAndExecute} disabled={loading}>
            {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null} Valider et exécuter
          </Button>
        </div>
      </div>

      <div>
        <div className="mb-3 text-sm text-muted-foreground">Plateforme: {platform} · Cible: {url}</div>
        <Textarea className="font-mono bg-black text-white min-h-[420px]" value={script} onChange={(e) => setScript(e.target.value)} />
      </div>
    </div>
  )
}
