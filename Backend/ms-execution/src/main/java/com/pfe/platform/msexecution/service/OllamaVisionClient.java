package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Vision client using a local Ollama instance with a multimodal model (moondream, llava, etc.).
 * No API key needed, no quota limits — runs entirely on local GPU.
 *
 * Ollama API: POST http://localhost:11434/api/generate
 * {
 *   "model": "moondream",
 *   "prompt": "...",
 *   "images": ["base64..."],
 *   "stream": false
 * }
 */
@Service
@Slf4j
public class OllamaVisionClient {

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.vision.model:moondream}")
    private String visionModel;

    // Local models can take 30-90s on a 4GB GPU — generous timeout
    private final RestTemplate restTemplate;

    public OllamaVisionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);    // 10s to connect
        factory.setReadTimeout(180_000);      // 3 min to respond (model loading + inference)
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Send a screenshot + text prompt to a local Ollama vision model.
     *
     * @param screenshotBytes JPEG/PNG bytes
     * @param prompt          text prompt describing what to analyze
     * @return the model's text response, or null on failure
     */
    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("prompt", prompt);
        body.put("images", List.of(base64));
        body.put("stream", false);
        // Options to control generation
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0.4);
        options.put("num_predict", 1500);   // max tokens
        options.put("top_p", 0.9);
        body.put("options", options);

        String url = ollamaUrl + "/api/generate";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.debug("[OLLAMA] Sending vision request to {} (model={}, attempt={})",
                        url, visionModel, attempt);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                // Ollama can take a while on first load — use 3 min timeout
                ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

                if (response.getBody() == null) {
                    log.warn("[OLLAMA] Null response body (attempt {})", attempt);
                    continue;
                }

                String responseText = String.valueOf(response.getBody().get("response"));
                if (responseText == null || "null".equals(responseText) || responseText.isBlank()) {
                    log.warn("[OLLAMA] Empty response text (attempt {})", attempt);
                    continue;
                }

                log.info("[OLLAMA] Got response ({} chars) in model={}", responseText.length(), visionModel);
                return responseText.trim();

            } catch (org.springframework.web.client.ResourceAccessException e) {
                // Connection refused — Ollama is not running
                log.error("[OLLAMA] Cannot connect to {} — is Ollama running? Error: {}",
                        ollamaUrl, e.getMessage());
                return null; // fail fast, don't retry connection issues
            } catch (Exception e) {
                log.error("[OLLAMA] Vision call failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.error("[OLLAMA] Gave up after 3 attempts");
        return null;
    }

    /**
     * Text-only prompt (no image) — used for the final UX analysis report.
     */
    public String analyzeText(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0.6);
        options.put("num_predict", 2000);
        body.put("options", options);

        String url = ollamaUrl + "/api/generate";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() == null) return null;
            String responseText = String.valueOf(response.getBody().get("response"));
            return (responseText == null || "null".equals(responseText)) ? null : responseText.trim();

        } catch (Exception e) {
            log.error("[OLLAMA] Text call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if Ollama is reachable and the vision model is available.
     */
    public boolean isAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    ollamaUrl + "/api/tags", String.class);
            return response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().contains(visionModel);
        } catch (Exception e) {
            return false;
        }
    }
}
