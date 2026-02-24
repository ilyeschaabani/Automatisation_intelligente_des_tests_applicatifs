'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Search } from 'lucide-react'

import {
  createProject,
  getProjects,
  inferGitProviderFromUrl,
  resolveRepo,
  type Project,
  type ProjectType,
  type SourceType,
} from '@/lib/api-client'

const statusStyle: Record<'Deployed' | 'Not deployed', string> = {
  Deployed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  'Not deployed': 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function getProvider(project: Project): string {
  return String(project.sourceType ?? '—')
}

function getRepositoryUrl(project: Project): string {
  return String(project.repositoryUrl ?? '—')
}

function getType(project: Project): string {
  const value = String(project.projectType ?? '—')
  if (value === 'WEB') return 'WEB'
  if (value === 'MOBILE') return 'MOBILE'
  if (value === 'API') return 'API'
  if (value === 'DESKTOP') return 'DESKTOP'
  if (value === 'OTHER') return 'OTHER'
  return value
}

function getDefaultBranch(project: Project): string {
  return String(project.technologyStack ?? '—')
}

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isResolvingRepo, setIsResolvingRepo] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [projectType, setProjectType] = useState<ProjectType>('WEB')
  const [sourceType, setSourceType] = useState<SourceType>('GIT')
  const [gitTokenId, setGitTokenId] = useState('')
  const [technologyStack, setTechnologyStack] = useState('')
  const [deployed, setDeployed] = useState(false)

  const loadProjects = async () => {
    setIsLoading(true)
    try {
      const data = await getProjects()
      setProjects(Array.isArray(data) ? data : [])
    } catch (error) {
      console.error('Failed to load projects', error)
      setProjects([])
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadProjects()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filteredProjects = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) return projects

    return projects.filter((project) => {
      const projectName = String(project.name ?? '').toLowerCase()
      const repoUrl = String(project.repositoryUrl ?? '').toLowerCase()
      return projectName.includes(query) || repoUrl.includes(query)
    })
  }, [projects, searchQuery])

  const resetForm = () => {
    setFormError(null)
    setName('')
    setRepositoryUrl('')
    setProjectType('WEB')
    setSourceType('GIT')
    setGitTokenId('')
    setTechnologyStack('')
    setDeployed(false)
  }

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)
    setFormError(null)

    try {
      await createProject({
        name,
        projectType,
        sourceType,
        repositoryUrl: repositoryUrl.trim() ? repositoryUrl.trim() : null,
        gitTokenId: gitTokenId.trim() ? gitTokenId.trim() : null,
        technologyStack: technologyStack.trim() ? technologyStack.trim() : null,
        deployed,
      })
      setIsCreateOpen(false)
      resetForm()
      await loadProjects()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to create project'
      console.error('Create project failed', error)
      setFormError(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  const onAutoFill = async () => {
    const url = repositoryUrl.trim()
    if (!url) return

    setIsResolvingRepo(true)
    setFormError(null)
    try {
      if (sourceType !== 'GIT') {
        setFormError('Auto-fill is available only when Source type is GIT.')
        return
      }

      const inferredProvider = inferGitProviderFromUrl(url)
      if (!inferredProvider) {
        setFormError('Cannot infer provider from URL. Use a GitHub/GitLab URL.')
        return
      }

      const resolved = await resolveRepo({ repositoryUrl: url, provider: inferredProvider })

      const resolvedName = resolved.repo ?? resolved.name
      const resolvedUrl = resolved.htmlUrl ?? resolved.repositoryUrl

      if (!name.trim() && resolvedName) setName(resolvedName)
      if (resolvedUrl) setRepositoryUrl(resolvedUrl)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to auto-fill'
      console.error('Failed to auto-fill repository details', error)
      setFormError(message)
    } finally {
      setIsResolvingRepo(false)
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Projects</h1>
              <p className="text-muted-foreground mt-1">
                Organize campaigns, environments, and executions by application
              </p>
            </div>

            <Sheet open={isCreateOpen} onOpenChange={setIsCreateOpen}>
              <SheetTrigger asChild>
                <Button className="gap-2" onClick={() => setIsCreateOpen(true)}>
                  <Plus size={20} />
                  New Project
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="sm:max-w-md">
                <SheetHeader>
                  <SheetTitle>Add New Project</SheetTitle>
                  <SheetDescription>
                    Create a project to group campaigns and environments
                  </SheetDescription>
                </SheetHeader>

                <form className="mt-6 space-y-6" onSubmit={onSubmit}>
                  <div className="space-y-2">
                    <Label htmlFor="projectName">Project name</Label>
                    <Input
                      id="projectName"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="Banking Web App"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="projectRepoUrl">Repository URL</Label>
                    <div className="flex gap-2">
                      <Input
                        id="projectRepoUrl"
                        value={repositoryUrl}
                        onChange={(e) => setRepositoryUrl(e.target.value)}
                        placeholder="https://github.com/org/repo"
                      />
                      <Button
                        type="button"
                        variant="outline"
                        onClick={onAutoFill}
                        disabled={isResolvingRepo || !repositoryUrl.trim()}
                      >
                        {isResolvingRepo ? 'Auto-filling…' : 'Auto-fill'}
                      </Button>
                    </div>
                  </div>

                  {formError ? (
                    <p className="text-sm text-destructive">{formError}</p>
                  ) : null}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>Project type</Label>
                      <Select value={projectType} onValueChange={(v) => setProjectType(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select type" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="WEB">WEB</SelectItem>
                          <SelectItem value="MOBILE">MOBILE</SelectItem>
                          <SelectItem value="API">API</SelectItem>
                          <SelectItem value="DESKTOP">DESKTOP</SelectItem>
                          <SelectItem value="OTHER">OTHER</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="space-y-2">
                      <Label>Source type</Label>
                      <Select value={sourceType} onValueChange={(v) => setSourceType(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select source" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="GIT">GIT</SelectItem>
                          <SelectItem value="URL">URL</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="projectGitToken">Git token id</Label>
                    <Input
                      id="projectGitToken"
                      value={gitTokenId}
                      onChange={(e) => setGitTokenId(e.target.value)}
                      placeholder="Optional"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="projectTechStack">Technology stack</Label>
                    <Input
                      id="projectTechStack"
                      value={technologyStack}
                      onChange={(e) => setTechnologyStack(e.target.value)}
                      placeholder="Optional"
                    />
                  </div>

                  <div className="flex items-center justify-between rounded-lg border border-border p-3">
                    <div>
                      <p className="text-sm font-medium text-foreground">Deployed</p>
                      <p className="text-xs text-muted-foreground">Is the app currently deployed?</p>
                    </div>
                    <Switch checked={deployed} onCheckedChange={setDeployed} />
                  </div>
                  <SheetFooter className="pt-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setIsCreateOpen(false)
                        resetForm()
                      }}
                    >
                      Cancel
                    </Button>
                    <Button type="submit" disabled={isSubmitting}>
                      {isSubmitting ? 'Creating…' : 'Create project'}
                    </Button>
                  </SheetFooter>
                </form>
              </SheetContent>
            </Sheet>
          </div>

          <div className="flex flex-col md:flex-row gap-4 mb-8">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground size-5" />
              <Input
                placeholder="Search projects..."
                className="pl-10"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {!isLoading && filteredProjects.length === 0 ? (
              <Card className="p-6">
                <p className="text-sm text-muted-foreground">No projects found.</p>
              </Card>
            ) : null}

            {filteredProjects.map((project) => (
              <Card key={project.id} className="p-6">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-lg font-semibold text-foreground truncate">
                      {project.name}
                    </p>
                    <p className="text-sm text-muted-foreground mt-1 truncate">
                      {getRepositoryUrl(project)}
                    </p>
                  </div>
                  <Badge
                    variant="outline"
                    className={statusStyle[project.deployed ? 'Deployed' : 'Not deployed']}
                  >
                    {project.deployed ? 'Deployed' : 'Not deployed'}
                  </Badge>
                </div>

                <div className="grid grid-cols-3 gap-4 mt-6">
                  <div>
                    <p className="text-xs text-muted-foreground">Type</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {getType(project)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Source</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {getProvider(project)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Tech stack</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {getDefaultBranch(project)}
                    </p>
                  </div>
                </div>

                <div className="mt-4">
                  <p className="text-xs text-muted-foreground">
                    Created {project.createdAt ? formatDate(project.createdAt) : '—'}
                  </p>
                </div>

                <div className="mt-6">
                  <Button asChild variant="outline" className="w-full">
                    <Link href={`/projects/${project.id}`}>View details</Link>
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}
