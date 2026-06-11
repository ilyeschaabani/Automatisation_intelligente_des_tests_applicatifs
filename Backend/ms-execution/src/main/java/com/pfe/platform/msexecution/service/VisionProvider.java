package com.pfe.platform.msexecution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VisionProvider {

    private final OpenRouterVisionClient openRouterClient;

    private volatile String lastUsedBackend = "Inconnu";

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

    public String activeBackend() {
        return "OpenRouter (multi-model)";
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
