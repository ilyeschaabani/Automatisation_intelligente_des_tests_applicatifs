'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { Eye, Folder, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'

import { AuthGuard } from '@/components/auth-guard'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { ConfirmDialog } from '@/components/crud/ConfirmDialog'
import { FormDialog } from '@/components/crud/FormDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useUserRoles } from '@/hooks/use-roles'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { projectService } from '@/services/projects'
import type {
  CreateProjectRequest,
  Project,
  ProjectStatus,
  UpdateProjectRequest,
} from '@/types/ms-gestion'

type ProjectFormState = {
  name: string
  description: string
}

const emptyForm: ProjectFormState = {
  name: '',
  description: '',
}

const statusVariant: Record<
  ProjectStatus,
  'default' | 'secondary' | 'destructive' | 'outline'
> = {
  ACTIVE: 'default',
  PAUSED: 'secondary',
}

const statusLabels: Record<string, string> = {
  ACTIVE: 'Actif',
  PAUSED: 'En pause',
}

const formatDate = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function ProjectsPage() {
  const { isAdmin } = useUserRoles()
  const currentUserId = useMemo(() => {
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('access_token') : null
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1]))
        return typeof payload.userId === 'number' ? payload.userId : null
      }
    } catch { /* ignore */ }
    return null
  }, [])

  const [projects, setProjects] = useState<Project[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  const [formState, setFormState] = useState<ProjectFormState>(emptyForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingProject, setEditingProject] = useState<Project | null>(null)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingProject, setDeletingProject] = useState<Project | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)


  const loadProjects = async () => {
    setStatus('loading')
    setError(null)
    try {
      const data = await projectService.getAll()
      setProjects(Array.isArray(data) ? data : [])
      setStatus('ready')
    } catch (err) {
      setProjects([])
      setStatus('error')
      setError(err instanceof Error ? err.message : 'Échec du chargement des projets')
    }
  }

  useEffect(() => {
    void loadProjects()
  }, [])

  const filteredProjects = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return projects
    return projects.filter((project) => {
      const name = String(project.name ?? '').toLowerCase()
      const repo = String(project.gitRepoUrl ?? '').toLowerCase()
      const isAi = project.aiProject || project.aiBuiltin || !project.gitRepoUrl || project.gitRepoUrl === 'ai-builtin'
      return name.includes(query) || repo.includes(query) || (isAi && 'ia'.includes(query))
    })
  }, [projects, search])

  const resetForm = () => {
    setFormState(emptyForm)
    setFormError(null)
  }

  const openCreate = () => {
    resetForm()
    setCreateOpen(true)
  }

  const openEdit = (project: Project) => {
    setEditingProject(project)
    setFormError(null)
    setFormState({
      name: project.name ?? '',
      description: project.description ?? '',
    })
    setEditOpen(true)
  }

  const openDelete = (project: Project) => {
    setDeletingProject(project)
    setDeleteOpen(true)
  }



  const onCreateSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)

   

    try {
      const payload: CreateProjectRequest = {
        name: formState.name.trim(),
        description: formState.description.trim() || undefined,

      }
      await projectService.create(payload)
      setCreateOpen(false)
      resetForm()
      await loadProjects()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Échec de la création du projet')
    } finally {
      setIsSubmitting(false)
    }
  }

  const onEditSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editingProject) return
    setIsSubmitting(true)
    setFormError(null)

 

    try {
      const payload: UpdateProjectRequest = {
        name: formState.name.trim(),
        description: formState.description.trim() || undefined
      }
      await projectService.update(editingProject.id, payload)
      setEditOpen(false)
      setEditingProject(null)
      await loadProjects()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Échec de la mise à jour du projet')
    } finally {
      setIsSubmitting(false)
    }
  }

  const onDeleteConfirm = async () => {
    if (!deletingProject) return
    setIsDeleting(true)
    try {
      await projectService.delete(deletingProject.id)
      setDeleteOpen(false)
      setDeletingProject(null)
      await loadProjects()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Échec de la suppression du projet')
    } finally {
      setIsDeleting(false)
    }
  }


  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <AuthGuard>
          <div className="p-6 max-w-7xl">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-foreground">Projets</h1>
                <p className="text-muted-foreground mt-1">
                  Gérez les projets, environnements et suites de test.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={loadProjects} className="gap-2">
                  <RefreshCw size={16} />
                  Actualiser
                </Button>
                <Button className="gap-2" onClick={openCreate}>
                  <Plus size={18} />
                  Nouveau projet
                </Button>
              </div>
            </div>

            <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Rechercher par nom ou URL de dépôt"
                className="max-w-md"
              />
              {error ? (
                <p className="text-sm text-destructive">{error}</p>
              ) : null}
            </div>

            <Card className="mt-6">
              {status === 'loading' ? (
                <div className="p-6 text-sm text-muted-foreground">Chargement des projets…</div>
              ) : null}

              {status !== 'loading' && filteredProjects.length === 0 ? (
                <div className="p-6 text-sm text-muted-foreground flex items-center gap-2">
                  <Folder size={16} />
                  Aucun projet trouvé.
                </div>
              ) : null}

              {status !== 'loading' && filteredProjects.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nom</TableHead>
                      <TableHead>Dépôt</TableHead>
                      <TableHead>Branche par défaut</TableHead>
                      <TableHead>Statut</TableHead>
                      <TableHead>Créé le</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredProjects.map((project) => (
                      <TableRow key={project.id}>
                        <TableCell className="font-medium">
                          <div className="flex items-center gap-2">
                            <Link
                              href={`/projects/${project.id}`}
                              className="text-primary hover:underline"
                            >
                              {project.name}
                            </Link>
                          </div>
                        </TableCell>
                        <TableCell>{project.gitRepoUrl || '—'}</TableCell>
                        <TableCell>{project.gitDefaultBranch || 'main'}</TableCell>
                        <TableCell>
                          <Badge variant={statusVariant[project.status]}>
                            {statusLabels[project.status] ?? project.status}
                          </Badge>
                        </TableCell>
                        <TableCell>{formatDate(project.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <Button variant="ghost" size="sm" asChild title="Voir le projet">
                              <Link href={`/projects/${project.id}`}>
                                <Eye size={16} />
                              </Link>
                            </Button>
                            {(isAdmin || currentUserId === project.createdBy) && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openEdit(project)}
                                title="Modifier"
                              >
                                <Pencil size={16} />
                              </Button>
                            )}
                            {(isAdmin || currentUserId === project.createdBy) && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openDelete(project)}
                                title="Supprimer"
                                className="text-destructive hover:text-destructive hover:bg-destructive/10"
                              >
                                <Trash2 size={16} />
                              </Button>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </Card>
          </div>
        </AuthGuard>
      </main>

      <FormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        title="Créer un projet"
        description="Définissez les détails du projet."
        submitLabel="Créer le projet"
        isSubmitting={isSubmitting}
        onSubmit={onCreateSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="project-name">Nom</Label>
          <Input
            id="project-name"
            value={formState.name}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, name: event.target.value }))
            }
            placeholder="Plateforme bancaire digitale"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="project-description">Description</Label>
          <Textarea
            id="project-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
            placeholder="Description (facultatif)"
          />
        </div>

        <div className="space-y-3">
                   </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Modifier le projet"
        description="Mettez à jour les détails du projet."
        submitLabel="Enregistrer"
        isSubmitting={isSubmitting}
        onSubmit={onEditSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="edit-project-name">Nom</Label>
          <Input
            id="edit-project-name"
            value={formState.name}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, name: event.target.value }))
            }
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-project-description">Description</Label>
          <Textarea
            id="edit-project-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </div>

        <div className="space-y-3">
            
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Supprimer le projet"
        description={
          deletingProject
            ? `Supprimer définitivement « ${deletingProject.name} » ? Toutes les suites, cas de test et campagnes seront perdus. Cette action est irréversible.`
            : 'Supprimer ce projet ?'
        }
        confirmLabel="Supprimer définitivement"
        isConfirming={isDeleting}
        onConfirm={onDeleteConfirm}
      />

    </div>
  )
}