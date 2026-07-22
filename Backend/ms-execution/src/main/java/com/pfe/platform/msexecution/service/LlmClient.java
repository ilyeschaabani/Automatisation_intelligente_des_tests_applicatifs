package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class LlmClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ollama.url:http://localhost:11434/api/generate}")
    private String generateUrl;

    @Value("${ollama.model:nemotron-3-ultra:cloud}")
    private String model;

    /**
     * Generate test code for UX evaluation. Platform expected: "WEB" or "MOBILE".
     * This mirrors the prompts used in ms_gestion LlmService.
     */
    public String generateUxTestCode(String platform, String description) {
        return generateFunctionalTestCode(platform, description);
    }

    public String generateFunctionalTestCode(String platform, String description) {
        try {
            Map<String, Object> body = new HashMap<>();
            String prompt = buildPrompt(platform, description);
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);
            body.put("num_predict", 3000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            byte[] resp = restTemplate.postForObject(generateUrl, request, byte[].class);
            if (resp != null && resp.length > 0) {
                String raw = new String(resp, StandardCharsets.UTF_8).trim();
                return extractGeneratedText(raw);
            }
        } catch (Exception ex) {
            log.warn("LLM generation failed", ex);
        }
        return null;
    }

    private String extractGeneratedText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode responseNode = node.get("response");
            if (responseNode != null && !responseNode.isNull()) {
                return responseNode.asText();
            }
            JsonNode textNode = node.get("text");
            if (textNode != null && !textNode.isNull()) {
                return textNode.asText();
            }
        } catch (Exception ignored) {
            // Fall through to raw response below.
        }

        return raw;
    }

    private String buildPrompt(String platform, String description) {
        String platformLabel = "MOBILE".equalsIgnoreCase(platform) ? "mobile" : "web";
        return "Tu es un testeur QA humain expert chargé de tester le fonctionnement complet d'une application " + platformLabel + ".\n\n"
                + "Ton objectif : tester les fonctionnalités, détecter les anomalies, et produire un résumé détaillé.\n\n"
                + "Contexte de test :\n"
                + safeSection("Description", description) + "\n"
                + "Génère un script Java (TestNG + HtmlUnitDriver) qui :\n"
                + "1. Navigue vers l'URL fournie (System.getProperty(\"TEST_URL\"))\n"
                + "2. Teste TOUS les éléments interactifs (champs, boutons, liens, formulaires)\n"
                + "3. Remplit les formulaires avec des données valides ET invalides\n"
                + "4. Vérifie que les messages d'erreur sont affichés et lisibles\n"
                + "5. Vérifie que les redirections fonctionnent après soumission\n"
                + "6. Mesure le temps de chargement de chaque page\n"
                + "7. Capture le contenu textuel des pages visitées\n"
                + "8. Vérifie la présence des liens importants (mot de passe oublié, aide, contact)\n\n"
                + "RÈGLES STRICTES :\n"
                + "- Utilise HtmlUnitDriver (pas ChromeDriver)\n"
                + "- Utilise TestNG (pas JUnit)\n"
                + "- Utilise WebDriverWait pour attendre les éléments\n"
                + "- Chaque findElement doit être dans un try-catch\n"
                + "- Affiche \"TEST_SUMMARY: \" avec le résultat de chaque test (OK/FAIL)\n"
                + "- Affiche \"PAGE_CONTENT: \" avec le texte visible de la page principale\n"
                + "- Le script doit être directement exécutable, sans markdown, et centré sur la vérification fonctionnelle globale.";
    }

    private String safeSection(String label, String value) {
        return label + ": " + (value == null ? "" : value);
    }
}
