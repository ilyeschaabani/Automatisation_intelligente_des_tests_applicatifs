'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { FolderOpen, Settings2, ListChecks, Rocket } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { toast } from '@/hooks/use-toast'

import {
  createCampaign,
  updateCampaign,
  getCampaign,
  getProjects,
  listCampaigns,
  type Project,
  type TestCampaignDto,
  type TestCampaignCreateRequest,
} from '@/lib/api-client'
import { environmentService } from '@/services/environments'
import { testCaseService } from '@/services/testCases'
import { testSuiteService } from '@/services/suites'
import type { Environment, TestCase } from '@/types/ms-gestion'

type CampaignCaseRow = TestCase & { suiteName: string }

type CampaignFormProps = {
  mode: 'create' | 'edit'
  campaignId?: number
  initialProjectId?: number
}

function SectionHeader({ icon: Icon, title, description }: { icon: React.ElementType; title: string; description: string }) {
  return (
    <div className="flex items-start gap-3 mb-5">
      <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-primary/10 text-primary shrink-0 mt-0.5">
        <Icon size={18} />
      </div>
      <div>
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
    </div>
  )
}

export function CampaignForm({ mode, campaignId, initialProjectId }: CampaignFormProps) {
  const router = useRouter()

  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(false)
  const [projectsError, setProjectsError] = useState<string | null>(null)

  const [campaignLoading, setCampaignLoading] = useState(mode === 'edit')
  const [campaign, setCampaign] = useState<TestCampaignDto | null>(null)

  const [projectId, setProjectId] = useState<number | null>(initialProjectId ?? null)
  const [name, setName] = useState('')
  const [appVersion, setAppVersion] = useState('')
  const [environmentId, setEnvironmentId] = useState<number | null>(null)
  const [triggerMode, setTriggerMode] = useState('MANUAL')

  const [environments, setEnvironments] = useState<Environment[]>([])
  const [environmentsLoading, setEnvironmentsLoading] = useState(false)
  const [environmentsError, setEnvironmentsError] = useState<string | null>(null)

  const [testCases, setTestCases] = useState<CampaignCaseRow[]>([])
  const [testCasesLoading, setTestCasesLoading] = useState(false)
  const [testCasesError, setTestCasesError] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Load projects list
  useEffect(() => {
    let cancelled = false
    const run = async () => {
      setProjectsLoading(true)
      setProjectsError(null)
      try {
        const data = await getProjects()
        if (!cancelled) setProjects(Array.isArray(data) ? data : [])
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load projects'
        if (!cancelled) {
          setProjects([])
          setProjectsError(message)
          toast({ title: 'Erreur', description: message, variant: 'destructive' })
        }
      } finally {
        if (!cancelled) setProjectsLoading(false)
      }
    }
    void run()
    return () => { cancelled = true }
  }, [])

  // In edit mode without initialProjectId, find the project by scanning campaigns
  useEffect(() => {
    if (mode !== 'edit' || !campaignId || projectId) return
    let cancelled = false
    const findProject = async () => {
      try {
        const allProjects = await getProjects()
        for (const p of allProjects) {
          const campaigns = await listCampaigns({ projectId: p.id }).catch(() => [])
          const found = Array.isArray(campaigns) && campaigns.some(c => c.id === campaignId)
          if (found && !cancelled) {
            setProjectId(p.id)
            return
          }
        }
      } catch { /* ignore */ }
    }
    void findProject()
    return () => { cancelled = true }
  }, [mode, campaignId, projectId])

  // Reset dependent fields when project changes in create mode
  useEffect(() => {
    if (mode === 'edit') return
    setEnvironmentId(null)
    setSelectedIds(new Set())
  }, [mode, projectId])

  // Load campaign data in edit mode
  useEffect(() => {
    if (mode !== 'edit' || !campaignId || !projectId) return
    let cancelled = false
    const run = async () => {
      setCampaignLoading(true)
      setSubmitError(null)
      try {
        const loaded = await getCampaign(projectId, campaignId)
        if (cancelled) return
        setCampaign(loaded)
        setName(loaded.name ?? '')
        setAppVersion(String(loaded.appVersion ?? ''))
        setTriggerMode(String(loaded.triggerMode ?? 'MANUAL'))
        setEnvironmentId(loaded.environmentId ?? null)
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Échec du chargement de la campagne'
        if (!cancelled) { setCampaign(null); setSubmitError(message) }
      } finally {
        if (!cancelled) setCampaignLoading(false)
      }
    }
    void run()
    return () => { cancelled = true }
  }, [campaignId, mode, projectId])

  // Load environments and test cases when project is selected
  useEffect(() => {
    if (!projectId) { setEnvironments([]); setTestCases([]); return }
    let cancelled = false
    const run = async () => {
      setEnvironmentsLoading(true)
      setEnvironmentsError(null)
      setTestCasesLoading(true)
      setTestCasesError(null)
      try {
        const [envs, suitesData] = await Promise.all([
          environmentService.getAll(projectId),
          testSuiteService.getAll(projectId),
        ])
        if (cancelled) return
        const suitesList = Array.isArray(suitesData) ? suitesData : []
        setEnvironments(Array.isArray(envs) ? envs : [])
        if (suitesList.length === 0) { setTestCases([]); return }
        const casesBySuite = await Promise.all(
          suitesList.map(async (suite) => {
            const cases = await testCaseService.getAll(suite.id)
            return (Array.isArray(cases) ? cases : []).map((tc) => ({ ...tc, suiteName: suite.name }))
          }),
        )
        if (cancelled) return
        setTestCases(casesBySuite.flat())
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load project data'
        if (!cancelled) {
          setEnvironments([]); setTestCases([])
          setEnvironmentsError(message); setTestCasesError(message)
          toast({ title: 'Erreur', description: message, variant: 'destructive' })
        }
      } finally {
        if (!cancelled) { setEnvironmentsLoading(false); setTestCasesLoading(false) }
      }
    }
    void run()
    return () => { cancelled = true }
  }, [projectId])

  const filteredTestCases = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return testCases
    return testCases.filter((tc) => {
      return (
        String(tc.title ?? '').toLowerCase().includes(q) ||
        String(tc.type ?? '').toLowerCase().includes(q) ||
        String(tc.priority ?? '').toLowerCase().includes(q) ||
        String(tc.suiteName ?? '').toLowerCase().includes(q) ||
        String(tc.riskLevel ?? '').toLowerCase().includes(q) ||
        String(tc.id).includes(q)
      )
    })
  }, [search, testCases])

  const allVisibleSelected = useMemo(() => {
    if (filteredTestCases.length === 0) return false
    return filteredTestCases.every((tc) => selectedIds.has(tc.id))
  }, [filteredTestCases, selectedIds])

  const toggleOne = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const selectAll = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const tc of filteredTestCases) next.add(tc.id)
      return next
    })
  }

  const clearAll = () => setSelectedIds(new Set())

  const canSubmit = Boolean(projectId && name.trim() && environmentId) && !submitting

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!projectId || !name.trim()) return
    if (!environmentId) { setSubmitError('Sélectionnez un environnement.'); return }

    setSubmitting(true)
    setSubmitError(null)
    try {
      if (mode === 'create') {
        const testCaseIds = Array.from(selectedIds)
        const createPayload: TestCampaignCreateRequest = {
          projectId,
          name: name.trim(),
          environmentId,
          appVersion: appVersion.trim() || null,
          triggerMode: triggerMode.trim() || 'MANUAL',
          testCaseIds,
        }
        const created = await createCampaign(createPayload)
        toast({ title: 'Campagne créée', description: `${created.name} a été créée avec succès.` })
        router.push(`/campaigns/${created.id}?projectId=${projectId}`)
        router.refresh()
      } else {
        if (!campaignId) throw new Error('ID campagne manquant')
        await updateCampaign(projectId, campaignId, {
          name: name.trim(),
          environmentId,
          appVersion: appVersion.trim() || null,
          triggerMode: triggerMode.trim() || 'MANUAL',
        })
        toast({ title: 'Campagne modifiée', description: 'Les modifications ont été enregistrées.' })
        router.push(`/campaigns/${campaignId}?projectId=${projectId}`)
        router.refresh()
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Échec de la sauvegarde'
      setSubmitError(message)
      toast({ title: 'Erreur', description: message, variant: 'destructive' })
    } finally {
      setSubmitting(false)
    }
  }

  const selectedEnv = environmentId ? environments.find(e => e.id === environmentId) as any : null

  return (
    <form onSubmit={onSubmit}>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-foreground">
          {mode === 'create' ? 'Nouvelle campagne' : 'Modifier la campagne'}
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          {mode === 'create'
            ? 'Configurez votre campagne de test en trois étapes.'
            : 'Mettez à jour les paramètres de la campagne.'}
        </p>
      </div>

      {campaignLoading && (
        <p className="text-sm text-muted-foreground mb-4">Chargement…</p>
      )}

      <div className="space-y-5">
        {/* Section 1: Identification */}
        <Card>
          <CardContent className="pt-6">
            <SectionHeader icon={FolderOpen} title="Identification" description="Choisissez le projet et nommez votre campagne." />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Projet <span className="text-destructive">*</span></Label>
                <Select
                  value={projectId ? String(projectId) : ''}
                  onValueChange={(v) => setProjectId(Number(v))}
                  disabled={projectsLoading || mode === 'edit'}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={projectsLoading ? 'Chargement…' : 'Sélectionner un projet'} />
                  </SelectTrigger>
                  <SelectContent>
                    {projects.length === 0 ? (
                      <SelectItem value="__none" disabled>Aucun projet trouvé</SelectItem>
                    ) : (
                      projects
                        .slice()
                        .sort((a, b) => String(a.name).localeCompare(String(b.name)))
                        .map((p) => (
                          <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
                        ))
                    )}
                  </SelectContent>
                </Select>
                {projectsError && <p className="text-xs text-destructive">{projectsError}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="campaignName">Nom <span className="text-destructive">*</span></Label>
                <Input
                  id="campaignName"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Ex: Tests API Paiement v2.5"
                  required
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Section 2: Configuration */}
        <Card>
          <CardContent className="pt-6">
            <SectionHeader icon={Settings2} title="Configuration" description="Définissez l'environnement cible et les paramètres d'exécution." />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="environment">Environnement <span className="text-destructive">*</span></Label>
                <Select
                  value={environmentId ? String(environmentId) : ''}
                  onValueChange={(value) => setEnvironmentId(Number(value))}
                  disabled={environmentsLoading || environments.length === 0 || !projectId}
                >
                  <SelectTrigger id="environment">
                    <SelectValue
                      placeholder={
                        !projectId ? 'Sélectionnez d\'abord un projet' :
                        environmentsLoading ? 'Chargement…' : 'Sélectionner un environnement'
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {environments.length === 0 ? (
                      <SelectItem value="__none" disabled>Aucun environnement disponible</SelectItem>
                    ) : (
                      environments.map((env) => (
                        <SelectItem key={env.id} value={String(env.id)}>
                          {env.name}
                          {(env as any).gitRepoUrl ? ` · ${(env as any).gitBranch ?? 'main'}` : ''}
                          {(env as any).databaseType ? ` · ${(env as any).databaseType}` : ''}
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
                {environmentsError && <p className="text-xs text-destructive">{environmentsError}</p>}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="campaignVersion">Version</Label>
                  <Input
                    id="campaignVersion"
                    value={appVersion}
                    onChange={(e) => setAppVersion(e.target.value)}
                    placeholder="v2.5"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="triggerType">Déclenchement</Label>
                  <Select value={triggerMode} onValueChange={setTriggerMode}>
                    <SelectTrigger id="triggerType">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MANUAL">Manuel</SelectItem>
                      <SelectItem value="SCHEDULED">Planifié</SelectItem>
                      <SelectItem value="CI">CI/CD</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            {selectedEnv && (
              <div className="mt-4 rounded-lg border border-border bg-muted/30 px-4 py-3 space-y-1.5">
                <p className="text-xs font-semibold text-foreground">Résumé de l'environnement</p>
                {selectedEnv.gitRepoUrl && (
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono text-foreground">Repo :</span> {selectedEnv.gitRepoUrl}
                    {' @ '}<span className="font-mono">{selectedEnv.gitBranch ?? 'main'}</span>
                  </p>
                )}
                {selectedEnv.baseUrlApi && (
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono text-foreground">API :</span> {selectedEnv.baseUrlApi}
                  </p>
                )}
                {selectedEnv.baseUrlWeb && (
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono text-foreground">Web :</span> {selectedEnv.baseUrlWeb}
                  </p>
                )}
                {selectedEnv.databaseType && (
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono text-foreground">DB :</span> {selectedEnv.databaseType}
                  </p>
                )}
                {!selectedEnv.gitRepoUrl && !selectedEnv.baseUrlApi && !selectedEnv.baseUrlWeb && (
                  <p className="text-xs text-orange-600">Aucune configuration détectée — vérifiez les paramètres de l'environnement.</p>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Section 3: Test cases (create mode only) */}
        {mode === 'create' && (
          <Card>
            <CardContent className="pt-6">
              <SectionHeader icon={ListChecks} title="Cas de test" description="Sélectionnez les tests à inclure dans cette campagne." />

              {!projectId ? (
                <div className="rounded-lg border-2 border-dashed border-border py-8 text-center">
                  <p className="text-sm text-muted-foreground">Sélectionnez un projet pour voir les cas de test disponibles.</p>
                </div>
              ) : testCasesLoading ? (
                <p className="text-sm text-muted-foreground">Chargement des cas de test…</p>
              ) : testCasesError ? (
                <p className="text-sm text-destructive">{testCasesError}</p>
              ) : testCases.length === 0 ? (
                <div className="rounded-lg border-2 border-dashed border-border py-8 text-center">
                  <p className="text-sm text-muted-foreground">Aucun cas de test dans ce projet.</p>
                  <p className="text-xs text-muted-foreground mt-1">Créez des cas de test dans les suites du projet.</p>
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between gap-3 mb-3">
                    <Input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Filtrer par titre, suite, type…"
                      className="max-w-sm"
                    />
                    <div className="flex items-center gap-2 shrink-0">
                      <span className="text-xs text-muted-foreground font-medium tabular-nums">
                        {selectedIds.size}/{testCases.length}
                      </span>
                      <Button variant="outline" size="sm" type="button" onClick={selectAll} disabled={filteredTestCases.length === 0}>
                        Tout
                      </Button>
                      <Button variant="outline" size="sm" type="button" onClick={clearAll} disabled={selectedIds.size === 0}>
                        Effacer
                      </Button>
                    </div>
                  </div>

                  <div className="rounded-lg border border-border overflow-hidden">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="w-10">
                            <Checkbox
                              checked={allVisibleSelected}
                              onCheckedChange={(checked) => { if (checked) selectAll(); else clearAll() }}
                              aria-label="Tout sélectionner"
                            />
                          </TableHead>
                          <TableHead>Titre</TableHead>
                          <TableHead className="w-36">Suite</TableHead>
                          <TableHead className="w-28">Type</TableHead>
                          <TableHead className="w-24">Priorité</TableHead>
                          <TableHead className="w-28">Risque</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {filteredTestCases.map((tc) => {
                          const isInactive = (tc as any).active === false
                          const typeBadgeColor =
                            tc.type === 'UNIT' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' :
                            tc.type === 'INTEGRATION' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300' :
                            'bg-gray-100 text-gray-600'
                          return (
                            <TableRow key={tc.id} className={isInactive ? 'opacity-50' : ''}>
                              <TableCell>
                                <Checkbox
                                  checked={selectedIds.has(tc.id)}
                                  onCheckedChange={() => toggleOne(tc.id)}
                                  aria-label={`Sélectionner ${tc.title}`}
                                  disabled={isInactive}
                                />
                              </TableCell>
                              <TableCell>
                                <div className="flex items-center gap-2">
                                  <span className="font-medium text-sm">{tc.title}</span>
                                  {isInactive && (
                                    <span className="text-[10px] bg-orange-100 text-orange-600 dark:bg-orange-900/40 dark:text-orange-300 px-1.5 py-0.5 rounded font-medium">
                                      inactif
                                    </span>
                                  )}
                                </div>
                              </TableCell>
                              <TableCell className="text-muted-foreground text-sm">{tc.suiteName}</TableCell>
                              <TableCell>
                                <span className={`text-xs font-mono px-2 py-0.5 rounded-full ${typeBadgeColor}`}>
                                  {tc.type ?? '—'}
                                </span>
                              </TableCell>
                              <TableCell className="text-sm">{tc.priority ?? '—'}</TableCell>
                              <TableCell className="text-sm">{tc.riskLevel ?? '—'}</TableCell>
                            </TableRow>
                          )
                        })}
                      </TableBody>
                    </Table>
                  </div>

                  {testCases.filter((tc) => !(tc as any).active && (tc as any).active !== undefined).length > 0 && (
                    <p className="text-xs text-orange-500 mt-2">
                      {testCases.filter((tc) => !(tc as any).active && (tc as any).active !== undefined).length} test(s) inactif(s) — ignoré(s) à l'exécution.
                    </p>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        )}

        {/* Submit */}
        {submitError && <p className="text-sm text-destructive">{submitError}</p>}

        <div className="flex items-center justify-end gap-3 pb-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.back()}
            disabled={submitting}
          >
            Annuler
          </Button>
          <Button type="submit" disabled={!canSubmit} className="gap-2">
            <Rocket size={16} />
            {submitting
              ? mode === 'create' ? 'Création…' : 'Enregistrement…'
              : mode === 'create' ? 'Créer la campagne' : 'Enregistrer'}
          </Button>
        </div>
      </div>
    </form>
  )
}
