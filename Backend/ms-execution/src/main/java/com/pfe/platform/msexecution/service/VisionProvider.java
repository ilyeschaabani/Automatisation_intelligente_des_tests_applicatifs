package com.pfe.platform.msexecution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Façade vision : Ollama Cloud en priorité, OpenRouter en repli.
 *
 * Ollama Cloud (qwen3.5:cloud / gemma4:cloud …) évite les rate limits des
 * modèles OpenRouter ":free" et permet une exploration UX sans plafond.
 * OpenRouter reste un filet de sécurité si une clé est configurée.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VisionProvider {

    private final OllamaVisionClient ollamaClient;
    private final OpenRouterVisionClient openRouterClient;

    private volatile String lastUsedBackend = "Inconnu";

    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        // 1) Ollama Cloud (primaire) — 2 tentatives car le cloud peut hicup
        if (ollamaClient.isConfigured()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                String result = ollamaClient.analyzeScreenshot(screenshotBytes, prompt);
                if (result != null && !result.isBlank()) {
                    lastUsedBackend = "🟢 Ollama Cloud (" + shortModelName(ollamaClient.getLastUsedModel()) + ")";
                    return result;
                }
                if (attempt < 2) {
                    log.warn("[VISION] Ollama vide (tentative {}/2), retry dans 3s…", attempt);
                    try { Thread.sleep(3_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            log.warn("[VISION] Ollama indisponible après 2 tentatives, repli sur OpenRouter…");
        }

        // 2) OpenRouter (repli)
        if (openRouterClient.isConfigured()) {
            String result = openRouterClient.analyzeScreenshot(screenshotBytes, prompt);
            if (result != null && !result.isBlank()) {
                lastUsedBackend = "🟡 OpenRouter (" + shortModelName(openRouterClient.getLastUsedModel()) + ")";
                return result;
            }
            log.warn("[VISION] OpenRouter epuise — derniere tentative sur Ollama Cloud…");
        }

        // 3) Dernier recours : retry Ollama Cloud (peut s'etre debloque entre temps)
        if (ollamaClient.isConfigured()) {
            try { Thread.sleep(5_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            String result = ollamaClient.analyzeScreenshot(screenshotBytes, prompt);
            if (result != null && !result.isBlank()) {
                lastUsedBackend = "🟢 Ollama Cloud (" + shortModelName(ollamaClient.getLastUsedModel()) + ") [final retry]";
                return result;
            }
        }

        lastUsedBackend = "🔴 Aucun moteur disponible";
        return null;
    }

    public String analyzeText(String prompt) {
        if (ollamaClient.isConfigured()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                String result = ollamaClient.analyzeText(prompt);
                if (result != null && !result.isBlank()) {
                    lastUsedBackend = "🟢 Ollama Cloud (" + shortModelName(ollamaClient.getLastUsedModel()) + ")";
                    return result;
                }
                if (attempt < 2) {
                    try { Thread.sleep(3_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        if (openRouterClient.isConfigured()) {
            String result = openRouterClient.analyzeText(prompt);
            if (result != null && !result.isBlank()) {
                lastUsedBackend = "🟡 OpenRouter (" + shortModelName(openRouterClient.getLastUsedModel()) + ")";
                return result;
            }
        }
        // Dernier recours : Ollama Cloud avec pause plus longue
        if (ollamaClient.isConfigured()) {
            try { Thread.sleep(5_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            String result = ollamaClient.analyzeText(prompt);
            if (result != null && !result.isBlank()) {
                lastUsedBackend = "🟢 Ollama Cloud (" + shortModelName(ollamaClient.getLastUsedModel()) + ") [final retry]";
                return result;
            }
        }
        lastUsedBackend = "🔴 Aucun moteur disponible";
        return null;
    }

    public String activeBackend() {
        if (ollamaClient.isConfigured()) return "Ollama Cloud (vision)";
        if (openRouterClient.isConfigured()) return "OpenRouter (multi-model)";
        return "Aucun";
    }

    public String getLastUsedBackend() {
        return lastUsedBackend;
    }

    private String shortModelName(String fullId) {
        if (fullId == null) return "?";
        String s = fullId.replace(":free", "");
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.replaceAll("-it$", "");
        return s;
    }
}
