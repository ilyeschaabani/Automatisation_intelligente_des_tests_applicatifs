'use client'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'

import type { DiscoveryQuestion } from '@/lib/api-client'

function normalizeOptions(value: DiscoveryQuestion['options']): string[] {
  if (!Array.isArray(value)) return []
  return value.map((opt) => String(opt))
}

function shouldUseTextarea(question: DiscoveryQuestion): boolean {
  const expected = String(question.expected_format ?? '').toLowerCase()
  const example = String(question.example ?? '')

  if (expected.includes('json') || expected.includes('yaml')) return true
  if (expected.includes('object') || expected.includes('array')) return true
  if (example.includes('\n')) return true
  if (example.length > 120) return true

  return false
}

export function DiscoveryCompletionDialog(props: {
  open: boolean
  onOpenChange: (open: boolean) => void
  questions: DiscoveryQuestion[]
  answers: Record<string, string>
  onAnswerChange: (jsonPath: string, value: string) => void
  onSubmit: () => void
  submitting?: boolean
}) {
  const { open, onOpenChange, questions, answers, onAnswerChange, onSubmit, submitting } = props

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Discovery needs a bit more input</DialogTitle>
          <DialogDescription>
            Answer the questions below to help the extractor complete endpoint discovery.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-auto pr-1">
          {questions.length === 0 ? (
            <p className="text-sm text-muted-foreground">No questions were provided.</p>
          ) : null}

          {questions.map((q) => {
            const options = normalizeOptions(q.options)
            const value = answers[q.json_path] ?? ''
            const useTextarea = options.length === 0 && shouldUseTextarea(q)

            return (
              <div key={q.json_path} className="rounded-md border border-border p-4 space-y-3">
                <div className="space-y-1">
                  <p className="text-sm font-medium text-foreground">{q.json_path}</p>
                  <p className="text-sm text-muted-foreground">{q.reason}</p>
                </div>

                <div className="text-xs text-muted-foreground">
                  <span className="font-medium text-foreground">Expected:</span> {q.expected_format}
                </div>

                {q.example ? (
                  <div className="space-y-1">
                    <p className="text-xs font-medium text-foreground">Example</p>
                    <pre className="max-h-40 overflow-auto rounded-md border border-border bg-muted p-2 text-xs whitespace-pre-wrap">
                      {q.example}
                    </pre>
                  </div>
                ) : null}

                <div className="space-y-2">
                  <Label htmlFor={q.json_path}>Your answer</Label>

                  {options.length > 0 ? (
                    <Select value={value} onValueChange={(v) => onAnswerChange(q.json_path, v)}>
                      <SelectTrigger id={q.json_path}>
                        <SelectValue placeholder="Select an option" />
                      </SelectTrigger>
                      <SelectContent>
                        {options.map((opt) => (
                          <SelectItem key={opt} value={opt}>
                            {opt}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : useTextarea ? (
                    <Textarea
                      id={q.json_path}
                      value={value}
                      onChange={(e) => onAnswerChange(q.json_path, e.target.value)}
                      placeholder={q.example ? String(q.example) : ''}
                      rows={4}
                    />
                  ) : (
                    <Input
                      id={q.json_path}
                      value={value}
                      onChange={(e) => onAnswerChange(q.json_path, e.target.value)}
                      placeholder={q.example ? String(q.example) : ''}
                    />
                  )}
                </div>
              </div>
            )}
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={Boolean(submitting)}>
            Close
          </Button>
          <Button type="button" onClick={onSubmit} disabled={Boolean(submitting)}>
            {submitting ? 'Submitting…' : 'Submit answers'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
