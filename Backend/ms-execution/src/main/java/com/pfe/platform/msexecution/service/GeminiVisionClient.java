package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class GeminiVisionClient {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Maximum total attempts for a single Gemini call */
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Send a screenshot + text prompt to Gemini Vision and get a text response.
     * Retries up to {@value MAX_ATTEMPTS} times with exponential backoff for
     * both 429 (rate limit) and 503 (service unavailable / high demand).
     */
    public String analyzeScreenshot(byte[] screenshotPng, String prompt) {
        String base64 = Base64.getEncoder().encodeToString(screenshotPng);

        Map<String, Object> imagePart = Map.of(
            "inline_data", Map.of(
                "mime_type", "image/jpeg",
                "data", base64
            )
        );
        Map<String, Object> textPart = Map.of("text", prompt);

        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(imagePart, textPart))
            ),
            "generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", 1500
            )
        );

        String url = apiUrl + "?key=" + apiKey;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

                if (response.getBody() == null) {
                    log.warn("Gemini returned null body (attempt {})", attempt);
                    sleepBeforeRetry(attempt, 5);
                    continue;
                }

                // Check for error embedded in 200 response (e.g. 429 wrapped as 200)
                if (response.getBody().containsKey("error")) {
                    Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
                    int code = error.get("code") instanceof Integer ? (Integer) error.get("code") : 0;
                    String msg = String.valueOf(error.get("message"));
                    String status = String.valueOf(error.get("status"));

                    // RESOURCE_EXHAUSTED = daily quota gone — no point retrying
                    if ("RESOURCE_EXHAUSTED".equals(status) && msg.contains("FreeTier")) {
                        log.error("Gemini daily free-tier quota exhausted. Switch model or enable billing. Error: {}", msg);
                        return null; // fail fast — retrying won't help until tomorrow
                    }

                    if ((code == 429 || code == 503) && attempt < MAX_ATTEMPTS) {
                        // Try to use retryDelay from Gemini's response body
                        int waitSecs = extractRetryDelay(error);
                        if (waitSecs <= 0) waitSecs = retryWaitSeconds(attempt, code);
                        log.warn("Gemini {} — waiting {}s (attempt {}/{})", code, waitSecs, attempt, MAX_ATTEMPTS);
                        sleepBeforeRetry(attempt, waitSecs);
                        continue;
                    }
                    log.error("Gemini error (non-retriable): {}", msg);
                    return null;
                }

                return extractText(response.getBody());

            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 4xx errors
                String errorBody = e.getResponseBodyAsString();
                // Detect daily quota exhaustion — no point retrying
                if (errorBody.contains("RESOURCE_EXHAUSTED") && errorBody.contains("FreeTier")) {
                    log.error("Gemini daily free-tier quota exhausted — failing fast. Consider gemini-1.5-flash or billing.");
                    return null;
                }
                if (e.getStatusCode().value() == 429 && attempt < MAX_ATTEMPTS) {
                    int waitSecs = retryWaitSeconds(attempt, 429);
                    log.warn("Gemini 429 rate limit, waiting {}s (attempt {}/{})", waitSecs, attempt, MAX_ATTEMPTS);
                    sleepBeforeRetry(attempt, waitSecs);
                    continue;
                }
                log.error("Gemini 4xx error: {}", e.getMessage());
                return null;

            } catch (org.springframework.web.client.HttpServerErrorException e) {
                // 5xx errors — 503 "high demand" is the most common
                if (attempt < MAX_ATTEMPTS) {
                    int waitSecs = retryWaitSeconds(attempt, e.getStatusCode().value());
                    log.warn("Gemini {} server error, waiting {}s (attempt {}/{})",
                            e.getStatusCode().value(), waitSecs, attempt, MAX_ATTEMPTS);
                    sleepBeforeRetry(attempt, waitSecs);
                    continue;
                }
                log.error("Gemini server error after {} attempts: {}", MAX_ATTEMPTS, e.getMessage());
                return null;

            } catch (Exception e) {
                log.error("Gemini API call failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt, 5);
                }
            }
        }
        log.error("Gemini gave up after {} attempts", MAX_ATTEMPTS);
        return null;
    }

    /**
     * Exponential backoff per HTTP code.
     */
    private int retryWaitSeconds(int attempt, int httpCode) {
        return switch (httpCode) {
            case 429 -> attempt * 12;   // 12s, 24s, 36s, 48s
            case 503 -> attempt * 20;   // 20s, 40s, 60s, 80s
            default  -> attempt * 5;    // 5s, 10s, 15s, 20s
        };
    }

    /**
     * Try to extract the retryDelay advised by Gemini in the error details array.
     * Returns 0 if not found.
     * Format: details[].retryDelay = "35s" or "35.432936115s"
     */
    @SuppressWarnings("unchecked")
    private int extractRetryDelay(Map<?, ?> errorMap) {
        try {
            List<?> details = (List<?>) errorMap.get("details");
            if (details == null) return 0;
            for (Object detail : details) {
                if (detail instanceof Map<?, ?> d) {
                    Object delay = d.get("retryDelay");
                    if (delay != null) {
                        String raw = delay.toString().replaceAll("[^0-9.]", "");
                        double secs = Double.parseDouble(raw);
                        return (int) Math.ceil(secs) + 2; // add 2s buffer
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void sleepBeforeRetry(int attempt, int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Text-only prompt (no image) — used for the final UX analysis report.
     */
    public String analyzeText(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);

        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(textPart))
            ),
            "generationConfig", Map.of(
                "temperature", 0.6,
                "maxOutputTokens", 2000
            )
        );

        String url = apiUrl + "?key=" + apiKey;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() == null) return null;
            return extractText(response.getBody());
        } catch (Exception e) {
            log.error("Gemini text API call failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            Map<String, Object> first = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) first.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) return null;

            // Gemini 2.5 "thinking" models return thought parts first, then the actual text.
            // We need the LAST text part that is NOT a thought.
            String lastText = null;
            for (Map<String, Object> part : parts) {
                // Skip thought parts (they have a "thought" key set to true)
                if (Boolean.TRUE.equals(part.get("thought"))) continue;
                Object text = part.get("text");
                if (text != null) lastText = text.toString();
            }
            return lastText;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }
}
