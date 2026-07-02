'use client'

import { useEffect, useState } from 'react'
import { ChevronRight, Loader2, Wand2, CheckSquare, Square } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import type { SchemaField } from '@/app/api/llm/swagger/route'

// ── Types ─────────────────────────────────────────────────────────────────────

export type ScenarioType = 'HAPPY_PATH' | 'EXCEPTION' | 'NULL_INPUT' | 'WRONG_INPUT' | 'BOUNDARY'

export interface MethodInfo {
  name: string
  params: string   // e.g. "Long suiteId, CreateTestCaseRequest request"
  returnType: string // e.g. "TestCaseResponse"
  fullSignature: string
}

export interface AssertionItem {
  field: string
  type: string
  enumValues: string[]
  value: string
  enabled: boolean
  required: boolean
}

export interface ExceptionSpec {
  exceptionType: string
  messageContains: string
}

export interface ScenarioResult {
  methodName: string
  scenarioType: ScenarioType
  expectedBehavior: string   // built description for LLM
  assertions: AssertionItem[]
  exceptionSpec?: ExceptionSpec
}

interface ScenarioBuilderProps {
  skeleton: string
  testDataJson: string        // initial JSON from test data field (can be overridden)
  requestSchemaName?: string  // e.g. "CreateTestCaseRequest" — for test data generation
  suiteId?: number            // used to extract DTO fields from git when Swagger unavailable
  onComplete: (result: ScenarioResult, validatedTestData: string) => void
  onCancel: () => void
}

// ── Scenario definitions ──────────────────────────────────────────────────────

const SCENARIOS: { value: ScenarioType; label: string; description: string; color: string }[] = [
  {
    value: 'HAPPY_PATH',
    label: '✅ Cas nominal',
    description: 'La méthode s\'exécute normalement et retourne un résultat valide',
    color: 'bg-green-50 border-green-200 text-green-800',
  },
  {
    value: 'EXCEPTION',
    label: '💥 Exception',
    description: 'La méthode doit lever une exception dans ce contexte',
    color: 'bg-red-50 border-red-200 text-red-800',
  },
  {
    value: 'NULL_INPUT',
    label: '🔲 Entrée nulle',
    description: 'Un paramètre obligatoire est null — exception attendue',
    color: 'bg-orange-50 border-orange-200 text-orange-800',
  },
  {
    value: 'WRONG_INPUT',
    label: '⚠️ Mauvaise entrée',
    description: 'Valeur invalide (hors enum, format incorrect…)',
    color: 'bg-yellow-50 border-yellow-200 text-yellow-800',
  },
  {
    value: 'BOUNDARY',
    label: '📏 Valeur limite',
    description: 'Valeurs aux limites minimales ou maximales autorisées',
    color: 'bg-blue-50 border-blue-200 text-blue-800',
  },
]

const EXCEPTION_TYPES = [
  'RuntimeException',
  'IllegalArgumentException',
  'NullPointerException',
  'ResponseStatusException (400 Bad Request)',
  'ResponseStatusException (403 Forbidden)',
  'ResponseStatusException (404 Not Found)',
]

// ── Skeleton parser ───────────────────────────────────────────────────────────

