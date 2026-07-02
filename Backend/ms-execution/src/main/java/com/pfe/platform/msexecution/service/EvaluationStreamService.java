package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.dto.StreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Broadcasts real-time WebSocket events to the frontend during a UX evaluation.
 * Frontend subscribes to: /topic/evaluation/{id}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluationStreamService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_PREFIX = "/topic/evaluation/";

    // ── Generic send ─────────────────────────────────────────────────────────

    public void send(Long evaluationId, StreamEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + evaluationId, event);
        } catch (Exception e) {
            log.debug("[WS {}] Failed to send {} event: {}", evaluationId, event.getType(), e.getMessage());
        }
    }

    // ── Convenience methods ──────────────────────────────────────────────────

    public void sendScreenshot(Long id, byte[] jpegBytes) {
        send(id, StreamEvent.screenshot(jpegBytes));
    }

    public void sendThinking(Long id, int step, String url, String title) {
        send(id, StreamEvent.thinking(step, url, title));
    }

    public void sendObservation(Long id, int step, String observation, String url, String title) {
        send(id, StreamEvent.observation(step, observation, url, title));
    }

    public void sendAction(Long id, int step, String actionType, String selector, String value, String reason) {
        send(id, StreamEvent.action(step, actionType, selector, value, reason));
    }

    public void sendActionResult(Long id, int step, boolean success, String message) {
        send(id, StreamEvent.actionResult(step, success, message));
    }

    public void sendNeedsInput(Long id, int step, String question, String hint) {
        sendNeedsInput(id, step, question, hint, null);
    }

    public void sendNeedsInput(Long id, int step, String question, String hint, java.util.List<String> fields) {
        send(id, StreamEvent.needsInput(step, question, hint, fields));
        log.info("[WS {}] NEEDS_INPUT sent for step {} ({} field(s))",
                id, step, fields != null ? fields.size() : 0);
    }

    public void sendResumed(Long id, String humanAnswer) {
        send(id, StreamEvent.resumed(humanAnswer));
    }

    public void sendStepDone(Long id, int step, int maxSteps) {
        send(id, StreamEvent.stepDone(step, maxSteps));
    }

    public void sendCompleted(Long id, String report) {
        send(id, StreamEvent.completed(report));
    }

    public void sendFailed(Long id, String error) {
        send(id, StreamEvent.failed(error));
    }

    public void sendInfo(Long id, String message) {
        send(id, StreamEvent.info(message));
    }
}
