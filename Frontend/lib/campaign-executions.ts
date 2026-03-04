export type CampaignExecutionStatus = 'QUEUED' | 'RUNNING' | 'FINISHED' | 'ERROR'
export type CampaignExecutionScope = 'CAMPAIGN' | 'SELECTED_TESTS'

export type CampaignExecution = {
  id: string
  campaignId: string
  scope: CampaignExecutionScope
  selectedTests?: string[]
  status: CampaignExecutionStatus
  createdAt: string
  duration?: string
}

const STORAGE_KEY = 'aitap:campaign-executions:v1'

type StoreShape = Record<string, CampaignExecution[]>

function safeParse(json: string): unknown {
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function readStore(): StoreShape {
  if (typeof window === 'undefined') return {}
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) return {}

  const parsed = safeParse(raw)
  if (!isRecord(parsed)) return {}

  const out: StoreShape = {}
  for (const [key, value] of Object.entries(parsed)) {
    if (!Array.isArray(value)) continue
    out[key] = value.filter((v): v is CampaignExecution => {
      return (
        !!v &&
        typeof v === 'object' &&
        typeof (v as CampaignExecution).id === 'string' &&
        typeof (v as CampaignExecution).campaignId === 'string' &&
        typeof (v as CampaignExecution).scope === 'string' &&
        typeof (v as CampaignExecution).status === 'string' &&
        typeof (v as CampaignExecution).createdAt === 'string'
      )
    })
  }
  return out
}

function writeStore(store: StoreShape) {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
}

function newExecutionId(): string {
  const rand = Math.random().toString(16).slice(2, 8)
  return `run-${Date.now()}-${rand}`
}

export function listCampaignExecutions(campaignId: string): CampaignExecution[] {
  const store = readStore()
  const list = store[campaignId] ?? []
  return [...list].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
}

export function createCampaignExecution(input: {
  campaignId: string
  scope: CampaignExecutionScope
  selectedTests?: string[]
}): CampaignExecution {
  const store = readStore()
  const now = new Date().toISOString()
  const created: CampaignExecution = {
    id: newExecutionId(),
    campaignId: input.campaignId,
    scope: input.scope,
    selectedTests: input.selectedTests?.length ? [...input.selectedTests] : undefined,
    status: 'QUEUED',
    createdAt: now,
    duration: '—',
  }

  const prev = store[input.campaignId] ?? []
  store[input.campaignId] = [created, ...prev]
  writeStore(store)
  return created
}

export function deleteCampaignExecution(campaignId: string, executionId: string) {
  const store = readStore()
  const prev = store[campaignId] ?? []
  store[campaignId] = prev.filter((e) => e.id !== executionId)
  writeStore(store)
}
