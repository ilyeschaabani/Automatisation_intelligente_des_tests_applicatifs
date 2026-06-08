package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages human-in-the-loop pauses during agentic UX evaluations.
 *
 * When the AI needs information from the tester (credentials, 2FA code, etc.)
 * the agentic loop calls {@link #waitForInput} which blocks until the tester
 * responds via the REST endpoint, or until the timeout elapses.
 */
@Service
@Slf4j
public class HumanInputService {

    /** evaluationId → pending future waiting for the human's answer */
    private final ConcurrentHashMap<Long, CompletableFuture<String>> pendingInputs =
            new ConcurrentHashMap<>();

    // ── Called by AgenticEvaluationService (agentic thread) ─────────────────

    /**
     * Block the current thread until the human tester provides an answer,
     * or until {@code timeout} elapses.
     *
     * @return the human's answer
     * @throws TimeoutException if the tester does not respond in time
     * @throws InterruptedException if the thread is interrupted
     */
    public String waitForInput(Long evaluationId, Duration timeout)
            throws TimeoutException, InterruptedException {

        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> previous = pendingInputs.put(evaluationId, future);
        if (previous != null) {
            previous.cancel(true); // cancel any stale future
        }

        log.info("[HITL {}] Waiting for human input (timeout={})", evaluationId, timeout);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException("Human input future failed", e.getCause());
        } catch (TimeoutException e) {
            log.warn("[HITL {}] Human input timed out after {}", evaluationId, timeout);
            throw e;
        } finally {
            pendingInputs.remove(evaluationId, future);
        }
    }

    // ── Called by HumanInputController (HTTP thread) ─────────────────────────

    /**
     * Provide the human's answer, unblocking the agentic thread.
     *
     * @return true if there was a pending future and it was completed,
     *         false if the evaluation is not paused (answer ignored)
     */
    public boolean provideInput(Long evaluationId, String answer) {
        CompletableFuture<String> future = pendingInputs.get(evaluationId);
        if (future == null) {
            log.warn("[HITL {}] No pending input future — answer ignored", evaluationId);
            return false;
        }
        boolean completed = future.complete(answer);
        log.info("[HITL {}] Human provided answer (completed={}): {}",
                evaluationId, completed, answer.length() > 80 ? answer.substring(0, 80) + "…" : answer);
        return completed;
    }

    /** True if the evaluation is currently paused waiting for human input. */
    public boolean isPaused(Long evaluationId) {
        return pendingInputs.containsKey(evaluationId);
    }

    /** Cancel a pending input (e.g. when the evaluation is stopped). */
    public void cancel(Long evaluationId) {
        CompletableFuture<String> future = pendingInputs.remove(evaluationId);
        if (future != null) {
            future.cancel(true);
            log.info("[HITL {}] Pending input cancelled", evaluationId);
        }
    }
}
