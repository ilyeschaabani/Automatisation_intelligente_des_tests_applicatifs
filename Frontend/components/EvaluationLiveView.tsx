'use client'

import { useEffect, useRef, useState } from 'react'
import { Loader2, Send, Wifi, WifiOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { type ChatMessage, type StreamEventType } from '@/hooks/useEvaluationStream'

// ── Chat bubble styles per event type ────────────────────────────────────────

function bubbleStyle(type: StreamEventType): {
  bg: string; border: string; textColor: string; prefix: string
} {
  switch (type) {
    case 'THINKING':
      return { bg: 'bg-muted/40', border: 'border-border/50', textColor: 'text-muted-foreground', prefix: '🔍' }
    case 'OBSERVATION':
      return { bg: 'bg-blue-500/8', border: 'border-blue-500/20', textColor: 'text-foreground', prefix: '👁' }
    case 'ACTION':
      return { bg: 'bg-purple-500/8', border: 'border-purple-500/20', textColor: 'text-foreground', prefix: '⚡' }
    case 'ACTION_RESULT':
      return { bg: 'bg-green-500/8', border: 'border-green-500/20', textColor: 'text-green-700 dark:text-green-400', prefix: '' }
    case 'NEEDS_INPUT':
      return { bg: 'bg-orange-500/10', border: 'border-orange-500/40', textColor: 'text-foreground', prefix: '⏸' }
    case 'RESUMED':
      return { bg: 'bg-green-500/8', border: 'border-green-500/20', textColor: 'text-green-700 dark:text-green-400', prefix: '▶' }
    case 'COMPLETED':
      return { bg: 'bg-green-500/10', border: 'border-green-500/40', textColor: 'text-green-700 dark:text-green-400', prefix: '✅' }
    case 'FAILED':
      return { bg: 'bg-red-500/10', border: 'border-red-500/40', textColor: 'text-red-600 dark:text-red-400', prefix: '❌' }
    case 'STEP_DONE':
      return { bg: 'bg-muted/30', border: 'border-border/30', textColor: 'text-muted-foreground', prefix: '✓' }
    default:
      return { bg: 'bg-muted/30', border: 'border-border/40', textColor: 'text-muted-foreground', prefix: 'ℹ' }
  }
}

// ── Single chat bubble ────────────────────────────────────────────────────────

function ChatBubble({ msg }: { msg: ChatMessage }) {
  const { bg, border, textColor, prefix } = bubbleStyle(msg.event.type)
  const time = msg.timestamp.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

  if (msg.event.type === 'STEP_DONE') {
    return (
      <div className="flex items-center gap-2 my-1">
        <div className="flex-1 h-px bg-border/50" />
        <span className="text-[10px] text-muted-foreground/60">{msg.event.message}</span>
        <div className="flex-1 h-px bg-border/50" />
      </div>
    )
  }

  return (
    <div className={`rounded-xl border px-3.5 py-2.5 text-sm ${bg} ${border}`}>
      <div className="flex items-start justify-between gap-2">
        <p className={`leading-relaxed whitespace-pre-wrap flex-1 ${textColor}`}>
          {prefix && <span className="mr-1.5">{prefix}</span>}
          {msg.event.type === 'NEEDS_INPUT' ? (
            <span className="font-medium">{msg.event.question}</span>
          ) : (
            msg.event.message
          )}
        </p>
        <span className="text-[10px] text-muted-foreground/50 shrink-0 mt-0.5 font-mono">{time}</span>
      </div>
      {/* Show selector/value inline for ACTION events */}
      {msg.event.type === 'ACTION' && msg.event.selector && (
        <div className="mt-1 flex flex-wrap gap-1.5">
          <span className="inline-flex items-center rounded-md bg-muted/60 px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
            {msg.event.selector}
          </span>
          {msg.event.fillValue && (
            <span className="inline-flex items-center rounded-md bg-muted/60 px-1.5 py-0.5 text-[10px] text-muted-foreground italic">
              « {msg.event.fillValue} »
            </span>
          )}
        </div>
      )}
      {/* Hint for NEEDS_INPUT */}
      {msg.event.type === 'NEEDS_INPUT' && msg.event.hint && (
        <p className="mt-1 text-[11px] text-muted-foreground italic">
          Exemple : {msg.event.hint}
        </p>
      )}
    </div>
  )
}

// ── Human answer bubble (shown after user sends) ──────────────────────────────

function HumanBubble({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <div className="max-w-[80%] rounded-xl rounded-br-sm border border-primary/30 bg-primary/10 px-3.5 py-2 text-sm text-foreground">
        <span className="mr-1.5">👤</span>
        {text}
      </div>
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

interface EvaluationLiveViewProps {
  messages: ChatMessage[]
  liveScreenshot: string | null
  connected: boolean
  needsInput: { question: string; hint?: string | null } | null
  isRunning: boolean
  isMobile?: boolean
  currentBackend?: string | null
  onSendAnswer: (answer: string) => void
}

export function EvaluationLiveView({
  messages,
  liveScreenshot,
  connected,
  needsInput,
  isRunning,
  isMobile = false,
  currentBackend,
  onSendAnswer,
}: EvaluationLiveViewProps) {
  const [answer, setAnswer] = useState('')
  const [sentAnswers, setSentAnswers] = useState<{ id: string; text: string }[]>([])
  const chatEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  // Auto-scroll chat on new messages
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [messages.length, sentAnswers.length])

  // Focus input when NEEDS_INPUT arrives
  useEffect(() => {
    if (needsInput) {
      setTimeout(() => inputRef.current?.focus(), 100)
    }
  }, [needsInput])

  const handleSend = () => {
    const trimmed = answer.trim()
    if (!trimmed) return
    setSentAnswers(prev => [...prev, { id: String(Date.now()), text: trimmed }])
    onSendAnswer(trimmed)
    setAnswer('')
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  // Interleave messages + human answers (human answers come right after NEEDS_INPUT)
  const allItems: Array<{ kind: 'msg'; msg: ChatMessage } | { kind: 'human'; id: string; text: string }> = []
  let humanIdx = 0
  for (const msg of messages) {
    allItems.push({ kind: 'msg', msg })
    if (msg.event.type === 'NEEDS_INPUT' && humanIdx < sentAnswers.length) {
      allItems.push({ kind: 'human', ...sentAnswers[humanIdx++] })
    }
  }

  return (
    <div className="grid h-full gap-4 xl:grid-cols-[1.1fr_0.9fr]">

      {/* ── Left: Live screenshot ──────────────────────────────────── */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold">Écran en direct</h3>
          <div className={`flex items-center gap-1.5 text-xs ${connected ? 'text-green-500' : 'text-muted-foreground'}`}>
            {connected
              ? <><Wifi className="h-3.5 w-3.5" /><span>Connecté</span></>
              : <><WifiOff className="h-3.5 w-3.5" /><span>Déconnecté</span></>}
          </div>
        </div>

        {isMobile ? (
          /* ── Phone frame for mobile mode ── */
          <div className="flex flex-1 items-start justify-center py-4 min-h-[300px]">
            <div className="relative mx-auto" style={{ width: 280 }}>
              {/* Phone bezel */}
              <div className="rounded-[2.5rem] border-[6px] border-zinc-800 dark:border-zinc-600 bg-black shadow-2xl overflow-hidden">
                {/* Notch */}
                <div className="relative z-10 mx-auto h-6 w-28 rounded-b-2xl bg-black" />
                {/* Screen */}
                <div className="relative bg-white" style={{ height: 560 }}>
                  {liveScreenshot ? (
                    <img
                      src={liveScreenshot}
                      alt="Écran mobile en direct"
                      className="w-full h-full object-cover object-top"
                    />
                  ) : (
                    <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground bg-muted/20">
                      {isRunning ? (
                        <>
                          <Loader2 className="h-6 w-6 animate-spin" />
                          <p className="text-xs">En attente…</p>
                        </>
                      ) : (
                        <p className="text-xs">Aucun screenshot</p>
                      )}
                    </div>
                  )}
                  {isRunning && liveScreenshot && (
                    <div className="absolute top-1 right-1 flex items-center gap-1 rounded-full bg-black/60 px-2 py-0.5">
                      <span className="h-1.5 w-1.5 rounded-full bg-red-500 animate-pulse" />
                      <span className="text-[9px] text-white font-medium">LIVE</span>
                    </div>
                  )}
                </div>
                {/* Home bar */}
                <div className="flex justify-center py-2 bg-black">
                  <div className="h-1 w-24 rounded-full bg-zinc-600" />
                </div>
              </div>
              {/* Device label */}
              <p className="mt-2 text-center text-[10px] text-muted-foreground">iPhone 14 — 390×844</p>
            </div>
          </div>
        ) : (
          /* ── Desktop screenshot ── */
          <div className="relative flex-1 rounded-xl border border-border/60 bg-muted/20 overflow-hidden min-h-[300px]">
            {liveScreenshot ? (
              <img
                src={liveScreenshot}
                alt="Écran en direct"
                className="w-full h-full object-contain object-top"
              />
            ) : (
              <div className="flex h-full min-h-[300px] flex-col items-center justify-center gap-3 text-muted-foreground">
                {isRunning ? (
                  <>
                    <Loader2 className="h-8 w-8 animate-spin" />
                    <p className="text-sm">En attente du premier screenshot…</p>
                  </>
                ) : (
                  <p className="text-sm">Aucun screenshot disponible</p>
                )}
              </div>
            )}
            {isRunning && liveScreenshot && (
              <div className="absolute top-2 right-2 flex items-center gap-1.5 rounded-full bg-black/60 px-2.5 py-1">
                <span className="h-2 w-2 rounded-full bg-red-500 animate-pulse" />
                <span className="text-[10px] text-white font-medium">LIVE</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Right: Chat ───────────────────────────────────────────── */}
      <div className="flex flex-col gap-3 min-h-0">
        <h3 className="text-sm font-semibold shrink-0">
          Raisonnement de l'IA
          {isRunning && <Loader2 className="inline ml-2 h-3.5 w-3.5 animate-spin text-primary" />}
        </h3>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1 min-h-[300px] max-h-[500px]">
          {allItems.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground py-10">
              {isRunning
                ? 'Les messages de l\'IA apparaîtront ici…'
                : 'Aucun message.'}
            </div>
          ) : (
            allItems.map(item =>
              item.kind === 'msg'
                ? <ChatBubble key={item.msg.id} msg={item.msg} />
                : <HumanBubble key={item.id} text={item.text} />
            )
          )}
          <div ref={chatEndRef} />
        </div>

        {/* HITL input — appears only when AI asks a question */}
        {needsInput && (
          <div className="shrink-0 rounded-xl border-2 border-orange-500/50 bg-orange-500/5 p-3 space-y-2">
            <p className="text-xs font-semibold text-orange-600 dark:text-orange-400 uppercase tracking-wide">
              ⏸ L'IA attend votre réponse
            </p>
            <p className="text-sm text-foreground font-medium">{needsInput.question}</p>
            {needsInput.hint && (
              <p className="text-xs text-muted-foreground italic">Exemple : {needsInput.hint}</p>
            )}
            <div className="flex gap-2">
              <Input
                ref={inputRef}
                value={answer}
                onChange={e => setAnswer(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Votre réponse…"
                className="flex-1 border-orange-500/40 focus-visible:ring-orange-500/50"
              />
              <Button
                onClick={handleSend}
                disabled={!answer.trim()}
                size="sm"
                className="shrink-0 bg-orange-500 hover:bg-orange-600 text-white"
              >
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
