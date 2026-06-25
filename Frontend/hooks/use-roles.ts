'use client'

import { useEffect, useState } from 'react'
import type { GlobalRole } from '@/types/user-admin'

function parseRolesFromJwt(): GlobalRole[] {
  try {
    const token = typeof window !== 'undefined' ? localStorage.getItem('access_token') : null
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]))
      return Array.isArray(payload.roles) ? payload.roles : []
    }
  } catch { /* ignore */ }
  return []
}

export function useUserRoles() {
  const [roles, setRoles] = useState<GlobalRole[]>([])

  useEffect(() => {
    setRoles(parseRolesFromJwt())
  }, [])

  const hasRole = (...required: GlobalRole[]) =>
    required.some(r => roles.includes(r))

  const isAdmin = roles.includes('ADMIN')
  const canManageUsers = isAdmin
  const canCreateProject = hasRole('ADMIN', 'TEST_MANAGER')
  const canDeleteProject = isAdmin
  const canCreateEnvironment = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canDeleteEnvironment = hasRole('ADMIN', 'TEST_MANAGER')
  const canCreateSuite = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canDeleteSuite = hasRole('ADMIN', 'TEST_MANAGER')
  const canCreateTestCase = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canDeleteTestCase = hasRole('ADMIN', 'TEST_MANAGER')
  const canGenerateAI = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canCreateCampaign = hasRole('ADMIN', 'TEST_MANAGER')
  const canDeleteCampaign = hasRole('ADMIN', 'TEST_MANAGER')
  const canModifyCampaignTests = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canRunExecution = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canStopExecution = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canGenerateReport = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canLaunchScan = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canUpdateVulnStatus = hasRole('ADMIN', 'TEST_MANAGER')
  const canDownloadSecurityReport = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER', 'DEVELOPER')
  const canCreateUxEval = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canControlUxEval = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER')
  const canManageMembers = hasRole('ADMIN', 'TEST_MANAGER')
  const canConnectGitHub = hasRole('ADMIN', 'TEST_MANAGER', 'QA_ENGINEER', 'DEVELOPER')

  return {
    roles,
    hasRole,
    isAdmin,
    canManageUsers,
    canCreateProject,
    canDeleteProject,
    canCreateEnvironment,
    canDeleteEnvironment,
    canCreateSuite,
    canDeleteSuite,
    canCreateTestCase,
    canDeleteTestCase,
    canGenerateAI,
    canCreateCampaign,
    canDeleteCampaign,
    canModifyCampaignTests,
    canRunExecution,
    canStopExecution,
    canGenerateReport,
    canLaunchScan,
    canUpdateVulnStatus,
    canDownloadSecurityReport,
    canCreateUxEval,
    canControlUxEval,
    canManageMembers,
    canConnectGitHub,
  }
}
