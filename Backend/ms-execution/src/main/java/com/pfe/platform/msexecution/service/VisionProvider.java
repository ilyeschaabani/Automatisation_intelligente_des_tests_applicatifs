package com.pfe.platform.msexecution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Vision provider backed by OpenRouter (multi-model fallback).
 * If the primary model is rate-limited, the client automatically
 * tries fallback models (kimi-k2.6, gemma-4-26b, gemma-4-31b).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VisionProvider {

    private final OpenRouterVisionClient openRouterClient;

    /** Last backend that successfully returned a result (for display in UI) */
    private volatile String lastUsedBackend = "Inconnu";

    /**
     * Analyze a screenshot with a text prompt.
     */
    public String analyzeScreenshot(byte[] screenshotBytes, String prompt) {
        String result = openRouterClient.analyzeScreenshot(screenshotBytes, prompt);
        if (result != null && !result.isBlank()) {
            String model = openRouterClient.getLastUsedModel();
            lastUsedBackend = "🟢 OpenRouter (" + shortModelName(model) + ")";
            return result;
        }
        lastUsedBackend = "🔴 OpenRouter (échec)";
        return null;
    }

    /**
     * Text-only analysis (no image) — used for the final UX report.
     */
    public String analyzeText(String prompt) {
        String result = openRouterClient.analyzeText(prompt);
        if (result != null && !result.isBlank()) {
            String model = openRouterClient.getLastUsedModel();
            lastUsedBackend = "🟢 OpenRouter (" + shortModelName(model) + ")";
            return result;
        }
        lastUsedBackend = "🔴 OpenRouter (échec)";
        return null;
    }

    /**
     * Which backend is configured.
     */
    public String activeBackend() {
        return "OpenRouter (multi-model)";
    }

    /**
     * Which backend was actually used for the last successful call.
     */
    public String getLastUsedBackend() {
        return lastUsedBackend;
    }

    /**
     * Shorten "moonshotai/kimi-k2.6:free" → "kimi-k2.6"
     */
    private String shortModelName(String fullId) {
        if (fullId == null) return "?";
        String s = fullId.replace(":free", "");
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.replaceAll("-it$", "");
        return s;
    }
}
