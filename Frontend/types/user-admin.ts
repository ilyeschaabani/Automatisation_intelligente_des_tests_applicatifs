export type GlobalRole = 'ADMIN' | 'TEST_MANAGER' | 'TESTEUR' | 'OBSERVATEUR'

export interface UserAdmin {
  id: number
  nom: string | null
  prenom: string | null
  email: string
  imageUrl: string | null
  githubUsername: string | null
  githubConnected: boolean | null
  roles: GlobalRole[]
  enabled: boolean
  superAdmin: boolean
  createdAt: string | null
  lastLoginAt: string | null
}

export const ROLE_LABELS: Record<GlobalRole, string> = {
  ADMIN: 'Administrateur',
  TEST_MANAGER: 'Chef de test',
  TESTEUR: 'Testeur',
  OBSERVATEUR: 'Observateur',
}

export const ROLE_COLORS: Record<GlobalRole, string> = {
  ADMIN: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
  TEST_MANAGER: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  TESTEUR: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  OBSERVATEUR: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

export const ASSIGNABLE_ROLES: GlobalRole[] = ['ADMIN', 'TEST_MANAGER', 'TESTEUR', 'OBSERVATEUR']

export const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMIN: 'Accès total, gestion utilisateurs et système',
  TEST_MANAGER: 'Créer des projets/campagnes, gérer les membres, superviser',
  TESTEUR: 'Créer des cas de test, exécuter, évaluations UX, voir les rapports',
  OBSERVATEUR: 'Lecture seule : consulter résultats, rapports, évaluations',
}
