"use client"

import { useRouter } from 'next/navigation'
import { useCallback, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { MoreVertical, CheckCircle2, AlertCircle, Edit2, Trash2 } from 'lucide-react'
import { deleteCampaign } from '@/lib/api-client'
import { toast } from '@/hooks/use-toast'

interface CampaignCardProps {
  id?: number | string
  projectId?: number
  name: string
  type: 'Functional' | 'API' | 'Regression'
  status: 'Running' | 'Completed' | 'Failed' | 'Scheduled'
  progress: number
  tests: number
  passed: number
  failed: number
  lastRun: string
  onDelete?: () => void
}

const statusConfig = {
  Running: { color: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400' },
  Completed: { color: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400' },
  Failed: { color: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400' },
  Scheduled: { color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400' },
}

const typeColors = {
  Functional: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  API: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  Regression: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-400',
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
}

export function CampaignCard({
  id,
  projectId,
  name,
  type,
  status,
  progress,
  tests,
  passed,
  failed,
  lastRun,
  onDelete,
}: CampaignCardProps) {
  const router = useRouter()
  const [isDeleting, setIsDeleting] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const config = statusConfig[status]
  const typeColor = typeColors[type]
  const baseHref = id !== undefined && id !== null && String(id).trim()
    ? `/campaigns/${encodeURIComponent(String(id))}`
    : `/campaigns/${slugify(name)}`
  const href = projectId ? `${baseHref}?projectId=${encodeURIComponent(String(projectId))}` : baseHref
  const editHref = id !== undefined && id !== null && String(id).trim()
    ? `/campaigns/${encodeURIComponent(String(id))}/edit${projectId ? `?projectId=${encodeURIComponent(String(projectId))}` : ''}`
    : baseHref

  const navigate = useCallback(() => {
    router.push(href)
  }, [router, href])

  const handleEdit = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    router.push(editHref)
  }, [router, editHref])

  const handleDelete = useCallback(async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()

    if (!id || !projectId || isDeleting) return

    const parsedCampaignId = Number(id)
    const parsedProjectId = Number(projectId)
    if (!Number.isFinite(parsedCampaignId) || parsedCampaignId <= 0) {
      toast({
        title: 'Delete failed',
        description: 'Cannot delete this campaign: invalid campaign id.',
        variant: 'destructive',
      })
      setConfirmOpen(false)
      return
    }
    if (!Number.isFinite(parsedProjectId) || parsedProjectId <= 0) {
      toast({
        title: 'Delete failed',
        description: 'Cannot delete this campaign: invalid project id.',
        variant: 'destructive',
      })
      setConfirmOpen(false)
      return
    }

    setIsDeleting(true)
    try {
      await deleteCampaign(parsedProjectId, parsedCampaignId)
      onDelete?.()
      toast({
        title: 'Campaign deleted',
        description: `"${name}" was deleted.`,
      })
      setConfirmOpen(false)
    } catch (error) {
      console.error('Failed to delete campaign:', error)
      toast({
        title: 'Delete failed',
        description: error instanceof Error ? error.message : 'Failed to delete campaign. Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsDeleting(false)
    }
  }, [id, projectId, name, isDeleting, onDelete])

  const requestDelete = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (isDeleting) return
    setConfirmOpen(true)
  }, [isDeleting])

  return (
    <div
      className="bg-card border border-border rounded-lg p-5 hover:shadow-md transition-shadow cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      role="link"
      tabIndex={0}
      aria-label={`Open campaign details: ${name}`}
      onClick={() => navigate()}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          navigate()
        }
      }}
    >
      <div className="flex items-start justify-between mb-4">
        <div className="flex-1">
          <h3 className="font-semibold text-foreground">{name}</h3>
          <div className="flex items-center gap-2 mt-2">
            <Badge variant="outline" className={typeColor}>
              {type}
            </Badge>
            <Badge variant="outline" className={config.color}>
              {status === 'Running' && <span className="inline-block size-1.5 bg-current rounded-full mr-1 animate-pulse" />}
              {status}
            </Badge>
          </div>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="p-2 hover:bg-secondary rounded-lg transition-colors"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
              }}
              aria-label="Campaign actions"
            >
              <MoreVertical size={18} className="text-muted-foreground" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuItem onClick={handleEdit} className="cursor-pointer">
              <Edit2 size={16} className="mr-2" />
              <span>Edit Campaign</span>
            </DropdownMenuItem>
            <DropdownMenuItem 
              onClick={requestDelete}
              disabled={isDeleting}
              className="cursor-pointer text-red-600 dark:text-red-400 focus:text-red-600 focus:dark:text-red-400"
            >
              <Trash2 size={16} className="mr-2" />
              <span>{isDeleting ? 'Deleting...' : 'Delete Campaign'}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent
          onPointerDownOutside={(event) => {
            if (isDeleting) event.preventDefault()
          }}
          onEscapeKeyDown={(event) => {
            if (isDeleting) event.preventDefault()
          }}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>Delete campaign?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The campaign "{name}" and its linked test cases will be removed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => handleDelete(e as unknown as React.MouseEvent)}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={isDeleting}
            >
              {isDeleting ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Progress Bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs text-muted-foreground">Progress</span>
          <span className="text-xs font-semibold text-foreground">{progress}%</span>
        </div>
        <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-primary to-accent rounded-full transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p className="text-xs text-muted-foreground">Total</p>
          <p className="font-bold text-foreground">{tests}</p>
        </div>
        <div className="flex items-center gap-1">
          <CheckCircle2 size={16} className="text-green-600" />
          <div>
            <p className="text-xs text-muted-foreground">Passed</p>
            <p className="font-bold text-foreground">{passed}</p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <AlertCircle size={16} className="text-red-600" />
          <div>
            <p className="text-xs text-muted-foreground">Failed</p>
            <p className="font-bold text-foreground">{failed}</p>
          </div>
        </div>
      </div>

      {/* Last Run */}
      <div className="pt-4 border-t border-border">
        <p className="text-xs text-muted-foreground">Last run: {lastRun}</p>
      </div>
    </div>
  )
}
