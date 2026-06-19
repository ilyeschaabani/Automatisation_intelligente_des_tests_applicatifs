package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Client vision basé sur Ollama Cloud.
 * Utilise l'endpoint natif /api/generate avec le champ "images" (base64) pour
 * les modèles multimodaux cloud (ex. qwen3.5:cloud, gemma4:cloud, minimax-m3:cloud).
 *
 * Avantages vs OpenRouter free :
 *  - Pas de clé API séparée (réutilise le compte Ollama Cloud déjà configuré)
 *  - Pas de rate limit free-tier (429) → exploration UX illimitée possible
 *  - Tourne sur le cloud Ollama, donc indépendant des 4 Go de VRAM locale
 */
@Service
@Slf4j
public class OllamaVisionClient {

    @Value("${ollama.vision.url:http://localhost:11434/api/generate}")
    private String generateUrl;

    @Value("${ollama.vision.model:minimax-m3:cloud}")
    private String visionModel;

    @Value("${ollama.vision.text-model:${ollama.model:qwen3-coder-next:cloud}}")
    private String textModel;

    @Value("${ollama.vision.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private volatile String lastUsedModel = null;

    public OllamaVisionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(180_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return enabled;
    }

    public String getLastUsedModel() {
        return lastUsedModel;
    }

    /** Analyse un screenshot (vision) et renvoie la réponse texte du modèle. */
    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        if (!enabled) {
            log.debug("[OLLAMA-VISION] Désactivé (ollama.vision.enabled=false)");
            return null;
        }
        String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("prompt", prompt);
        body.put("images", List.of(base64));
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.4, "num_predict", 1500));

        String result = call(visionModel, body);
        if (result != null && !result.isBlank()) {
            lastUsedModel = visionModel;
            return result;
        }
        return null;
    }

    /** Analyse de texte pur (rapport final) sans image. */
    public String analyzeText(String prompt) {
        if (!enabled) return null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", textModel);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.6, "num_predict", 2500));

        String result = call(textModel, body);
        if (result != null && !result.isBlank()) {
            lastUsedModel = textModel;
            return result;
        }
        return null;
    }

    private String call(String model, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("[OLLAMA-VISION] Request → model={}", model);
            byte[] resp = restTemplate.postForObject(generateUrl, request, byte[].class);
            if (resp == null || resp.length == 0) {
                log.warn("[OLLAMA-VISION] Réponse vide (model={})", model);
                return null;
            }

            String raw = new String(resp, StandardCharsets.UTF_8).trim();
            String text = extractResponse(raw);
            if (text != null && !text.isBlank()) {
                log.info("[OLLAMA-VISION] ✓ {} chars from model={}", text.length(), model);
                return text;
            }
            log.warn("[OLLAMA-VISION] Contenu vide (model={})", model);
            return null;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                log.error("[OLLAMA-VISION] Modèle '{}' introuvable (404). "
                        + "Vérifie le nom — lance `ollama pull {}` ou ajuste ollama.vision.model", model, model);
            } else {
                log.error("[OLLAMA-VISION] HTTP {} (model={}): {}", status, model, e.getResponseBodyAsString());
            }
            return null;
        } catch (Exception e) {
            log.error("[OLLAMA-VISION] Erreur (model={}): {}", model, e.getMessage());
            return null;
        }
    }

    /** Ollama /api/generate renvoie {"response": "...", "done": true, ...}. */
    private String extractResponse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode responseNode = node.get("response");
            if (responseNode != null && !responseNode.isNull()) {
                return responseNode.asText();
            }
        } catch (Exception e) {
            log.warn("[OLLAMA-VISION] Parsing JSON échoué: {}", e.getMessage());
        }
        return null;
    }
}
