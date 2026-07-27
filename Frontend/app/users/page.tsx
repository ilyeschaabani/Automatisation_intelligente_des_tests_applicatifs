'use client'

import { Fragment, useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import {
  Dialog, DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
} from '@/components/ui/dialog'
import { Plus, Edit2, Trash2, Shield, Eye, EyeOff, Users, UserCheck, ShieldCheck, KeyRound, Check, X } from 'lucide-react'
import {
  type UserAdmin, type GlobalRole,
  ROLE_LABELS, ROLE_COLORS, ASSIGNABLE_ROLES, ROLE_DESCRIPTIONS,
} from '@/types/user-admin'

function getInitials(user: UserAdmin): string {
  if (user.nom && user.prenom) {
    return `${user.prenom[0]}${user.nom[0]}`.toUpperCase()
  }
  return user.email.substring(0, 2).toUpperCase()
}

function getDisplayName(user: UserAdmin): string {
  if (user.prenom && user.nom) return `${user.prenom} ${user.nom}`
  if (user.prenom) return user.prenom
  if (user.nom) return user.nom
  return user.email
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleDateString('fr-FR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatDateTime(iso: string | null): string {
  if (!iso) return 'Jamais'
  const d = new Date(iso)
  return d.toLocaleDateString('fr-FR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

// ─── Permission matrix ────────────────────────────────────────
type PermissionEntry = { label: string; roles: GlobalRole[] }
type PermissionGroup = { group: string; permissions: PermissionEntry[] }

const PERMISSION_GROUPS: PermissionGroup[] = [
  {
    group: 'Gestion des utilisateurs',
    permissions: [
      { label: 'Créer / supprimer des utilisateurs', roles: ['ADMIN'] },
      { label: 'Affecter / modifier les rôles', roles: ['ADMIN'] },
      { label: 'Activer / désactiver un compte', roles: ['ADMIN'] },
    ],
  },
  {
    group: 'Projets',
    permissions: [
      { label: 'Créer un projet', roles: ['ADMIN', 'TEST_MANAGER'] },
      { label: 'Modifier un projet', roles: ['ADMIN', 'TEST_MANAGER'] },
      { label: 'Supprimer un projet', roles: ['ADMIN', 'TEST_MANAGER'] },
      { label: 'Consulter les projets', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
      { label: 'Gérer les membres du projet', roles: ['ADMIN', 'TEST_MANAGER'] },
    ],
  },
  {
    group: 'Environnements',
    permissions: [
      { label: 'Créer / modifier un environnement', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Supprimer un environnement', roles: ['ADMIN', 'TEST_MANAGER'] },
    ],
  },
  {
    group: 'Suites de tests',
    permissions: [
      { label: 'Créer / modifier une suite', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Supprimer une suite', roles: ['ADMIN', 'TEST_MANAGER'] },
    ],
  },
  {
    group: 'Cas de test',
    permissions: [
      { label: 'Créer / modifier un cas de test', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Supprimer un cas de test', roles: ['ADMIN', 'TEST_MANAGER'] },
      { label: 'Générer des tests via IA (LLM)', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
    ],
  },
  {
    group: 'Campagnes',
    permissions: [
      { label: 'Créer une campagne', roles: ['ADMIN', 'TEST_MANAGER'] },
      { label: 'Modifier / ajouter des tests', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Supprimer une campagne', roles: ['ADMIN', 'TEST_MANAGER'] },
    ],
  },
  {
    group: 'Exécution',
    permissions: [
      { label: 'Lancer une campagne', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Arrêter une exécution en cours', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Consulter les résultats d\'exécution', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
      { label: 'Consulter l\'analyse IA des échecs', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
    ],
  },
  {
    group: 'Rapports',
    permissions: [
      { label: 'Générer un rapport PDF', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Télécharger un rapport', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
      { label: 'Consulter les KPIs / tableaux de bord', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
    ],
  },
  {
    group: 'Évaluation UX',
    permissions: [
      { label: 'Créer une évaluation UX', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Lancer / pause / arrêter', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Consulter les résultats', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
    ],
  },
  {
    group: 'Sécurité & conformité',
    permissions: [
      { label: 'Lancer un scan (SAST / DAST / SCA)', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Consulter les vulnérabilités', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
      { label: 'Modifier le statut d\'une vulnérabilité', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Télécharger un rapport de sécurité', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR'] },
    ],
  },
  {
    group: 'GitHub & intégration',
    permissions: [
      { label: 'Connecter / déconnecter GitHub', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
      { label: 'Parcourir les repos / fichiers', roles: ['ADMIN', 'TEST_MANAGER', 'TESTEUR'] },
    ],
  },
  {
    group: 'Paramètres système',
    permissions: [
      { label: 'Accéder aux paramètres globaux', roles: ['ADMIN'] },
    ],
  },
]

const MATRIX_ROLES: GlobalRole[] = ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR']

export default function UsersPage() {
  const router = useRouter()
  const [users, setUsers] = useState<UserAdmin[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)

  // Modal states
  const [showAddModal, setShowAddModal] = useState(false)
  const [editUser, setEditUser] = useState<UserAdmin | null>(null)
  const [rolesUser, setRolesUser] = useState<UserAdmin | null>(null)
  const [deleteUser, setDeleteUser] = useState<UserAdmin | null>(null)

  // Current user email and roles from JWT
  const [currentUserEmail, setCurrentUserEmail] = useState<string | null>(null)

  // Password reset requests
  type ResetRequest = { id: number; userId: number; email: string; displayName: string; status: string; createdAt: string }
  const [resetRequests, setResetRequests] = useState<ResetRequest[]>([])
  const [resetLoading, setResetLoading] = useState(true)
  const [processingResetId, setProcessingResetId] = useState<number | null>(null)

  const showToast = useCallback((message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type })
    setTimeout(() => setToast(null), 3000)
  }, [])

  const fetchUsers = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/users')
      if (!res.ok) throw new Error('Erreur de chargement')
      const data = await res.json()
      setUsers(data)
      setError(null)
    } catch {
      setError('Impossible de charger les utilisateurs')
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchResetRequests = useCallback(async () => {
    setResetLoading(true)
    try {
      const res = await fetch('/api/admin/users/password-reset-requests')
      if (res.ok) {
        const data = await res.json()
        setResetRequests(Array.isArray(data) ? data : [])
      }
    } catch { /* ignore */ }
    finally { setResetLoading(false) }
  }, [])

  const handleResetAction = useCallback(async (id: number, action: 'approve' | 'reject') => {
    setProcessingResetId(id)
    try {
      const res = await fetch(`/api/admin/users/password-reset-requests/${id}/${action}`, { method: 'POST' })
      const data = await res.json().catch(() => ({})) as any
      if (res.ok) {
        showToast(data.message || (action === 'approve' ? 'Mot de passe réinitialisé' : 'Demande rejetée'))
        fetchResetRequests()
      } else {
        showToast(data.message || 'Erreur', 'error')
      }
    } catch {
      showToast('Erreur réseau', 'error')
    } finally {
      setProcessingResetId(null)
    }
  }, [showToast, fetchResetRequests])

  useEffect(() => {
    fetchUsers()
    fetchResetRequests()
    // Decode current user email and roles from JWT in localStorage
    try {
      const token = localStorage.getItem('access_token')
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1]))
        setCurrentUserEmail(payload.sub || null)
        const roles: string[] = Array.isArray(payload.roles) ? payload.roles : []
        if (!roles.includes('ADMIN')) {
          router.replace('/dashboard')
          return
        }
      }
    } catch { /* ignore */ }
  }, [fetchUsers, fetchResetRequests])

  // Stats
  const totalUsers = users.length
  const activeUsers = users.filter(u => u.enabled).length
  const adminUsers = users.filter(u => u.roles?.includes('ADMIN')).length

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <div className="p-6 max-w-7xl">
          {/* Toast */}
          {toast && (
            <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg text-white transition-all ${
              toast.type === 'success' ? 'bg-green-600' : 'bg-red-600'
            }`}>
              {toast.message}
            </div>
          )}

          {/* Page Header */}
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Utilisateurs & Rôles</h1>
              <p className="text-muted-foreground mt-1">Gérer les accès et permissions des utilisateurs</p>
            </div>
            <Button onClick={() => setShowAddModal(true)} className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
              <Plus size={20} />
              Ajouter un utilisateur
            </Button>
          </div>

          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <Card className="p-6 flex items-center gap-4">
              <div className="p-3 rounded-lg bg-blue-100 dark:bg-blue-950">
                <Users className="text-blue-600 dark:text-blue-400" size={24} />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Total utilisateurs</p>
                <p className="text-3xl font-bold text-foreground">{totalUsers}</p>
              </div>
            </Card>
            <Card className="p-6 flex items-center gap-4">
              <div className="p-3 rounded-lg bg-green-100 dark:bg-green-950">
                <UserCheck className="text-green-600 dark:text-green-400" size={24} />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Utilisateurs actifs</p>
                <p className="text-3xl font-bold text-foreground">{activeUsers}</p>
              </div>
            </Card>
            <Card className="p-6 flex items-center gap-4">
              <div className="p-3 rounded-lg bg-red-100 dark:bg-red-950">
                <ShieldCheck className="text-red-600 dark:text-red-400" size={24} />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Administrateurs</p>
                <p className="text-3xl font-bold text-foreground">{adminUsers}</p>
              </div>
            </Card>
          </div>

          {/* Password Reset Requests */}
          {(() => {
            const pendingRequests = resetRequests.filter(r => r.status === 'PENDING')
            if (resetLoading || pendingRequests.length === 0) return null
            return (
              <Card className="mb-8 overflow-hidden border-orange-200 dark:border-orange-800">
                <div className="px-6 py-4 bg-orange-50 dark:bg-orange-950/30 border-b border-orange-200 dark:border-orange-800">
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-orange-100 dark:bg-orange-900/50 text-orange-600">
                      <KeyRound size={18} />
                    </div>
                    <div>
                      <h2 className="text-sm font-bold text-foreground">
                        Demandes de réinitialisation
                        <Badge variant="secondary" className="ml-2 bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300">
                          {pendingRequests.length}
                        </Badge>
                      </h2>
                      <p className="text-xs text-muted-foreground">
                        Des utilisateurs ont demandé la réinitialisation de leur mot de passe.
                      </p>
                    </div>
                  </div>
                </div>
                <div className="divide-y divide-border">
                  {pendingRequests.map((req) => (
                    <div key={req.id} className="px-6 py-3 flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-foreground truncate">{req.displayName}</p>
                        <p className="text-xs text-muted-foreground">{req.email} · {new Date(req.createdAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-1.5 text-green-700 border-green-300 hover:bg-green-50 dark:text-green-400 dark:border-green-700 dark:hover:bg-green-950/30"
                          disabled={processingResetId === req.id}
                          onClick={() => handleResetAction(req.id, 'approve')}
                        >
                          <Check size={14} />
                          Réinitialiser
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-1.5 text-red-700 border-red-300 hover:bg-red-50 dark:text-red-400 dark:border-red-700 dark:hover:bg-red-950/30"
                          disabled={processingResetId === req.id}
                          onClick={() => handleResetAction(req.id, 'reject')}
                        >
                          <X size={14} />
                          Rejeter
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </Card>
            )
          })()}

          {/* Users Table */}
          {loading ? (
            <Card className="p-12 text-center text-muted-foreground">Chargement...</Card>
          ) : error ? (
            <Card className="p-12 text-center text-red-500">{error}</Card>
          ) : (
            <Card className="overflow-hidden">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="border-b border-border">
                      <TableHead className="text-left">Nom</TableHead>
                      <TableHead className="text-left">Email</TableHead>
                      <TableHead className="text-center">Rôles</TableHead>
                      <TableHead className="text-center">Statut</TableHead>
                      <TableHead className="text-center">Date d&apos;inscription</TableHead>
                      <TableHead className="text-center">Dernière connexion</TableHead>
                      <TableHead className="text-center">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {users.map((user) => (
                      <TableRow key={user.id} className="border-b border-border hover:bg-secondary/50">
                        <TableCell className="font-medium text-foreground">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 bg-gradient-to-br from-primary to-accent rounded-full flex items-center justify-center flex-shrink-0">
                              <span className="text-xs font-bold text-primary-foreground">
                                {getInitials(user)}
                              </span>
                            </div>
                            {getDisplayName(user)}
                          </div>
                        </TableCell>
                        <TableCell className="text-muted-foreground">{user.email}</TableCell>
                        <TableCell className="text-center">
                          <div className="flex flex-wrap gap-1 justify-center">
                            {user.roles && user.roles.length > 0 ? (
                              user.roles.map(role => (
                                <Badge key={role} variant="outline" className={ROLE_COLORS[role] || ''}>
                                  {ROLE_LABELS[role] || role}
                                </Badge>
                              ))
                            ) : (
                              <span className="text-xs text-muted-foreground italic">Aucun rôle</span>
                            )}
                          </div>
                        </TableCell>
                        <TableCell className="text-center">
                          <Badge variant="outline" className={
                            user.enabled
                              ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
                              : 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400'
                          }>
                            {user.enabled ? 'Actif' : 'Inactif'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground">
                          {formatDate(user.createdAt)}
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground text-sm">
                          {formatDateTime(user.lastLoginAt)}
                        </TableCell>
                        <TableCell className="text-center">
                          <div className="flex items-center justify-center gap-1">
                            <Button variant="ghost" size="sm" title="Modifier le profil"
                              onClick={() => setEditUser(user)}>
                              <Edit2 size={16} />
                            </Button>
                            {!user.superAdmin && (
                              <Button variant="ghost" size="sm" title="Gérer les rôles"
                                onClick={() => setRolesUser(user)}>
                                <Shield size={16} className="text-blue-500" />
                              </Button>
                            )}
                            {user.superAdmin ? (
                              <span className="text-xs text-muted-foreground px-2" title="Compte protégé">🔒</span>
                            ) : (
                              <>
                                <Button variant="ghost" size="sm"
                                  title={user.enabled ? 'Désactiver' : 'Activer'}
                                  onClick={async () => {
                                    try {
                                      const res = await fetch(`/api/admin/users/${user.id}/status`, {
                                        method: 'PATCH',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({ enabled: !user.enabled }),
                                      })
                                      if (!res.ok) {
                                        const data = await res.json().catch(() => ({}))
                                        throw new Error(data.message || 'Erreur')
                                      }
                                      const updated = await res.json()
                                      setUsers(prev => prev.map(u => u.id === user.id ? updated : u))
                                      showToast(updated.enabled ? 'Utilisateur activé' : 'Utilisateur désactivé')
                                    } catch (err: unknown) {
                                      showToast(err instanceof Error ? err.message : 'Erreur', 'error')
                                    }
                                  }}>
                                  {user.enabled
                                    ? <EyeOff size={16} className="text-orange-500" />
                                    : <Eye size={16} className="text-green-500" />}
                                </Button>
                                <Button variant="ghost" size="sm"
                                  className="text-destructive hover:bg-destructive/10"
                                  title="Supprimer"
                                  onClick={() => setDeleteUser(user)}>
                                  <Trash2 size={16} />
                                </Button>
                              </>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </Card>
          )}

          {/* Role Permissions Matrix */}
          <Card className="p-6 mt-8">
            <div className="flex items-center gap-3 mb-6">
              <Shield className="text-primary" />
              <h2 className="text-lg font-bold text-foreground">Matrice des permissions</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left py-3 px-4 font-semibold text-foreground min-w-[240px]">Permission</th>
                    {MATRIX_ROLES.map(role => (
                      <th key={role} className="text-center py-3 px-4 font-semibold text-foreground">
                        {ROLE_LABELS[role]}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {PERMISSION_GROUPS.map(({ group, permissions }) => (
                    <Fragment key={group}>
                      <tr className="bg-secondary/50">
                        <td colSpan={MATRIX_ROLES.length + 1} className="py-2 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                          {group}
                        </td>
                      </tr>
                      {permissions.map(({ label, roles }) => (
                        <tr key={label} className="border-b border-border">
                          <td className="py-2.5 px-4 text-foreground">{label}</td>
                          {MATRIX_ROLES.map(role => (
                            <td key={role} className="text-center py-2.5 px-4">
                              {roles.includes(role) ? (
                                <span className="text-green-600 dark:text-green-400 font-bold">✓</span>
                              ) : (
                                <span className="text-muted-foreground/40">✗</span>
                              )}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </main>

      {/* ─── Add User Modal ─── */}
      <AddUserModal
        open={showAddModal}
        onClose={() => setShowAddModal(false)}
        onSuccess={(newUser) => {
          setUsers(prev => [...prev, newUser])
          setShowAddModal(false)
          showToast('Utilisateur créé avec succès')
        }}
        onError={(msg) => showToast(msg, 'error')}
      />

      {/* ─── Edit Profile Modal ─── */}
      <EditProfileModal
        user={editUser}
        onClose={() => setEditUser(null)}
        onSuccess={(updated) => {
          setUsers(prev => prev.map(u => u.id === updated.id ? updated : u))
          setEditUser(null)
          showToast('Profil mis à jour')
        }}
        onError={(msg) => showToast(msg, 'error')}
      />

      {/* ─── Manage Roles Modal ─── */}
      <ManageRolesModal
        user={rolesUser}
        currentUserEmail={currentUserEmail}
        onClose={() => setRolesUser(null)}
        onSuccess={(updated) => {
          setUsers(prev => prev.map(u => u.id === updated.id ? updated : u))
          setRolesUser(null)
          showToast('Rôles mis à jour')
        }}
        onError={(msg) => showToast(msg, 'error')}
      />

      {/* ─── Delete Confirmation ─── */}
      <DeleteConfirmModal
        user={deleteUser}
        onClose={() => setDeleteUser(null)}
        onSuccess={(id) => {
          setUsers(prev => prev.filter(u => u.id !== id))
          setDeleteUser(null)
          showToast('Utilisateur supprimé')
        }}
        onError={(msg) => showToast(msg, 'error')}
      />
    </div>
  )
}

// ─── Add User Modal ────────────────────────────────────────────
function AddUserModal({ open, onClose, onSuccess, onError }: {
  open: boolean
  onClose: () => void
  onSuccess: (user: UserAdmin) => void
  onError: (msg: string) => void
}) {
  const [form, setForm] = useState({ nom: '', prenom: '', email: '', roles: [] as GlobalRole[] })
  const [submitting, setSubmitting] = useState(false)

  const reset = () => setForm({ nom: '', prenom: '', email: '', roles: [] })

  const handleSubmit = async () => {
    if (!form.email.trim()) return onError('Email requis')
    setSubmitting(true)
    try {
      const res = await fetch('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, roles: form.roles }),
      })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.message || data.error || `Erreur ${res.status}`)
      }
      const created = await res.json()
      reset()
      onSuccess(created)
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Erreur de création')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleRole = (role: GlobalRole) => {
    setForm(prev => ({
      ...prev,
      roles: prev.roles.includes(role)
        ? prev.roles.filter(r => r !== role)
        : [...prev.roles, role],
    }))
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) { reset(); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Ajouter un utilisateur</DialogTitle>
          <DialogDescription>Créer un nouveau compte avec les rôles sélectionnés</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Prénom</Label>
              <Input value={form.prenom} onChange={e => setForm(f => ({ ...f, prenom: e.target.value }))} />
            </div>
            <div>
              <Label>Nom</Label>
              <Input value={form.nom} onChange={e => setForm(f => ({ ...f, nom: e.target.value }))} />
            </div>
          </div>
          <div>
            <Label>Email *</Label>
            <Input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
          </div>
          <div className="bg-blue-50 dark:bg-blue-950/30 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
            <p className="text-sm text-blue-700 dark:text-blue-300">
              Un mot de passe sera généré automatiquement et envoyé par e-mail à l&apos;utilisateur.
            </p>
          </div>
          <div>
            <Label>Rôles</Label>
            <div className="space-y-2 mt-2">
              {ASSIGNABLE_ROLES.map(role => (
                <label key={role} className="flex items-center gap-3 cursor-pointer">
                  <Checkbox
                    checked={form.roles.includes(role)}
                    onCheckedChange={() => toggleRole(role)}
                  />
                  <div>
                    <span className="font-medium text-sm">{ROLE_LABELS[role]}</span>
                    <span className="text-xs text-muted-foreground ml-2">— {ROLE_DESCRIPTIONS[role]}</span>
                  </div>
                </label>
              ))}
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => { reset(); onClose() }}>Annuler</Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Création...' : 'Créer'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ─── Edit Profile Modal ────────────────────────────────────────
function EditProfileModal({ user, onClose, onSuccess, onError }: {
  user: UserAdmin | null
  onClose: () => void
  onSuccess: (user: UserAdmin) => void
  onError: (msg: string) => void
}) {
  const [nom, setNom] = useState('')
  const [prenom, setPrenom] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (user) {
      setNom(user.nom || '')
      setPrenom(user.prenom || '')
    }
  }, [user])

  const handleSubmit = async () => {
    if (!user) return
    setSubmitting(true)
    try {
      const res = await fetch(`/api/admin/users/${user.id}/profile`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nom, prenom }),
      })
      if (!res.ok) throw new Error('Erreur de mise à jour')
      const updated = await res.json()
      onSuccess(updated)
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Erreur')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={!!user} onOpenChange={(v) => { if (!v) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Modifier le profil</DialogTitle>
          <DialogDescription>{user?.email}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div>
            <Label>Prénom</Label>
            <Input value={prenom} onChange={e => setPrenom(e.target.value)} />
          </div>
          <div>
            <Label>Nom</Label>
            <Input value={nom} onChange={e => setNom(e.target.value)} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Annuler</Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Enregistrement...' : 'Enregistrer'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ─── Manage Roles Modal ────────────────────────────────────────
function ManageRolesModal({ user, currentUserEmail, onClose, onSuccess, onError }: {
  user: UserAdmin | null
  currentUserEmail: string | null
  onClose: () => void
  onSuccess: (user: UserAdmin) => void
  onError: (msg: string) => void
}) {
  const [selectedRoles, setSelectedRoles] = useState<GlobalRole[]>([])
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (user) {
      setSelectedRoles([...(user.roles || [])])
    }
  }, [user])

  const isSelf = user && currentUserEmail && user.email.toLowerCase() === currentUserEmail.toLowerCase()
  const selfRemovingAdmin = isSelf && !selectedRoles.includes('ADMIN')

  const toggleRole = (role: GlobalRole) => {
    setSelectedRoles(prev =>
      prev.includes(role) ? prev.filter(r => r !== role) : [...prev, role]
    )
  }

  const handleSubmit = async () => {
    if (!user || selfRemovingAdmin) return
    setSubmitting(true)
    try {
      const res = await fetch(`/api/admin/users/${user.id}/roles`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roles: selectedRoles }),
      })
      if (!res.ok) throw new Error('Erreur de mise à jour des rôles')
      const updated = await res.json()
      onSuccess(updated)
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Erreur')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={!!user} onOpenChange={(v) => { if (!v) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Gérer les rôles</DialogTitle>
          <DialogDescription>
            {user ? `${getDisplayName(user)} — ${user.email}` : ''}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-4">
          {ASSIGNABLE_ROLES.map(role => (
            <label key={role} className="flex items-start gap-3 cursor-pointer p-2 rounded-lg hover:bg-secondary/50">
              <Checkbox
                checked={selectedRoles.includes(role)}
                onCheckedChange={() => toggleRole(role)}
                className="mt-0.5"
              />
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className={ROLE_COLORS[role]}>
                    {ROLE_LABELS[role]}
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground mt-1">{ROLE_DESCRIPTIONS[role]}</p>
              </div>
            </label>
          ))}

          {selectedRoles.length === 0 && (
            <p className="text-sm text-orange-500 bg-orange-50 dark:bg-orange-950/30 p-3 rounded-lg">
              ⚠ Un utilisateur sans rôle n&apos;aura accès à aucune fonctionnalité.
            </p>
          )}

          {selfRemovingAdmin && (
            <p className="text-sm text-red-500 bg-red-50 dark:bg-red-950/30 p-3 rounded-lg">
              ⚠ Retirer votre propre rôle ADMIN vous empêchera de gérer les utilisateurs.
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Annuler</Button>
          <Button onClick={handleSubmit} disabled={submitting || !!selfRemovingAdmin}>
            {submitting ? 'Enregistrement...' : 'Sauvegarder'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ─── Delete Confirmation Modal ─────────────────────────────────
function DeleteConfirmModal({ user, onClose, onSuccess, onError }: {
  user: UserAdmin | null
  onClose: () => void
  onSuccess: (id: number) => void
  onError: (msg: string) => void
}) {
  const [submitting, setSubmitting] = useState(false)

  const handleDelete = async () => {
    if (!user) return
    setSubmitting(true)
    try {
      const res = await fetch(`/api/admin/users/${user.id}`, { method: 'DELETE' })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.message || 'Erreur de suppression')
      }
      onSuccess(user.id)
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Erreur')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={!!user} onOpenChange={(v) => { if (!v) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Confirmer la suppression</DialogTitle>
          <DialogDescription>
            Êtes-vous sûr de vouloir supprimer <strong>{user ? getDisplayName(user) : ''}</strong> ?
            Cette action est irréversible.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Annuler</Button>
          <Button variant="destructive" onClick={handleDelete} disabled={submitting}>
            {submitting ? 'Suppression...' : 'Supprimer'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
