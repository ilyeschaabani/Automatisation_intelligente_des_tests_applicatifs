'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { Archive, Folder, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'

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
  ARCHIVED: 'outline',
}

const formatDate = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function ProjectsPage() {
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

  const [archiveOpen, setArchiveOpen] = useState(false)
  const [archivingProject, setArchivingProject] = useState<Project | null>(null)
  const [isArchiving, setIsArchiving] = useState(false)

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
      setError(err instanceof Error ? err.message : 'Failed to load projects')
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

  const openArchive = (project: Project) => {
    setArchivingProject(project)
    setArchiveOpen(true)
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
      setFormError(err instanceof Error ? err.message : 'Failed to create project')
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
      setFormError(err instanceof Error ? err.message : 'Failed to update project')
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
      setError(err instanceof Error ? err.message : 'Failed to delete project')
    } finally {
      setIsDeleting(false)
    }
  }

  const onArchiveConfirm = async () => {
    if (!archivingProject) return
    setIsArchiving(true)
    try {
      await projectService.archive(archivingProject.id)
      setArchiveOpen(false)
      setArchivingProject(null)
      await loadProjects()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to archive project')
    } finally {
      setIsArchiving(false)
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
                <h1 className="text-3xl font-bold text-foreground">Projects</h1>
                <p className="text-muted-foreground mt-1">
                  Manage projects, environments, and test suites for ms_gestion.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={loadProjects} className="gap-2">
                  <RefreshCw size={16} />
                  Refresh
                </Button>
                <Button className="gap-2" onClick={openCreate}>
                  <Plus size={18} />
                  New project
                </Button>
              </div>
            </div>

            <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search by name or repo URL"
                className="max-w-md"
              />
              {error ? (
                <p className="text-sm text-destructive">{error}</p>
              ) : null}
            </div>

            <Card className="mt-6">
              {status === 'loading' ? (
                <div className="p-6 text-sm text-muted-foreground">Loading projects...</div>
              ) : null}

              {status !== 'loading' && filteredProjects.length === 0 ? (
                <div className="p-6 text-sm text-muted-foreground flex items-center gap-2">
                  <Folder size={16} />
                  No projects found.
                </div>
              ) : null}

              {status !== 'loading' && filteredProjects.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Repository</TableHead>
                      <TableHead>Default branch</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
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
                            {(project.aiProject || project.aiBuiltin || !project.gitRepoUrl || project.gitRepoUrl === 'ai-builtin') ? (
                              <Badge variant="secondary" className="bg-blue-100 text-blue-800">
                                IA
                              </Badge>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell>{project.gitRepoUrl || '—'}</TableCell>
                        <TableCell>{project.gitDefaultBranch || 'main'}</TableCell>
                        <TableCell>
                          <Badge variant={statusVariant[project.status]}>
                            {project.status}
                          </Badge>
                        </TableCell>
                        <TableCell>{formatDate(project.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => openEdit(project)}
                            >
                              <Pencil size={14} />
                              Edit
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => openArchive(project)}
                              disabled={project.status === 'ARCHIVED'}
                              title={project.status === 'ARCHIVED' ? 'Already archived' : 'Archive project'}
                            >
                              <Archive size={14} />
                              Archive
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => openDelete(project)}
                            >
                              <Trash2 size={14} />
                              Delete
                            </Button>
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
        title="Create project"
        description="Define the project details for ms_gestion."
        submitLabel="Create project"
        isSubmitting={isSubmitting}
        onSubmit={onCreateSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="project-name">Name</Label>
          <Input
            id="project-name"
            value={formState.name}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, name: event.target.value }))
            }
            placeholder="Digital Banking Platform"
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
            placeholder="Optional description"
          />
        </div>

        <div className="space-y-3">
                   </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Update project"
        description="Keep project details in sync with ms_gestion."
        submitLabel="Save changes"
        isSubmitting={isSubmitting}
        onSubmit={onEditSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="edit-project-name">Name</Label>
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
        title="Delete project"
        description={
          deletingProject
            ? `Permanently delete "${deletingProject.name}"? All test suites, test cases and campaigns will be lost. This cannot be undone.`
            : 'Delete this project?'
        }
        confirmLabel="Delete permanently"
        isConfirming={isDeleting}
        onConfirm={onDeleteConfirm}
      />

      <ConfirmDialog
        open={archiveOpen}
        onOpenChange={setArchiveOpen}
        title="Archive project"
        description={
          archivingProject
            ? `Archive "${archivingProject.name}"? The project will be read-only. You can restore it later.`
            : 'Archive this project?'
        }
        confirmLabel="Archive"
        isConfirming={isArchiving}
        onConfirm={onArchiveConfirm}
      />
    </div>
  )
}