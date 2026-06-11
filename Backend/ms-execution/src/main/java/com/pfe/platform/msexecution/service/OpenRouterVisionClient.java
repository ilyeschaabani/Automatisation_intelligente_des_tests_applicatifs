package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class OpenRouterVisionClient {

    @Value("${openrouter.api.key:}")
    private String apiKey;

    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${openrouter.vision.model:moonshotai/kimi-k2.6:free}")
    private String primaryModel;

    private static final List<String> FALLBACK_MODELS = List.of(
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "moonshotai/kimi-k2.6:free",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
    );

    private static final int MAX_ATTEMPTS_PER_MODEL = 2;
    private static final int RATE_LIMIT_WAIT_SECS = 15;

    private final RestTemplate restTemplate;
    private volatile String lastUsedModel = null;

    public OpenRouterVisionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getLastUsedModel() {
        return lastUsedModel;
    }

    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        if (!isConfigured()) {
            log.debug("[OPENROUTER] Pas de clé API configurée");
            return null;
        }

        List<String> modelsToTry = buildModelList();
        String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

        for (String model : modelsToTry) {
            String result = tryModelWithScreenshot(model, base64, prompt);
            if (result != null) {
                lastUsedModel = model;
                return result;
            }
            log.info("[OPENROUTER] Modèle {} indisponible, basculement…", model);
        }

        log.error("[OPENROUTER] Tous les modèles épuisés");
        return null;
    }

    public String analyzeText(String prompt) {
        if (!isConfigured()) return null;

        List<String> modelsToTry = buildModelList();
        for (String model : modelsToTry) {
            String result = tryModelWithText(model, prompt);
            if (result != null) {
                lastUsedModel = model;
                return result;
            }
        }
        return null;
    }

    private List<String> buildModelList() {
        List<String> models = new ArrayList<>();
        models.add(primaryModel);
        for (String fb : FALLBACK_MODELS) {
            if (!fb.equals(primaryModel)) models.add(fb);
        }
        return models;
    }

    private String tryModelWithScreenshot(String model, String base64, String prompt) {
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
                    log.warn("[OPENROUTER] Réponse vide (model={}, attempt={})", model, attempt);
                    continue;
                }

                if (response.getBody().containsKey("error")) {
                    Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
                    String errorMsg = String.valueOf(error.get("message"));
                    int code = error.get("code") instanceof Integer ? (Integer) error.get("code") : 0;

                    if (code == 429) {
                        if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                            log.warn("[OPENROUTER] 429 on model={}, waiting {}s before retry (attempt {}/{})",
                                    model, RATE_LIMIT_WAIT_SECS, attempt, MAX_ATTEMPTS_PER_MODEL);
                            try { Thread.sleep(RATE_LIMIT_WAIT_SECS * 1000L); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            continue;
                        }
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
                log.error("[OPENROUTER] Erreur réseau: {}", e.getMessage());
                return null;

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
