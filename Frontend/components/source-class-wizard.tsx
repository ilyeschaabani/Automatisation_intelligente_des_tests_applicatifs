'use client'

import { useEffect, useState, useCallback } from 'react'
import { Search, FileCode2, CheckCircle2, ChevronRight, Loader2, FolderOpen, Code2 } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { apiFetch } from '@/components/profile/GitHubIntegrationCard'

// ─── Types ────────────────────────────────────────────────────────────────────

export type WizardResult = {
  targetClassName: string
  filePath: string
  skeleton: string        // compact skeleton extracted client-side
  descriptionAI: string
  testData: string
}

type Props = {
  /** GitHub owner (parsed from env.gitRepoUrl) */
  owner: string
  /** GitHub repo name */
  repo: string
  /** Branch to browse */
  branch: string
  /** Called when the testeur completes the wizard */
  onComplete: (result: WizardResult) => void
  /** Called when the testeur cancels */
  onCancel: () => void
}

// ─── Skeleton extractor (client-side, no VRAM) ───────────────────────────────

function extractSkeleton(source: string, className: string): string {
  const lines: string[] = []

  // Package
  const pkgMatch = source.match(/(?:^|\n)\s*package\s+([\w.]+)\s*;/)
  if (pkgMatch) {
    lines.push(`package ${pkgMatch[1]};`)
    lines.push('')
  }

  // Class declaration
  const classMatch = source.match(/(?:public\s+)?(?:abstract\s+)?(?:class|interface|enum)\s+\w+[^{]*\{/)
  lines.push(classMatch ? classMatch[0].replace('{', '{').trim() : `public class ${className} {`)

  // Fields with annotations
  const fieldRe = /(@(?:Autowired|Value|Inject|Mock|InjectMocks)[^\n]*\n\s*)?((?:private|protected|public)\s+[\w<>[\],. ]+\s+\w+\s*;)/g
  let m: RegExpExecArray | null
  // eslint-disable-next-line no-cond-assign
  while ((m = fieldRe.exec(source)) !== null) {
    if (m[1]) lines.push(`    ${m[1].trim()}`)
    lines.push(`    ${m[2].trim()}`)
  }

  if (lines.length > 2) lines.push('')

  // Public/protected method signatures only
  const methodRe = /(?:public|protected)\s+[\w<>[\],. ]+\s+\w+\s*\([^)]*\)\s*(?:throws[^{]+)?\{/g
  // eslint-disable-next-line no-cond-assign
  while ((m = methodRe.exec(source)) !== null) {
    const sig = m[0].replace(/\{\s*$/, '').trim()
    lines.push(`    ${sig} { ... }`)
  }

  lines.push('}')

  const result = lines.join('\n')
  return result.length > 1500 ? result.substring(0, 1500) + '\n    // ... (truncated)\n}' : result
}

function getClassName(filePath: string): string {
  const parts = filePath.split('/')
  return parts[parts.length - 1].replace('.java', '')
}

function buildFileTree(files: string[]): Record<string, string[]> {
  const tree: Record<string, string[]> = {}
  for (const f of files) {
    const parts = f.split('/')
    const dir = parts.slice(0, -1).join('/')
    if (!tree[dir]) tree[dir] = []
    tree[dir].push(f)
  }
  return tree
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SourceClassWizard({ owner, repo, branch, onComplete, onCancel }: Props) {
  const [step, setStep] = useState<1 | 2 | 3>(1)

  // Step 1 state
  const [files, setFiles] = useState<string[]>([])
  const [filesLoading, setFilesLoading] = useState(true)
  const [filesError, setFilesError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [selectedFile, setSelectedFile] = useState<string | null>(null)
  const [expandedDirs, setExpandedDirs] = useState<Set<string>>(new Set())

  // Step 2 state
  const [skeleton, setSkeleton] = useState('')
  const [skeletonLoading, setSkeletonLoading] = useState(false)
  const [descriptionAI, setDescriptionAI] = useState('')
  const [testData, setTestData] = useState('')

  // Step 3 state — handled by parent (passed to onComplete)

  // ── Load file list ──────────────────────────────────────────────────────────
  useEffect(() => {
    setFilesLoading(true)
    setFilesError(null)
    ;(async () => {
      try {
        const res = await apiFetch(
          `/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/java-files?branch=${encodeURIComponent(branch)}`
        )
        const text = await res.text()
        if (!res.ok) {
          setFilesError(`GitHub API error ${res.status}: ${text.substring(0, 200)}`)
          return
        }
        if (!text || !text.trim()) {
          setFilesError('Empty response from server. Check that GitHub is connected in your profile.')
          return
        }
        let data: unknown
        try { data = JSON.parse(text) } catch {
          setFilesError(`Invalid response: ${text.substring(0, 100)}`)
          return
        }
        const list: string[] = Array.isArray(data) ? data : []
        setFiles(list)
        if (list.length > 0) {
          const firstDir = list[0].split('/').slice(0, -1).join('/')
          setExpandedDirs(new Set([firstDir]))
        }
      } catch (e) {
        setFilesError(e instanceof Error ? e.message : 'Network error — is the auth service running?')
      } finally {
        setFilesLoading(false)
      }
    })()
  }, [owner, repo, branch])

  // ── File select → fetch content → extract skeleton ─────────────────────────
  const handleSelectFile = useCallback(async (filePath: string) => {
    setSelectedFile(filePath)
    setSkeletonLoading(true)
    setSkeleton('')
    try {
      const res = await apiFetch(
        `/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/file-content?path=${encodeURIComponent(filePath)}&branch=${encodeURIComponent(branch)}`
      )
      const text = await res.text()
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.substring(0, 100)}`)
      if (!text || !text.trim()) throw new Error('Empty file response')
      let data: { content?: string }
      try { data = JSON.parse(text) } catch { throw new Error('Invalid JSON from file endpoint') }
      const content: string = data.content ?? ''
      const className = getClassName(filePath)
      const sk = extractSkeleton(content, className)
      setSkeleton(sk || `// No public methods found in ${className}`)
    } catch (e) {
      setSkeleton(`// Could not extract skeleton: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setSkeletonLoading(false)
    }
  }, [owner, repo, branch])

  // ── Filtered file list ──────────────────────────────────────────────────────
  const filtered = search.trim()
    ? files.filter((f) => f.toLowerCase().includes(search.trim().toLowerCase()))
    : files

  const tree = buildFileTree(filtered)
  const dirs = Object.keys(tree).sort()

  // ── Toggle directory ────────────────────────────────────────────────────────
  const toggleDir = (dir: string) => {
    setExpandedDirs((prev) => {
      const next = new Set(prev)
      if (next.has(dir)) next.delete(dir)
      else next.add(dir)
      return next
    })
  }

  // ── Step indicators ─────────────────────────────────────────────────────────
  const steps = [
    { n: 1, label: 'Browse source' },
    { n: 2, label: 'Describe test' },
    { n: 3, label: 'Generate' },
  ]

  return (
    <div className="flex flex-col gap-4">
      {/* Step indicator */}
      <div className="flex items-center gap-2">
        {steps.map((s, idx) => (
          <div key={s.n} className="flex items-center gap-2">
            <div
              className={`flex items-center justify-center w-7 h-7 rounded-full text-xs font-semibold border-2 transition-colors ${
                step > s.n
                  ? 'border-primary bg-primary text-primary-foreground'
                  : step === s.n
                    ? 'border-primary text-primary bg-background'
                    : 'border-muted text-muted-foreground bg-background'
              }`}
            >
              {step > s.n ? <CheckCircle2 size={14} /> : s.n}
            </div>
            <span className={`text-xs font-medium ${step === s.n ? 'text-foreground' : 'text-muted-foreground'}`}>
              {s.label}
            </span>
            {idx < steps.length - 1 && <ChevronRight size={14} className="text-muted-foreground" />}
          </div>
        ))}
      </div>

      {/* ── STEP 1 — File browser ─────────────────────────────────────────── */}
      {step === 1 && (
        <div className="space-y-3">
          <div>
            <p className="text-sm font-semibold text-foreground">Select the class to test</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              Browsing <span className="font-mono">{owner}/{repo}</span> @ <span className="font-mono">{branch}</span>
            </p>
          </div>

          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8 h-8 text-sm"
              placeholder="Search class name…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <ScrollArea className="h-64 rounded-md border border-border bg-muted/20 p-2">
            {filesLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground p-2">
                <Loader2 size={14} className="animate-spin" />
                Loading source files from GitHub…
              </div>
            ) : filesError ? (
              <p className="text-sm text-destructive p-2">{filesError}</p>
            ) : filtered.length === 0 ? (
              <p className="text-sm text-muted-foreground p-2">No Java files found.</p>
            ) : search.trim() ? (
              // Flat list when searching
              <div className="space-y-0.5">
                {filtered.map((f) => {
                  const className = getClassName(f)
                  const isSelected = selectedFile === f
                  return (
                    <button
                      key={f}
                      type="button"
                      onClick={() => handleSelectFile(f)}
                      className={`w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-sm transition-colors ${
                        isSelected
                          ? 'bg-primary/10 text-primary'
                          : 'hover:bg-muted text-foreground'
                      }`}
                    >
                      <FileCode2 size={13} className="shrink-0 text-blue-500" />
                      <span className="font-medium">{className}</span>
                      <span className="text-xs text-muted-foreground truncate">{f}</span>
                    </button>
                  )
                })}
              </div>
            ) : (
              // Tree view
              <div className="space-y-0.5">
                {dirs.map((dir) => {
                  const isOpen = expandedDirs.has(dir)
                  const shortDir = dir.replace('src/main/java/', '')
                  return (
                    <div key={dir}>
                      <button
                        type="button"
                        onClick={() => toggleDir(dir)}
                        className="w-full flex items-center gap-1.5 px-2 py-1 rounded text-left text-xs text-muted-foreground hover:bg-muted transition-colors"
                      >
                        <FolderOpen size={12} className={isOpen ? 'text-yellow-500' : 'text-muted-foreground'} />
                        <span className="font-mono">{shortDir}/</span>
                      </button>
                      {isOpen && (
                        <div className="ml-4 space-y-0.5">
                          {tree[dir].map((f) => {
                            const className = getClassName(f)
                            const isSelected = selectedFile === f
                            return (
                              <button
                                key={f}
                                type="button"
                                onClick={() => handleSelectFile(f)}
                                className={`w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-sm transition-colors ${
                                  isSelected
                                    ? 'bg-primary/10 text-primary font-semibold'
                                    : 'hover:bg-muted text-foreground'
                                }`}
                              >
                                <FileCode2 size={13} className="shrink-0 text-blue-400" />
                                {className}
                                {isSelected && <CheckCircle2 size={12} className="ml-auto text-primary" />}
                              </button>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </ScrollArea>

          {/* Selected file summary */}
          {selectedFile && (
            <div className="rounded-md border border-primary/30 bg-primary/5 px-3 py-2 flex items-center gap-2">
              {skeletonLoading ? (
                <Loader2 size={14} className="animate-spin text-primary" />
              ) : (
                <CheckCircle2 size={14} className="text-primary" />
              )}
              <div className="min-w-0">
                <p className="text-sm font-medium text-foreground">{getClassName(selectedFile)}</p>
                <p className="text-xs text-muted-foreground truncate font-mono">{selectedFile}</p>
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onCancel}>Cancel</Button>
            <Button
              type="button"
              size="sm"
              disabled={!selectedFile || skeletonLoading}
              onClick={() => setStep(2)}
            >
              Next — Describe test
              <ChevronRight size={14} className="ml-1" />
            </Button>
          </div>
        </div>
      )}

      {/* ── STEP 2 — Describe the test ─────────────────────────────────────── */}
      {step === 2 && (
        <div className="space-y-4">
          <div>
            <p className="text-sm font-semibold text-foreground">Describe the test</p>
            <span className="text-xs text-muted-foreground mt-0.5 flex items-center gap-1 flex-wrap">
              The AI will generate a test for <Badge variant="secondary" className="font-mono text-xs">{getClassName(selectedFile!)}</Badge>
            </span>
          </div>

          {/* Skeleton preview */}
          {skeleton && (
            <div className="space-y-1.5">
              <div className="flex items-center gap-1.5">
                <Code2 size={13} className="text-muted-foreground" />
                <Label className="text-xs text-muted-foreground">Detected API (sent to AI as context)</Label>
              </div>
              <ScrollArea className="h-28 rounded-md border border-border bg-muted/30">
                <pre className="p-2 text-xs font-mono text-foreground whitespace-pre-wrap">{skeleton}</pre>
              </ScrollArea>
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="wiz-desc" className="text-sm">
              AI description <span className="text-destructive">*</span>
            </Label>
            <Textarea
              id="wiz-desc"
              value={descriptionAI}
              onChange={(e) => setDescriptionAI(e.target.value)}
              placeholder="Ex: Test the add() method — verify a TestCase is created with the correct title and type"
              className="min-h-[80px] text-sm"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="wiz-data" className="text-sm">
              Test data <span className="text-muted-foreground text-xs">(optional JSON)</span>
            </Label>
            <Textarea
              id="wiz-data"
              value={testData}
              onChange={(e) => setTestData(e.target.value)}
              placeholder='{ "title": "Login test", "riskLevel": "LOW" }'
              className="min-h-[60px] text-sm font-mono"
            />
            <p className="text-xs text-muted-foreground">
              These values will be used directly in the generated test assertions.
            </p>
          </div>

          <div className="flex justify-between gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setStep(1)}>
              ← Back
            </Button>
            <Button
              type="button"
              size="sm"
              disabled={!descriptionAI.trim()}
              onClick={() => {
                onComplete({
                  targetClassName: getClassName(selectedFile!),
                  filePath: selectedFile!,
                  skeleton,
                  descriptionAI: descriptionAI.trim(),
                  testData: testData.trim(),
                })
              }}
            >
              Generate script →
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
