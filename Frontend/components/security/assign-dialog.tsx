'use client'

import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { UserPlus, Loader2, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'

interface ProjectMember {
  userId: number
  nom: string | null
  prenom: string | null
  email: string | null
  imageUrl: string | null
}

interface AssignDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  projectId: string | number
  onAssign: (member: { userId: number; name: string; email: string }) => void
}

export function AssignDialog({ open, onOpenChange, projectId, onAssign }: AssignDialogProps) {
  const [members, setMembers] = useState<ProjectMember[]>([])
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<ProjectMember | null>(null)

  useEffect(() => {
    if (!open || !projectId) return
    setLoading(true)
    setSelected(null)
    setSearch('')
    fetch(`/api/projects/${projectId}/members`)
      .then(r => r.ok ? r.json() : [])
      .then(data => setMembers(Array.isArray(data) ? data : []))
      .catch(() => setMembers([]))
      .finally(() => setLoading(false))
  }, [open, projectId])

  const filtered = members.filter(m => {
    if (!search) return true
    const fullName = `${m.prenom ?? ''} ${m.nom ?? ''}`.toLowerCase()
    return fullName.includes(search.toLowerCase()) ||
      (m.email ?? '').toLowerCase().includes(search.toLowerCase())
  })

  const getMemberName = (m: ProjectMember) => {
    const parts = [m.prenom, m.nom].filter(Boolean)
    return parts.length > 0 ? parts.join(' ') : `User #${m.userId}`
  }

  const getInitials = (m: ProjectMember) => {
    const first = (m.prenom ?? '')[0] ?? ''
    const last = (m.nom ?? '')[0] ?? ''
    return (first + last).toUpperCase() || 'U'
  }

  const handleConfirm = () => {
    if (!selected) return
    onAssign({
      userId: selected.userId,
      name: getMemberName(selected),
      email: selected.email ?? '',
    })
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus className="h-5 w-5" />
            Assigner la vulnerabilite
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Rechercher un membre..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : filtered.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">
              {members.length === 0 ? 'Aucun membre dans ce projet' : 'Aucun resultat'}
            </div>
          ) : (
            <ScrollArea className="max-h-[280px]">
              <div className="space-y-1">
                {filtered.map(m => (
                  <button
                    key={m.userId}
                    onClick={() => setSelected(m)}
                    className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-left transition-colors ${
                      selected?.userId === m.userId
                        ? 'bg-primary/10 ring-1 ring-primary/30'
                        : 'hover:bg-muted/50'
                    }`}
                  >
                    {m.imageUrl ? (
                      <img
                        src={m.imageUrl}
                        alt=""
                        className="w-8 h-8 rounded-full object-cover"
                      />
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center">
                        <span className="text-xs font-bold text-primary-foreground">{getInitials(m)}</span>
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{getMemberName(m)}</p>
                      {m.email && (
                        <p className="text-xs text-muted-foreground truncate">{m.email}</p>
                      )}
                    </div>
                    {selected?.userId === m.userId && (
                      <div className="size-5 rounded-full bg-primary flex items-center justify-center">
                        <span className="text-[10px] text-primary-foreground">✓</span>
                      </div>
                    )}
                  </button>
                ))}
              </div>
            </ScrollArea>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Annuler</Button>
          <Button onClick={handleConfirm} disabled={!selected}>
            Assigner
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
