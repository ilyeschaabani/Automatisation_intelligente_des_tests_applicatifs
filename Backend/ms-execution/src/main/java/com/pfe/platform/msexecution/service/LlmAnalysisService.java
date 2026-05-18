package com.pfe.platform.msexecution.service;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LlmAnalysisService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "deepseek-coder:6.7b";
    private static final int MAX_SCRIPT_CHARS = 2000;
    private static final int MAX_LOG_CHARS = 2000;
    private static final int MAX_ERROR_CHARS = 500;

    private final RestTemplate restTemplate = new RestTemplate();

    public String analyze(String scriptCode, String logs, String status, String errorMessage) {
        String truncatedScript = truncate(scriptCode, MAX_SCRIPT_CHARS);
        String truncatedLogs = truncate(logs);
        String truncatedErrorMessage = truncate(errorMessage, MAX_ERROR_CHARS);
        String prompt = buildPrompt(truncatedScript, truncatedLogs, status, truncatedErrorMessage);

        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "prompt", prompt,
                    "stream", false
            );

            Map response = restTemplate.postForObject(OLLAMA_URL, body, Map.class);
            if (response == null || !response.containsKey("response")) {
                log.warn("Ollama did not return an analysis response.");
                return null;
            }

            Object raw = response.get("response");
            return raw != null ? raw.toString().trim() : null;
        } catch (RestClientException ex) {
            log.warn("Ollama analysis service is unavailable: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Unexpected error while analyzing failure logs: {}", ex.getMessage());
            return null;
        }
    }

    public String analyze(String logs, String status) {
        return analyze("Script non disponible", logs, status, "");
    }

    public String analyzeFailure(String logs) {
        return analyze("Script non disponible", logs, "FAILURE", "");
    }

    private String buildPrompt(String scriptCode, String logs, String status, String errorMessage) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if ("SUCCESS".equals(normalizedStatus)) {
            return "Tu es un expert en automatisation de tests.\n"
                    + "Analyse le script de test et les logs d'exécution ci-dessous.\n"
                    + "Explique :\n"
                    + "1. Ce qui a bien fonctionné (en 1-2 lignes)\n"
                    + "2. Une suggestion d'optimisation si pertinent (temps d'exécution, robustesse) (en 1-2 lignes)\n\n"
                    + "Script de test :\n"
                    + scriptCode + "\n\n"
                    + "Logs d'exécution :\n"
                    + logs;
        }

        return "Tu es un expert en automatisation de tests et en débogage.\n"
                + "Analyse le script de test et les logs d'exécution ci-dessous.\n"
                + "Explique :\n"
                + "1. La cause probable de l'échec (en 2-3 lignes)\n"
                + "2. La correction à apporter au script (en 2-3 lignes)\n"
                + "3. Si pertinent, une suggestion d'amélioration du test (en 1-2 lignes)\n\n"
                + "Script de test :\n"
                + scriptCode + "\n\n"
                + "Logs d'exécution :\n"
                + logs + "\n\n"
                + "Message d'erreur :\n"
                + errorMessage;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private String truncate(String logs) {
        return truncate(logs, MAX_LOG_CHARS);
    }
}