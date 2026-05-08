package com.pfe.platform.ms_gestion.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateTestCode(String type, String description) {
        String prompt = buildPrompt(type, description);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "deepseek-coder:6.7b",
                "prompt", prompt,
                "stream", false
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:11434/api/generate", request, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("response")) {
            String raw = (String) response.getBody().get("response");
            // Supprimer les marqueurs markdown ```java ... ```
            raw = raw.replaceAll("(?i)```java\\s*", "")
                    .replaceAll("```", "")
                    .trim();
            return raw;
        }
        throw new RuntimeException("Ollama n’a pas renvoyé de code.");
    }

    private String buildPrompt(String type, String description) {
        String base = """
            Tu es un assistant spécialisé en automatisation de tests Java avec TestNG.
            Génère **uniquement** le code Java complet (imports, classe, méthode), sans explications.
            Le code doit être directement compilable et utilisable dans un projet Maven.
            
            Type de test : %s
            Description : %s
            """;

        String specifics = switch (type.toUpperCase()) {
            case "UNIT"   -> "C'est un test unitaire. Utilise Mockito si nécessaire.";
            case "INTEGRATION" -> "C'est un test d'intégration. Utilise JDBC et une base H2 en mémoire.";
            case "WEB"    -> "C'est un test web avec HtmlUnitDriver (headless). Utilise Selenium.";
            case "API"    -> "C'est un test d'API REST avec REST Assured.";
            default       -> "Type de test inconnu.";
        };

        return String.format(base, specifics, description);
    }
}