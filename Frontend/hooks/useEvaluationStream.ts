'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// ── Types ────────────────────────────────────────────────────────────────────

export type StreamEventType =
  | 'SCREENSHOT'
  | 'THINKING'
  | 'OBSERVATION'
  | 'ACTION'
  | 'ACTION_RESULT'
  | 'NEEDS_INPUT'
  | 'RESUMED'
  | 'STEP_DONE'
  | 'COMPLETED'
  | 'FAILED'
  | 'INFO'
  | 'BACKEND_INFO'
  | 'FORM_DETECTED'
  | 'TEST_CASE'
  | 'TEST_VERDICT'

export interface StreamEvent {
  type: StreamEventType
  message?: string | null
  screenshotBase64?: string | null
  step?: number | null
  actionType?: string | null
  selector?: string | null
  fillValue?: string | null
  /** Vision backend: "🟢 Ollama (local)", "🟡 Gemini (fallback)", etc. */
  backend?: string | null
  question?: string | null
  hint?: string | null
  success?: boolean | null
  currentUrl?: string | null
  pageTitle?: string | null
}

export interface ChatMessage {
  id: string
  event: StreamEvent
  timestamp: Date
}

interface UseEvaluationStreamOptions {
  evaluationId: number | null
  /** Base URL of ms-execution, e.g. http://localhost:8083 */
  baseUrl?: string
  /** Called when a COMPLETED event arrives */
  onCompleted?: () => void
  /** Called when a FAILED event arrives */
  onFailed?: (error: string) => void
}

// ── Hook ─────────────────────────────────────────────────────────────────────

export function useEvaluationStream({
  evaluationId,
  baseUrl = 'http://localhost:8083',
  onCompleted,
  onFailed,
}: UseEvaluationStreamOptions) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [liveScreenshot, setLiveScreenshot] = useState<string | null>(null)
  const [connected, setConnected] = useState(false)
  const [needsInput, setNeedsInput] = useState<{ question: string; hint?: string | null } | null>(null)
  const [isCompleted, setIsCompleted] = useState(false)
  const [currentBackend, setCurrentBackend] = useState<string | null>(null)

  const clientRef = useRef<Client | null>(null)
  const msgIdRef = useRef(0)

  const addMessage = useCallback((event: StreamEvent) => {
    const id = String(++msgIdRef.current)
    setMessages(prev => [...prev, { id, event, timestamp: new Date() }])
  }, [])

  // ── Connect ─────────────────────────────────────────────────────────────

  useEffect(() => {
    if (!evaluationId) return

    const wsUrl = `${baseUrl}/ws`

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl) as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        // Subscribe to evaluation-specific topic
        client.subscribe(`/topic/evaluation/${evaluationId}`, (msg: IMessage) => {
          try {
            const event: StreamEvent = JSON.parse(msg.body)
            handleEvent(event)
          } catch (e) {
            console.warn('[WS] Failed to parse event:', e)
          }
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message'])
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
      setConnected(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [evaluationId, baseUrl])

  // ── Event handler ────────────────────────────────────────────────────────

  function handleEvent(event: StreamEvent) {
    switch (event.type) {
      case 'SCREENSHOT':
        if (event.screenshotBase64) {
          setLiveScreenshot(`data:image/jpeg;base64,${event.screenshotBase64}`)
        }
        // Don't add screenshots to chat — they show in the live panel
        break

      case 'NEEDS_INPUT':
        setNeedsInput({
          question: event.question ?? 'L\'IA a besoin d\'informations',
          hint: event.hint,
        })
        addMessage(event)
        break

      case 'RESUMED':
        setNeedsInput(null)
        addMessage(event)
        break

      case 'COMPLETED':
        setIsCompleted(true)
        setNeedsInput(null)
        addMessage(event)
        onCompleted?.()
        break

      case 'FAILED':
        setNeedsInput(null)
        addMessage(event)
        onFailed?.(event.message ?? 'Erreur inconnue')
        break

      case 'BACKEND_INFO':
        if (event.backend) setCurrentBackend(event.backend)
        // Don't add to chat — it's shown as a persistent badge
        break

      case 'THINKING':
      case 'OBSERVATION':
      case 'ACTION':
      case 'ACTION_RESULT':
      case 'INFO':
      case 'STEP_DONE':
      case 'FORM_DETECTED':
      case 'TEST_CASE':
      case 'TEST_VERDICT':
        addMessage(event)
        break
    }
  }

  // ── Send human answer ────────────────────────────────────────────────────

  const sendAnswer = useCallback(async (answer: string) => {
    if (!evaluationId) return
    try {
      // Use Next.js proxy (same origin) to avoid CORS issues
      const res = await fetch(
        `/api/functional-evaluation/${evaluationId}/respond`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ answer }),
        }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setNeedsInput(null)
    } catch (e) {
      console.error('[HITL] Failed to send answer:', e)
    }
  }, [evaluationId])

  const clearMessages = useCallback(() => {
    setMessages([])
    setLiveScreenshot(null)
    setNeedsInput(null)
    setIsCompleted(false)
    setCurrentBackend(null)
  }, [])

  return {
    messages,
    liveScreenshot,
    connected,
    needsInput,
    isCompleted,
    currentBackend,
    sendAnswer,
    clearMessages,
  }
}