function parseMethods(skeleton: string): MethodInfo[] {
  const methods: MethodInfo[] = []
  // Match: public ReturnType methodName(params)
  const methodRegex = /public\s+([\w<>[\],\s]+?)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws[^{]+)?\{/g
  let match
  while ((match = methodRegex.exec(skeleton)) !== null) {
    const returnType = match[1].trim()
    const name = match[2].trim()
    const params = match[3].trim()
    // Skip constructors and common non-test methods
    if (['get', 'set', 'is', 'hashCode', 'equals', 'toString'].some(p => name.startsWith(p))) continue
    methods.push({ name, params, returnType, fullSignature: `${returnType} ${name}(${params})` })
  }
  return methods
}

function extractReturnType(returnType: string): string {
  // "List<TestCaseResponse>" → "TestCaseResponse"
  // "ResponseEntity<TestCaseResponse>" → "TestCaseResponse"
  // "TestCaseResponse" → "TestCaseResponse"
  const inner = returnType.match(/<([^>]+)>/)
  if (inner) return inner[1].trim()
  return returnType.trim()
}

function parseTestData(json: string): Record<string, string> {
  if (!json || !json.trim()) return {}
  try {
    const parsed = JSON.parse(json)
    if (typeof parsed === 'object' && parsed !== null) {
      return Object.fromEntries(
        Object.entries(parsed).map(([k, v]) => [k, String(v)])
      )
    }
  } catch { /* ignore */ }
  return {}
}

// ── Main component ────────────────────────────────────────────────────────────

type Step = 2 | 3 | '4a' | '4b'

export function ScenarioBuilder({ skeleton, testDataJson, requestSchemaName, suiteId, onComplete, onCancel }: ScenarioBuilderProps) {
  const [step, setStep] = useState<Step>(2)
  const [methods, setMethods] = useState<MethodInfo[]>([])
  const [selectedMethod, setSelectedMethod] = useState<MethodInfo | null>(null)
  const [scenario, setScenario] = useState<ScenarioType | null>(null)

  // Données de test
  const [requestFields, setRequestFields] = useState<SchemaField[]>([])
  const [generatingTestData, setGeneratingTestData] = useState(false)
  const [validatedTestData, setValidatedTestData] = useState<string>(testDataJson)
  const [testDataError, setTestDataError] = useState<string | null>(null)
  const [testDataValidated, setTestDataValidated] = useState(false)

  // Assertions
  const [assertions, setAssertions] = useState<AssertionItem[]>([])
  const [exceptionSpec, setExceptionSpec] = useState<ExceptionSpec>({ exceptionType: 'RuntimeException', messageContains: '' })
  const [schemaLoading, setSchemaLoading] = useState(false)
  const [schemaError, setSchemaError] = useState<string | null>(null)

  async function fetchDtoFields(className: string): Promise<SchemaField[]> {
    if (!suiteId || !className) return []
    try {
      const r = await fetch('/api/llm/extract-dto-fields', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ suiteId, className }),
      })
      if (!r.ok) return []
      const data = await r.json()
      return (data.fields ?? []) as SchemaField[]
    } catch {
      return []
    }
  }

  // Parse methods from skeleton on mount
  useEffect(() => {
    if (skeleton) {
      setMethods(parseMethods(skeleton))
    }
  }, [skeleton])

  // When method + scenario selected, fetch schema and build assertions
  useEffect(() => {
    if (!selectedMethod || !scenario) return
    if (scenario !== 'HAPPY_PATH' && scenario !== 'WRONG_INPUT') return

    const returnType = extractReturnType(selectedMethod.returnType)
    if (!returnType || returnType === 'void' || returnType === 'String') {
      setAssertions([])
      return
    }

    setSchemaLoading(true)
    setSchemaError(null)
    const testData = parseTestData(testDataJson)

    // Fields that are output-only (never in test data, but meaningful to assert)
    const OUTPUT_ONLY = new Set(['id', 'suiteId', 'projectId', 'createdAt', 'updatedAt'])

    // Smart defaults based on field name + context (suite type is not available here,
    // but generated=true is always true for UNIT/INTEGRATION suites)
    const SMART_DEFAULTS: Record<string, string> = {
      active:    'true',
      generated: 'true',   // UNIT/INTEGRATION suites always generate via AI
      flaky:     'false',
    }

    const buildFromFields = (fields: SchemaField[]) => {
      return fields.map(f => {
        const tdValue = testData[f.name] ?? testData[f.name.toLowerCase()] ?? null
        const smartDefault = SMART_DEFAULTS[f.name] ?? null
        const isOutputOnly = OUTPUT_ONLY.has(f.name)
        const value = tdValue ?? smartDefault ?? (isOutputOnly ? 'NOT_NULL' : '')
        const enabled = tdValue !== null || smartDefault !== null || f.required
        return { field: f.name, type: f.type, enumValues: f.enumValues ?? [], value, enabled, required: f.required }
      })
    }

    fetch(`/api/llm/swagger?schema=${encodeURIComponent(returnType)}`)
      .then(r => r.ok ? r.json() : null)
      .then(async (data: { fields?: SchemaField[] } | null) => {
        if (data?.fields?.length) {
          setAssertions(buildFromFields(data.fields))
          return
        }
        const dtoFields = await fetchDtoFields(returnType)
        if (dtoFields.length > 0) {
          setAssertions(buildFromFields(dtoFields))
        } else {
          setAssertions(buildAssertionsFromTestData(testData))
        }
      })
      .catch(async () => {
        const dtoFields = await fetchDtoFields(returnType)
        if (dtoFields.length > 0) {
          setAssertions(buildFromFields(dtoFields))
        } else {
          setAssertions(buildAssertionsFromTestData(testData))
        }
      })
      .finally(() => setSchemaLoading(false))
  }, [selectedMethod, scenario, testDataJson])

  const handleMethodSelect = (m: MethodInfo) => {
    setSelectedMethod(m)
    setScenario(null)
    setAssertions([])
    setStep(3)
  }

  const handleScenarioSelect = async (s: ScenarioType) => {
    setScenario(s)
    setTestDataValidated(false)
    setTestDataError(null)

    // Fetch REQUEST schema fields first, then generate test data
    const schemaToFetch = requestSchemaName ?? inferRequestSchemaName(selectedMethod?.returnType ?? '')
    setStep('4a')

    try {
      // Fetch REQUEST schema: try Swagger first, then DTO extraction from git
      let fields: SchemaField[] = []
      if (schemaToFetch) {
        const r = await fetch(`/api/llm/swagger?schema=${encodeURIComponent(schemaToFetch)}`)
        if (r.ok) {
          const data = await r.json()
          fields = data.fields ?? []
        }
      }
      if (fields.length === 0) {
        const requestDtoName = inferRequestDtoName(selectedMethod)
        if (requestDtoName) {
          fields = await fetchDtoFields(requestDtoName)
        }
      }
      if (fields.length > 0) {
        setRequestFields(fields)
      }

      // Generate test data using LLM
      setGeneratingTestData(true)
      const payload = {
        methodName: selectedMethod?.name,
        scenarioType: s,
        targetClassName: selectedMethod?.returnType,
        skeleton,
        fields: fields.map(f => ({
          name: f.name,
          type: f.type,
          enumValues: f.enumValues,
          required: f.required,
        })),
      }

      const res = await fetch('/api/llm/generate-testdata', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (res.ok) {
        const data = await res.json()
        setValidatedTestData(data.testData ?? '{}')
      } else {
        setValidatedTestData(testDataJson || '{}')
      }
    } catch {
      setValidatedTestData(testDataJson || '{}')
    } finally {
      setGeneratingTestData(false)
    }
  }

  /** Infer the request schema name from the response type name */
  function inferRequestSchemaName(returnType: string): string {
    const base = extractReturnType(returnType).replace(/Response$/, '')
    return base ? `Create${base}Request` : ''
  }

  /** Infer the request DTO class name from method params (e.g. "OrderRequest" from "OrderRequest request") */
  function inferRequestDtoName(method: MethodInfo | null): string | null {
    if (!method?.params) return null
    const params = method.params.split(',').map(p => p.trim())
    for (const param of params) {
      const parts = param.split(/\s+/)
      if (parts.length >= 2) {
        const typeName = parts[0]
        if (typeName.endsWith('Request') || typeName.endsWith('Dto') || typeName.endsWith('DTO')) {
          return typeName
        }
      }
    }
    // Fallback: infer from method name (createOrder → OrderRequest)
    const match = method.name.match(/^(?:create|add|update|save)(\w+)$/i)
    if (match) {
      return match[1] + 'Request'
    }
    return null
  }

  function buildAssertionsFromTestData(testData: Record<string, unknown>): AssertionItem[] {
    return Object.entries(testData)
      .filter(([key]) => !['id', 'createdAt', 'updatedAt'].includes(key))
      .filter(([, val]) => typeof val !== 'object' || val === null)
      .map(([key, val]) => ({
        field: key,
        type: typeof val === 'number' ? 'number' : typeof val === 'boolean' ? 'boolean' : 'string',
        enumValues: [],
        value: val != null ? String(val) : '',
        enabled: val != null,
        required: false,
      }))
  }

  const handleValidateTestData = () => {
    // Basic JSON validation
    try {
      JSON.parse(validatedTestData)
      setTestDataError(null)
      setTestDataValidated(true)
      // Move to step 4b — build assertions from validated test data
      buildAssertions(validatedTestData)
      setStep('4b')
    } catch {
      setTestDataError('JSON invalide — vérifiez la syntaxe')
    }
  }

  const buildAssertions = (testDataStr: string) => {
    if (!selectedMethod || !scenario) return
    if (scenario !== 'HAPPY_PATH' && scenario !== 'WRONG_INPUT') return

    const returnType = extractReturnType(selectedMethod.returnType)
    if (!returnType || returnType === 'void') { setAssertions([]); return }

    setSchemaLoading(true)
    const testData = parseTestData(testDataStr)
    const OUTPUT_ONLY = new Set(['id', 'suiteId', 'projectId', 'createdAt', 'updatedAt'])
    const SMART_DEFAULTS: Record<string, string> = { active: 'true', generated: 'true', flaky: 'false' }

    const buildFromFields = (fields: SchemaField[]) => {
      return fields.map(f => {
        const tdValue      = testData[f.name] ?? testData[f.name.toLowerCase()] ?? null
        const smartDefault = SMART_DEFAULTS[f.name] ?? null
        const isOutputOnly = OUTPUT_ONLY.has(f.name)
        const value        = tdValue ?? smartDefault ?? (isOutputOnly ? 'NOT_NULL' : '')
        const enabled      = tdValue !== null || smartDefault !== null || f.required
        return { field: f.name, type: f.type, enumValues: f.enumValues ?? [], value, enabled, required: f.required }
      })
    }

    fetch(`/api/llm/swagger?schema=${encodeURIComponent(returnType)}`)
      .then(r => r.ok ? r.json() : null)
      .then(async (data: { fields?: SchemaField[] } | null) => {
        if (data?.fields?.length) {
          setAssertions(buildFromFields(data.fields))
          return
        }
        const dtoFields = await fetchDtoFields(returnType)
        if (dtoFields.length > 0) {
          setAssertions(buildFromFields(dtoFields))
        } else {
          setAssertions(buildAssertionsFromTestData(testData))
        }
      })
      .catch(async () => {
        const dtoFields = await fetchDtoFields(returnType)
        if (dtoFields.length > 0) {
          setAssertions(buildFromFields(dtoFields))
        } else {
          setAssertions(buildAssertionsFromTestData(testData))
        }
      })
      .finally(() => setSchemaLoading(false))
  }

  const buildExpectedBehavior = (): string => {
    if (!scenario || !selectedMethod) return ''

    if (scenario === 'HAPPY_PATH') {
      const enabled = assertions.filter(a => a.enabled && a.value)
      if (!enabled.length) return `retourne un ${extractReturnType(selectedMethod.returnType)} valide`
      return enabled.map(a => `${a.field}='${a.value}'`).join(', ')
    }

    if (scenario === 'EXCEPTION' || scenario === 'NULL_INPUT') {
      const msg = exceptionSpec.messageContains ? ` avec message contenant '${exceptionSpec.messageContains}'` : ''
      return `lève ${exceptionSpec.exceptionType}${msg}`
    }

    if (scenario === 'WRONG_INPUT') {
      const invalid = assertions.filter(a => a.enabled)
      return invalid.length
        ? `lève une exception car ${invalid.map(a => `${a.field}='${a.value}'`).join(', ')} est invalide`
        : 'lève une exception avec une valeur invalide'
    }

    if (scenario === 'BOUNDARY') {
      return `se comporte correctement aux valeurs limites`
    }

    return ''
  }

  const handleFinish = () => {
    if (!selectedMethod || !scenario) return
    onComplete(
      {
        methodName: selectedMethod.name,
        scenarioType: scenario,
        expectedBehavior: buildExpectedBehavior(),
        assertions,
        exceptionSpec: (scenario === 'EXCEPTION' || scenario === 'NULL_INPUT') ? exceptionSpec : undefined,
      },
      validatedTestData
    )
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-4">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <span className={step === 2 ? 'text-primary font-semibold' : 'text-muted-foreground'}>① Méthode</span>
        <ChevronRight size={12} />
        <span className={step === 3 ? 'text-primary font-semibold' : 'text-muted-foreground'}>② Scénario</span>
        <ChevronRight size={12} />
        <span className={step === '4a' ? 'text-primary font-semibold' : 'text-muted-foreground'}>③ Test Data</span>
        <ChevronRight size={12} />
        <span className={step === '4b' ? 'text-primary font-semibold' : 'text-muted-foreground'}>④ Assertions</span>
      </div>

      {/* ── Step 2: Method selection ── */}
      {(step === 2 || step === 3 || step === '4a' || step === '4b') && (
        <div className="space-y-2">
          <Label className="text-sm font-semibold">
            Étape 2 — Méthode à tester
          </Label>
          {methods.length === 0 ? (
            <p className="text-xs text-muted-foreground">Aucune méthode publique trouvée dans le squelette.</p>
          ) : (
            <div className="grid gap-1.5">
              {methods.map(m => (
                <button
                  key={m.fullSignature}
                  type="button"
                  onClick={() => handleMethodSelect(m)}
                  className={`text-left rounded-md border px-3 py-2 text-sm transition-colors ${
                    selectedMethod?.name === m.name
                      ? 'border-primary bg-primary/5 font-medium'
                      : 'border-border hover:border-primary/50 hover:bg-muted/50'
                  }`}
                >
                  <span className="font-mono text-xs text-primary">{m.returnType}</span>
                  <span className="font-mono text-xs font-semibold ml-1">{m.name}</span>
                  <span className="font-mono text-xs text-muted-foreground">({m.params})</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── Step 3: Scenario selection ── */}
      {(step === 3 || step === '4a' || step === '4b') && selectedMethod && (
        <div className="space-y-2">
          <Label className="text-sm font-semibold">
            Étape 3 — Type de scénario
          </Label>
          <div className="grid gap-1.5">
            {SCENARIOS.map(s => (
              <button
                key={s.value}
                type="button"
                onClick={() => handleScenarioSelect(s.value)}
                className={`text-left rounded-md border px-3 py-2 text-sm transition-colors ${
                  scenario === s.value
                    ? s.color + ' font-medium'
                    : 'border-border hover:border-primary/50 hover:bg-muted/40'
                }`}
              >
                <div className="font-medium">{s.label}</div>
                <div className="text-xs text-muted-foreground mt-0.5">{s.description}</div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ── Step 4a: Test Data — génération LLM + validation humaine ── */}
      {step === '4a' && scenario && selectedMethod && (
        <div className="space-y-3">
          <Label className="text-sm font-semibold">
            Étape 3 — Test Data
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              Générées par l'IA selon le scénario · Modifiez si nécessaire
            </span>
          </Label>

          {/* Scenario badge */}
          <div className={`text-xs px-2 py-1 rounded-md border inline-flex items-center gap-1 ${
            SCENARIOS.find(s => s.value === scenario)?.color ?? ''
          }`}>
            {SCENARIOS.find(s => s.value === scenario)?.label}
            <span className="text-muted-foreground ml-1">pour {selectedMethod.name}()</span>
          </div>

          {/* Generating spinner */}
          {generatingTestData && (
            <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
              <Loader2 size={14} className="animate-spin" />
              L'IA génère les données de test…
            </div>
          )}

          {/* Editable JSON */}
          {!generatingTestData && (
            <>
              <textarea
                className="w-full rounded-md border border-border bg-background font-mono text-xs p-3 resize-y min-h-[120px] focus:outline-none focus:ring-2 focus:ring-primary/40"
                value={validatedTestData}
                onChange={e => { setValidatedTestData(e.target.value); setTestDataValidated(false); setTestDataError(null) }}
                spellCheck={false}
              />
              {testDataError && (
                <p className="text-xs text-destructive">{testDataError}</p>
              )}
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  onClick={handleValidateTestData}
                  className="gap-2"
                  disabled={!validatedTestData.trim()}
                >
                  <CheckSquare size={13} />
                  Valider et continuer
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => handleScenarioSelect(scenario)}
                  disabled={generatingTestData}
                  className="gap-2"
                >
                  <Wand2 size={13} />
                  Régénérer
                </Button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Step 4b: Assertions / Exception spec ── */}
      {step === '4b' && scenario && selectedMethod && (
        <div className="space-y-3">
          <Label className="text-sm font-semibold">
            Étape 4 — Résultat attendu
          </Label>

          {/* HAPPY_PATH / WRONG_INPUT — assertion checklist from Swagger */}
          {(scenario === 'HAPPY_PATH' || scenario === 'WRONG_INPUT') && (
            <>
              {schemaLoading && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Loader2 size={12} className="animate-spin" />
                  Chargement du schéma depuis Swagger…
                </div>
              )}
              {schemaError && (
                <p className="text-xs text-muted-foreground italic">{schemaError}</p>
              )}
              {!schemaLoading && assertions.length > 0 && (
                <>
                  {/* Header */}
                  <div className="flex items-center gap-2 px-3 py-1.5 bg-muted/60 rounded-t-md border border-border border-b-0 text-[10px] text-muted-foreground font-medium">
                    <span className="w-5" />
                    <span className="w-28">Champ</span>
                    <span className="w-12">Type</span>
                    <span className="flex-1">Valeur attendue</span>
                    <span className="w-16 text-right">Source</span>
                  </div>
                  <div className="rounded-b-md border border-border divide-y divide-border text-sm">
                    {assertions.map((a, i) => {
                      const testData = parseTestData(testDataJson)
                      const fromTestData = testData[a.field] !== undefined || testData[a.field?.toLowerCase()] !== undefined
                      const fromDefault  = ['active','generated','flaky'].includes(a.field)
                      const isNotNull    = a.value === 'NOT_NULL'
                      const sourceLabel  = fromTestData ? '📥 testData' : fromDefault ? '⚙️ défaut' : isNotNull ? '🔑 not null' : ''

                      return (
                        <div key={a.field} className={`flex items-center gap-2 px-3 py-2 ${!a.enabled ? 'opacity-40' : ''}`}>
                          <button
                            type="button"
                            onClick={() => setAssertions(prev =>
                              prev.map((x, j) => j === i ? { ...x, enabled: !x.enabled } : x)
                            )}
                            className="shrink-0 text-primary"
                          >
                            {a.enabled ? <CheckSquare size={15} /> : <Square size={15} className="text-muted-foreground" />}
                          </button>
                          <span className="font-mono text-xs w-28 shrink-0">{a.field}</span>
                          <span className="text-xs text-muted-foreground w-12 shrink-0">{a.type}</span>

                          {/* Value display — read-only for auto-filled, editable otherwise */}
                          {isNotNull ? (
                            <span className="flex-1 font-mono text-xs text-muted-foreground italic">≠ null</span>
                          ) : a.enumValues.length > 0 ? (
                            <select
                              className={`flex-1 text-xs border rounded px-2 py-1 ${fromTestData ? 'bg-green-50 border-green-200' : ''}`}
                              value={a.value}
                              onChange={e => setAssertions(prev =>
                                prev.map((x, j) => j === i ? { ...x, value: e.target.value } : x)
                              )}
                            >
                              <option value="">— choisir —</option>
                              {a.enumValues.map(v => <option key={v} value={v}>{v}</option>)}
                            </select>
                          ) : a.type === 'boolean' ? (
                            <select
                              className={`flex-1 text-xs border rounded px-2 py-1 ${fromTestData || fromDefault ? 'bg-green-50 border-green-200' : ''}`}
                              value={a.value || 'true'}
                              onChange={e => setAssertions(prev =>
                                prev.map((x, j) => j === i ? { ...x, value: e.target.value } : x)
                              )}
                            >
                              <option value="true">true</option>
                              <option value="false">false</option>
                            </select>
                          ) : (
                            <Input
                              className={`flex-1 h-7 text-xs ${fromTestData ? 'bg-green-50 border-green-200 focus:border-green-400' : ''}`}
                              value={a.value}
                              placeholder={fromTestData ? '' : 'valeur attendue (optionnel)'}
                              readOnly={fromTestData}
                              onChange={e => !fromTestData && setAssertions(prev =>
                                prev.map((x, j) => j === i ? { ...x, value: e.target.value } : x)
                              )}
                            />
                          )}

                          {/* Source badge */}
                          {sourceLabel && (
                            <span className="text-[10px] w-16 text-right shrink-0 text-muted-foreground">{sourceLabel}</span>
                          )}
                          {a.required && !sourceLabel && (
                            <Badge variant="outline" className="text-[10px] h-4 shrink-0">required</Badge>
                          )}
                        </div>
                      )
                    })}
                  </div>
                  <p className="text-[11px] text-muted-foreground pt-1">
                    📥 = valeur depuis tes test data &nbsp;·&nbsp; ⚙️ = valeur par défaut &nbsp;·&nbsp; 🔑 = assertion ≠ null
                  </p>
                </>
              )}
              {!schemaLoading && assertions.length === 0 && !schemaError && (
                <p className="text-xs text-muted-foreground">
                  Schéma non disponible — les assertions seront déduites automatiquement par le LLM.
                </p>
              )}
            </>
          )}

          {/* EXCEPTION / NULL_INPUT — exception type selector */}
          {(scenario === 'EXCEPTION' || scenario === 'NULL_INPUT') && (
            <div className="space-y-2">
              <div className="space-y-1">
                <Label className="text-xs">Type d'exception attendue</Label>
                <select
                  className="w-full text-sm border rounded px-3 py-1.5"
                  value={exceptionSpec.exceptionType}
                  onChange={e => setExceptionSpec(prev => ({ ...prev, exceptionType: e.target.value }))}
                >
                  {EXCEPTION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Message d'erreur contient <span className="text-muted-foreground">(optionnel)</span></Label>
                <Input
                  className="text-sm h-8"
                  value={exceptionSpec.messageContains}
                  onChange={e => setExceptionSpec(prev => ({ ...prev, messageContains: e.target.value }))}
                  placeholder="ex: Suite not found, Action non autorisée…"
                />
              </div>
            </div>
          )}

          {/* BOUNDARY — free text */}
          {scenario === 'BOUNDARY' && (
            <div className="space-y-1">
              <Label className="text-xs">Valeur limite à tester</Label>
              <Input
                className="text-sm h-8"
                placeholder="ex: title vide '', priority = 0, maxDuration = 0…"
                onChange={e => setAssertions([{ field: 'boundary', type: 'string', enumValues: [], value: e.target.value, enabled: true, required: false }])}
              />
            </div>
          )}

          {/* Preview */}
          {buildExpectedBehavior() && (
            <div className="rounded-md bg-muted/50 border border-border px-3 py-2 text-xs">
              <span className="font-semibold text-muted-foreground">LLM recevra : </span>
              <span className="font-mono">
                test_{selectedMethod.name}_{scenario.toLowerCase()}() — {buildExpectedBehavior()}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Action buttons */}
      <div className="flex justify-between pt-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
          Annuler
        </Button>
        {step === '4b' && scenario && selectedMethod && (
          <Button type="button" size="sm" className="gap-2" onClick={handleFinish}>
            <Wand2 size={14} />
            Générer le script
          </Button>
        )}
      </div>
    </div>
  )
}
