package com.pfe.platform.ms_gestion.service;

import com.pfe.platform.ms_gestion.dto.request.GenerateTestDataRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TestDataGeneratorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "deepseek-coder:6.7b";

    public String generateTestData(GenerateTestDataRequest request) {
        String prompt = buildPrompt(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "prompt", prompt,
                "stream", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                OLLAMA_URL,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (response.getBody() != null && response.getBody().containsKey("response")) {
            String raw = (String) response.getBody().get("response");
            return extractJson(raw);
        }
        throw new RuntimeException("Ollama n'a pas retourné de test data.");
    }

    private String buildPrompt(GenerateTestDataRequest req) {
        String scenario = req.getScenarioType() != null ? req.getScenarioType().toUpperCase() : "HAPPY_PATH";
        String method   = req.getMethodName() != null ? req.getMethodName() : "la méthode";
        String fields   = buildFieldsDescription(req.getFields());

        String scenarioInstructions = switch (scenario) {
            case "HAPPY_PATH" -> """
                SCÉNARIO : Happy Path (cas nominal)
                - Génère des valeurs VALIDES et RÉALISTES pour chaque champ.
                - Utilise des valeurs représentatives du domaine métier.
                - Pour les enums : choisis une valeur valide du milieu (ni la première ni la dernière).
                - Pour les entiers : utilise une valeur entre min et max (ou 1 si pas de contrainte).
                - Pour les strings : génère une valeur courte mais significative (5-30 chars).
                """;

            case "NULL_INPUT" -> """
                SCÉNARIO : Null Input (entrée nulle)
                - Choisis UN SEUL champ marqué comme "required: true".
                - Mets sa valeur à null dans le JSON.
                - Tous les autres champs doivent avoir des valeurs valides.
                - Objectif : déclencher une NullPointerException ou IllegalArgumentException.
                """;

            case "WRONG_INPUT" -> """
                SCÉNARIO : Wrong Input (entrée invalide)
                - Choisis UN SEUL champ avec des contraintes (enum, min, max, minLength).
                - Pour un champ enum : utilise une valeur HORS de la liste (ex: "INVALID_VALUE").
                - Pour un entier avec minimum : utilise une valeur EN DESSOUS du minimum (ex: -1 si min=1).
                - Pour un string avec minLength : utilise une string TROP COURTE (ex: "" si minLength=1).
                - Tous les autres champs doivent avoir des valeurs valides.
                - Objectif : déclencher une IllegalArgumentException ou erreur de validation.
                """;

            case "BOUNDARY" -> """
                SCÉNARIO : Boundary (valeur limite)
                - Choisis UN SEUL champ avec des contraintes de taille ou valeur (minLength, maxLength, minimum, maximum).
                - Utilise EXACTEMENT la valeur limite maximale (ex: string de exactement maxLength chars).
                - Si pas de maxLength : utilise la valeur minimale exacte.
                - Tous les autres champs doivent avoir des valeurs valides.
                - Objectif : tester le comportement aux limites exactes des contraintes.
                """;

            case "EXCEPTION" -> """
                SCÉNARIO : Exception (erreur métier)
                - Génère des valeurs qui DÉCLENCHERONT une exception métier.
                - Exemples : ID inexistant (99999), référence à un objet qui n'existe pas.
                - Pour les champs d'ID : utilise une valeur très grande (99999) pour simuler un enregistrement inexistant.
                - Pour les autres champs : valeurs valides mais le contexte provoque l'erreur.
                - Objectif : déclencher une RuntimeException ou ResponseStatusException (404/403).
                """;

            default -> """
                SCÉNARIO : Cas nominal
                - Génère des valeurs valides et réalistes.
                """;
        };

        return """
                Tu es un expert en tests logiciels Java.
                Génère un objet JSON de données de test pour la méthode '%s'.

                CHAMPS DU FORMULAIRE (issus du schéma API Swagger) :
                %s

                %s

                RÈGLES ABSOLUES :
                - Réponds UNIQUEMENT avec le JSON brut, sans texte avant ni après.
                - Pas de markdown, pas de backticks, pas d'explication.
                - Le JSON doit être valide et parseable.
                - N'inclus PAS les champs de sortie comme id, suiteId, createdAt, generatedCode.
                - Commence directement par { et termine par }.

                Génère maintenant le JSON de test data :
                """.formatted(method, fields, scenarioInstructions);
    }

    private String buildFieldsDescription(List<GenerateTestDataRequest.FieldSchema> fields) {
        if (fields == null || fields.isEmpty()) return "Aucun champ défini.";

        return fields.stream()
                .map(f -> {
                    StringBuilder desc = new StringBuilder("  - " + f.getName() + " (" + f.getType() + ")");
                    if (f.isRequired()) desc.append(" [REQUIRED]");
                    if (f.getEnumValues() != null && !f.getEnumValues().isEmpty()) {
                        desc.append(" enum:[").append(String.join(", ", f.getEnumValues())).append("]");
                    }
                    if (f.getMinLength() != null) desc.append(" minLength=").append(f.getMinLength());
                    if (f.getMaxLength() != null) desc.append(" maxLength=").append(f.getMaxLength());
                    if (f.getMinimum()   != null) desc.append(" minimum=").append(f.getMinimum());
                    if (f.getMaximum()   != null) desc.append(" maximum=").append(f.getMaximum());
                    return desc.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Extracts the first valid JSON object from the LLM response.
     * Handles cases where the model adds preamble text despite instructions.
     */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        raw = raw.strip();
        // Remove deepseek special tokens that sometimes leak into output
        raw = raw.replaceAll("<｜[^｜]*｜>", "")
                 .replaceAll("\\|begin▁of▁sentence\\|", "")
                 .replaceAll("\\|end▁of▁sentence\\|", "");
        // Remove markdown code blocks if present
        raw = raw.replaceAll("(?i)```json\\s*", "").replaceAll("```", "").strip();
        // Find first { and last }
        int start = raw.indexOf('{');
        int end   = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return "{}";
    }
}
