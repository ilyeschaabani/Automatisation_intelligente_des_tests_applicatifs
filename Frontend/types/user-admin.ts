export type GlobalRole = 'ADMIN' | 'TEST_MANAGER' | 'QA_ENGINEER' | 'DEVELOPER' | 'VIEWER' | 'TESTER'

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
  ADMIN: 'Admin',
  TEST_MANAGER: 'Test Manager',
  QA_ENGINEER: 'QA Engineer',
  DEVELOPER: 'Developer',
  VIEWER: 'Viewer',
  TESTER: 'Tester',
}

export const ROLE_COLORS: Record<GlobalRole, string> = {
  ADMIN: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
  TEST_MANAGER: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  QA_ENGINEER: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  DEVELOPER: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  VIEWER: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
  TESTER: 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-400',
}

export const ASSIGNABLE_ROLES: GlobalRole[] = ['ADMIN', 'TEST_MANAGER', 'QA_ENGINEER', 'DEVELOPER', 'VIEWER']

export const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMIN: 'Accès total, gestion utilisateurs et système',
  TEST_MANAGER: 'Création de campagnes, supervision des tests',
  QA_ENGINEER: 'Exécution de tests, consultation des résultats',
  DEVELOPER: 'Consultation des résultats seulement',
  VIEWER: 'Lecture seule sur les résultats',
}
