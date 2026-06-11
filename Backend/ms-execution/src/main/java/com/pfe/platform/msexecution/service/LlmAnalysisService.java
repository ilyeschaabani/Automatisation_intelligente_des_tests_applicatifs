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
    private static final int MAX_SCRIPT_CHARS = 1500;
    private static final int MAX_LOG_CHARS = 1500;
    private static final int MAX_ERROR_CHARS = 400;

    private final RestTemplate restTemplate = new RestTemplate();

    public String analyze(String scriptCode, String logs, String status, String errorMessage) {
        String truncatedScript = truncate(scriptCode, MAX_SCRIPT_CHARS);
        // Extract the relevant error lines instead of blindly taking first N chars
        String truncatedLogs = extractRelevantErrors(logs);
        String truncatedErrorMessage = truncate(errorMessage, MAX_ERROR_CHARS);
        String errorType = detectErrorType(logs, errorMessage);
        String prompt = buildPrompt(truncatedScript, truncatedLogs, status, truncatedErrorMessage, errorType);

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

    public String analyzeUx(String testSummary, String pageContent, String platform) {
        return analyzeFunctionalTest(testSummary, pageContent, platform);
    }

    public String analyzeFunctionalTest(String testSummary, String pageContent, String platform) {
        String normalizedSummary = truncate(testSummary, MAX_LOG_CHARS);
        String normalizedPageContent = truncate(pageContent, MAX_LOG_CHARS);
        String platformLabel = platform == null ? "" : platform.trim();
        String prompt = "Tu es un expert en qualité logicielle et expérience utilisateur avec 15 ans d'expérience.\n"
                + "Tu viens de tester une application " + platformLabel + " et tu dois rédiger ton rapport.\n\n"
                + "Voici les résultats des tests automatisés :\n"
                + normalizedSummary + "\n\n"
                + "Voici le contenu textuel des pages visitées :\n"
                + normalizedPageContent + "\n\n"
                + "Rédige un rapport d'évaluation fonctionnelle complet en français avec :\n"
                + "1. RÉSUMÉ GLOBAL (2-3 phrases sur l'état général de l'application)\n"
                + "2. FONCTIONNALITÉS OK (liste de ce qui marche bien)\n"
                + "3. ANOMALIES DÉTECTÉES (bugs, erreurs, comportements inattendus)\n"
                + "4. CLARTÉ DES MESSAGES (les messages d'erreur/succès sont-ils compréhensibles ?)\n"
                + "5. PERFORMANCE (les temps de réponse sont-ils acceptables ?)\n"
                + "6. RECOMMANDATIONS (suggestions concrètes d'amélioration)\n"
                + "7. SCORE GLOBAL SUR 10\n\n"
                + "IMPORTANT : Écris comme un humain, pas comme une machine. Sois naturel, constructif, et utile.";

        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "prompt", prompt,
                    "stream", false
            );

            Map response = restTemplate.postForObject(OLLAMA_URL, body, Map.class);
            if (response == null || !response.containsKey("response")) {
                log.warn("Ollama did not return a functional analysis response.");
                return "Service d'IA temporairement indisponible";
            }

            Object raw = response.get("response");
            return raw != null ? raw.toString().trim() : "Service d'IA temporairement indisponible";
        } catch (RestClientException ex) {
            log.warn("Ollama functional analysis service is unavailable: {}", ex.getMessage());
            return "Service d'IA temporairement indisponible";
        } catch (Exception ex) {
            log.warn("Unexpected error while analyzing functional summary: {}", ex.getMessage());
            return "Service d'IA temporairement indisponible";
        }
    }

    public String extractRelevantErrors(String logs) {
        if (logs == null || logs.isBlank()) return "";

        StringBuilder relevant = new StringBuilder();
        String[] lines = logs.split("\\r?\\n");

        for (String line : lines) {
            if (line.contains("[ERROR]") || line.contains("COMPILATION ERROR")
                    || line.contains("cannot find symbol") || line.contains("is not applicable")
                    || line.contains("AssertionError") || line.contains("NullPointerException")
                    || line.contains("Exception in thread") || line.contains("BUILD FAILURE")) {
                relevant.append(line).append("\n");
            }
        }

        // Fallback: dernières 60 lignes si peu d'erreurs trouvées
        if (relevant.length() < 100) {
            int start = Math.max(0, lines.length - 60);
            StringBuilder tail = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                tail.append(lines[i]).append("\n");
            }
            return truncate(tail.toString(), MAX_LOG_CHARS);
        }

        return truncate(relevant.toString(), MAX_LOG_CHARS);
    }

    public String detectErrorType(String logs, String errorMessage) {
        if (logs == null) logs = "";
        if (errorMessage == null) errorMessage = "";
        String combined = logs + errorMessage;
        if (combined.contains("COMPILATION ERROR") || combined.contains("cannot find symbol")
                || combined.contains("is not applicable") || combined.contains("testCompile")) {
            return "COMPILATION_ERROR";
        }
        if (combined.contains("AssertionError") || combined.contains("assertEquals")
                || combined.contains("expected") && combined.contains("but was")) {
            return "ASSERTION_FAILURE";
        }
        if (combined.contains("NullPointerException")) return "NULL_POINTER";
        if (combined.contains("timeout") || combined.contains("Timeout")) return "TIMEOUT";
        if (combined.contains("Exception")) return "RUNTIME_EXCEPTION";
        return "UNKNOWN";
    }

    private String buildPrompt(String scriptCode, String logs, String status, String errorMessage, String errorType) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();

        if ("SUCCESS".equals(normalizedStatus)) {
            return "Tu es un expert en automatisation de tests Java (TestNG + Mockito).\n"
                    + "Le test suivant a réussi. Analyse-le brièvement :\n"
                    + "1. Ce qui est bien testé (1-2 lignes)\n"
                    + "2. Une suggestion d'amélioration si pertinent (robustesse, lisibilité) (1-2 lignes)\n\n"
                    + "Script de test :\n" + scriptCode;
        }

        String errorTypeLabel = switch (errorType) {
            case "COMPILATION_ERROR" -> "ERREUR DE COMPILATION — le test ne compile pas";
            case "ASSERTION_FAILURE" -> "ÉCHEC D'ASSERTION — le test a exécuté mais une assertion a échoué";
            case "NULL_POINTER"      -> "NullPointerException — un objet est null au moment de l'appel";
            case "TIMEOUT"           -> "TIMEOUT — le test a dépassé la durée limite";
            case "RUNTIME_EXCEPTION" -> "EXCEPTION RUNTIME — une exception non attendue a été levée";
            default                  -> "ÉCHEC INCONNU";
        };

        return "Tu es un expert en automatisation de tests Java (TestNG + Mockito + Spring).\n"
                + "Type d'échec détecté : " + errorTypeLabel + "\n\n"
                + "RÈGLES DE RÉPONSE :\n"
                + "- Réponds en français, en 3 sections courtes et précises\n"
                + "- Ne répète pas le code d'erreur en entier\n"
                + "- Cite la ligne ou le symbole exact concerné\n\n"
                + "Script de test :\n" + scriptCode + "\n\n"
                + "Erreurs extraites des logs Maven :\n" + logs
                + (errorMessage.isBlank() ? "" : "\n\nMessage d'erreur : " + errorMessage) + "\n\n"
                + "Réponds avec exactement :\n"
                + "**Cause** : (1-2 phrases — quelle classe/méthode manque ou est fausse)\n"
                + "**Correction** : (étapes concrètes pour corriger le script ou le setup)\n"
                + "**Conseil** : (1 phrase — bonne pratique pour éviter ce type d'échec)";
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