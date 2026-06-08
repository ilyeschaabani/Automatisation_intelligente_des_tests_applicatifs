package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Vision client using OpenRouter API (OpenAI-compatible).
 * Supports multiple free vision models via a single API key.
 * If the primary model is rate-limited (429), automatically tries fallback models.
 *
 * API: POST https://openrouter.ai/api/v1/chat/completions
 * Auth: Bearer token
 */
@Service
@Slf4j
public class OpenRouterVisionClient {

    @Value("${openrouter.api.key:}")
    private String apiKey;

    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${openrouter.vision.model:moonshotai/kimi-k2.6:free}")
    private String primaryModel;

    /**
     * Fallback models to try if the primary model is rate-limited.
     * Each uses a different upstream provider, so rate limits don't overlap.
     */
    private static final List<String> FALLBACK_MODELS = List.of(
        "google/gemma-4-26b-a4b-it:free",           // Google AI Studio
        "google/gemma-4-31b-it:free",                // Google AI Studio
        "moonshotai/kimi-k2.6:free",                 // Moonshot
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"  // NVIDIA
    );

    private static final int MAX_ATTEMPTS_PER_MODEL = 2;

    /** Seconds to wait after a 429 before trying the next model */
    private static final int RATE_LIMIT_WAIT_SECS = 15;

    private final RestTemplate restTemplate;

    /** Tracks which model was last used successfully (for UI badge) */
    private volatile String lastUsedModel = null;

