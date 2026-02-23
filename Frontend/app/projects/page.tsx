'use client'

import Link from 'next/link'
import { useState } from 'react'

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

const projects = [
  {
    id: 'PRJ-001',
    name: 'Banking Web App',
    description: 'Release testing for core banking web application.',
    repositoryUrl: 'https://gitlab.example.com/banking/web-app',
    type: 'Web',
    createdBy: 'admin@company.com',
    createdAt: '2026-02-01T10:15:00Z',
    ownerUserId: 'USR-001',
    defaultBranch: 'main',
    repoProvider: 'GitLab',
    archived: false,
  },
  {
    id: 'PRJ-002',
    name: 'Mobile Customer Portal',
    description: 'Customer portal mobile web regression suites.',
    repositoryUrl: 'https://github.com/company/mobile-portal',
    type: 'Mobile',
    createdBy: 'qa.lead@company.com',
    createdAt: '2026-01-12T09:00:00Z',
    ownerUserId: 'USR-002',
    defaultBranch: 'develop',
    repoProvider: 'GitHub',
    archived: false,
  },
  {
    id: 'PRJ-003',
    name: 'API Gateway',
    description: 'API gateway integration and security tests.',
    repositoryUrl: 'https://gitlab.example.com/banking/api-gateway',
    type: 'API',
    createdBy: 'devops@company.com',
    createdAt: '2025-11-20T14:30:00Z',
    ownerUserId: 'USR-003',
    defaultBranch: 'main',
    repoProvider: 'GitLab',
    archived: true,
  },
]

const statusStyle: Record<'Active' | 'Archived', string> = {
  Active: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  Archived: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function ProjectsPage() {
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [type, setType] = useState<'Web' | 'Mobile' | 'API' | 'Desktop' | 'Other'>('Web')
  const [defaultBranch, setDefaultBranch] = useState('main')
  const [repoProvider, setRepoProvider] = useState<'GitHub' | 'GitLab'>('GitLab')
  const [archived, setArchived] = useState(false)

  const resetForm = () => {
    setName('')
    setDescription('')
    setRepositoryUrl('')
    setType('Web')
    setDefaultBranch('main')
    setRepoProvider('GitLab')
    setArchived(false)
  }

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)

    try {
      // UI-only for now (backend wiring can be added later).
      setIsCreateOpen(false)
      resetForm()
    } finally {
      setIsSubmitting(false)
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
                    <Label htmlFor="projectDescription">Description</Label>
                    <Input
                      id="projectDescription"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="Optional"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="projectRepoUrl">Repository URL</Label>
                    <Input
                      id="projectRepoUrl"
                      value={repositoryUrl}
                      onChange={(e) => setRepositoryUrl(e.target.value)}
                      placeholder="https://github.com/org/repo"
                    />
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>Type</Label>
                      <Select value={type} onValueChange={(v) => setType(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select type" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="Web">Web</SelectItem>
                          <SelectItem value="Mobile">Mobile</SelectItem>
                          <SelectItem value="API">API</SelectItem>
                          <SelectItem value="Desktop">Desktop</SelectItem>
                          <SelectItem value="Other">Other</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="space-y-2">
                      <Label>Repo provider</Label>
                      <Select value={repoProvider} onValueChange={(v) => setRepoProvider(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select provider" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="GitHub">GitHub</SelectItem>
                          <SelectItem value="GitLab">GitLab</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="projectDefaultBranch">Default branch</Label>
                    <Input
                      id="projectDefaultBranch"
                      value={defaultBranch}
                      onChange={(e) => setDefaultBranch(e.target.value)}
                      placeholder="main"
                    />
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
              <Input placeholder="Search projects..." className="pl-10" />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {projects.map((project) => (
              <Card key={project.id} className="p-6">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-lg font-semibold text-foreground truncate">
                      {project.name}
                    </p>
                    <p className="text-sm text-muted-foreground mt-1 truncate">
                      {project.repositoryUrl}
                    </p>
                  </div>
                  <Badge
                    variant="outline"
                    className={statusStyle[project.archived ? 'Archived' : 'Active']}
                  >
                    {project.archived ? 'Archived' : 'Active'}
                  </Badge>
                </div>

                <div className="grid grid-cols-3 gap-4 mt-6">
                  <div>
                    <p className="text-xs text-muted-foreground">Type</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {project.type}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Provider</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {project.repoProvider}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Default branch</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {project.defaultBranch}
                    </p>
                  </div>
                </div>

                <div className="mt-4">
                  <p className="text-xs text-muted-foreground">
                    Created by <span className="font-medium text-foreground">{project.createdBy}</span> • Owner <span className="font-medium text-foreground">{project.ownerUserId}</span> • {formatDate(project.createdAt)}
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