    public OpenRouterVisionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);   // 15s connect
        factory.setReadTimeout(120_000);     // 2 min read (vision models can be slow)
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Check if OpenRouter is configured (API key present).
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Which model was last used successfully.
     */
    public String getLastUsedModel() {
        return lastUsedModel;
    }

    /**
     * Analyze a screenshot with a text prompt via OpenRouter vision model.
     * Tries the primary model first, then fallback models if rate-limited.
     */
    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        if (!isConfigured()) {
            log.debug("[OPENROUTER] No API key configured — skipping");
            return null;
        }

        // Build the ordered list of models to try (primary first, then fallbacks)
        List<String> modelsToTry = new ArrayList<>();
        modelsToTry.add(primaryModel);
        for (String fb : FALLBACK_MODELS) {
            if (!fb.equals(primaryModel)) modelsToTry.add(fb);
        }

        String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

        for (String model : modelsToTry) {
            String result = tryModelWithScreenshot(model, base64, prompt);
            if (result != null) {
                lastUsedModel = model;
                return result;
            }
            log.info("[OPENROUTER] Model {} failed or rate-limited, trying next…", model);
        }

        log.error("[OPENROUTER] All models exhausted — no response");
        return null;
    }

    /**
     * Text-only analysis (no image) — used for UX report generation.
     */
    public String analyzeText(String prompt) {
        if (!isConfigured()) return null;

        // Build ordered model list
        List<String> modelsToTry = new ArrayList<>();
        modelsToTry.add(primaryModel);
        for (String fb : FALLBACK_MODELS) {
            if (!fb.equals(primaryModel)) modelsToTry.add(fb);
        }

        for (String model : modelsToTry) {
            String result = tryModelWithText(model, prompt);
            if (result != null) {
                lastUsedModel = model;
                return result;
            }
        }
        return null;
    }

    // ── Internal: try a single model with screenshot ──────────────────────

    private String tryModelWithScreenshot(String model, String base64, String prompt) {
        // Build OpenAI-compatible vision request
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textPart, imagePart));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", 0.4);
        body.put("max_tokens", 1500);

        return callApi(model, body);
    }

    private String tryModelWithText(String model, String prompt) {
        Map<String, Object> textMsg = new LinkedHashMap<>();
        textMsg.put("role", "user");
        textMsg.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(textMsg));
        body.put("temperature", 0.6);
        body.put("max_tokens", 2000);

        return callApi(model, body);
    }

    // ── Core API call with retry ──────────────────────────────────────────

    private String callApi(String model, Map<String, Object> body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);
                headers.set("HTTP-Referer", "https://pfe-test-platform.local");
                headers.set("X-Title", "PFE Test Platform - UX Evaluation");

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                log.info("[OPENROUTER] Request → model={}, attempt={}/{}",
                        model, attempt, MAX_ATTEMPTS_PER_MODEL);

                ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

                if (response.getBody() == null) {
                    log.warn("[OPENROUTER] Null body (model={}, attempt={})", model, attempt);
                    continue;
                }

                // Check for embedded error
                if (response.getBody().containsKey("error")) {
                    Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
                    String errorMsg = String.valueOf(error.get("message"));
                    int code = error.get("code") instanceof Integer ? (Integer) error.get("code") : 0;

                    if (code == 429) {
                        if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                            // Retry same model after a wait (rate limits are per-minute)
                            log.warn("[OPENROUTER] 429 on model={}, waiting {}s before retry (attempt {}/{})",
                                    model, RATE_LIMIT_WAIT_SECS, attempt, MAX_ATTEMPTS_PER_MODEL);
                            try { Thread.sleep(RATE_LIMIT_WAIT_SECS * 1000L); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            continue;
                        }
                        // Exhausted retries for this model — switch to next
                        log.warn("[OPENROUTER] 429 on model={} after {} attempts — switching model",
                                model, MAX_ATTEMPTS_PER_MODEL);
                        return null;
                    }
                    if (errorMsg != null && (errorMsg.contains("credit") || errorMsg.contains("quota"))) {
                        log.error("[OPENROUTER] Credits exhausted: {}", errorMsg);
                        return null;
                    }
                    log.error("[OPENROUTER] Error on model={}: {}", model, errorMsg);
                    return null;
                }

                String text = extractText(response.getBody());
                if (text != null && !text.isBlank()) {
                    log.info("[OPENROUTER] ✓ Got {} chars from model={}", text.length(), model);
                    return text;
                }
                log.warn("[OPENROUTER] Empty content from model={} (attempt={})", model, attempt);

            } catch (org.springframework.web.client.HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429) {
                    if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                        log.warn("[OPENROUTER] HTTP 429 on model={}, waiting {}s (attempt {}/{})",
                                model, RATE_LIMIT_WAIT_SECS, attempt, MAX_ATTEMPTS_PER_MODEL);
                        try { Thread.sleep(RATE_LIMIT_WAIT_SECS * 1000L); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                    log.warn("[OPENROUTER] HTTP 429 on model={} after retries — switching", model);
                    return null;
                }
                if (status == 404) {
                    log.warn("[OPENROUTER] Model {} not found (404) — switching", model);
                    return null;
                }
                log.error("[OPENROUTER] HTTP {} on model={}: {}",
                        status, model, e.getResponseBodyAsString());
                return null;

            } catch (org.springframework.web.client.HttpServerErrorException e) {
                if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                    int waitSecs = attempt * 8;
                    log.warn("[OPENROUTER] Server {} on model={}, waiting {}s",
                            e.getStatusCode().value(), model, waitSecs);
                    try { Thread.sleep(waitSecs * 1000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                log.error("[OPENROUTER] Server error on model={} after retries", model);
                return null;

            } catch (org.springframework.web.client.ResourceAccessException e) {
                log.error("[OPENROUTER] Connection failed: {}", e.getMessage());
                return null; // network issue — fail fast, no point trying other models

            } catch (Exception e) {
                log.error("[OPENROUTER] Unexpected error (model={}, attempt={}): {}",
                        model, attempt, e.getMessage());
                if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                    try { Thread.sleep(3000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract text from OpenAI-compatible response format.
     * Response: { choices: [{ message: { content: "..." } }] }
     */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            if (message == null) return null;

            String content = String.valueOf(message.get("content"));
            if ("null".equals(content) || content.isBlank()) return null;

            return content.trim();

        } catch (Exception e) {
            log.warn("[OPENROUTER] Failed to parse response: {}", e.getMessage());
            return null;
        }
    }
}
