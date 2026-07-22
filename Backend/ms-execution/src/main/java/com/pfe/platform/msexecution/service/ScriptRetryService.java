package com.pfe.platform.msexecution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class ScriptRetryService {

    @Value("${ollama.url:http://localhost:11434/api/generate}")
    private String ollamaUrl;

    @Value("${ollama.model:nemotron-3-ultra:cloud}")
    private String model;

    @Value("${ollama.model.fallback:deepseek-coder:6.7b}")
    private String fallbackModel;

    private final RestTemplate restTemplate = new RestTemplate();

    public String correctScript(String originalScript, String compilationErrors, String sourceClass) {
        return correctScript(originalScript, compilationErrors, sourceClass, null, null);
    }

    public String correctScript(String originalScript, String compilationErrors,
                                String sourceClass, String fileTree) {
        return correctScript(originalScript, compilationErrors, sourceClass, null, fileTree);
    }

    public String correctScript(String originalScript, String compilationErrors,
                                String sourceClass, String relatedClasses, String fileTree) {
        String prompt = buildCorrectionPrompt(originalScript, compilationErrors, sourceClass, relatedClasses, fileTree);

        String result = callOllama(model, prompt);
        if (result == null) {
            log.warn("Primary model {} failed for script correction, trying fallback {}", model, fallbackModel);
            result = callOllama(fallbackModel, prompt);
        }
        return result;
    }

    private String buildCorrectionPrompt(String script, String errors, String sourceClass,
                                         String relatedClasses, String fileTree) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            Tu es un expert en correction de tests Java (TestNG + Mockito).
            Un test généré par IA a échoué. Tu dois le corriger pour qu'il compile ET passe.

            RÈGLES :
            - Réponds UNIQUEMENT avec le code Java corrigé complet (un seul fichier).
            - Pas de markdown, pas de backticks, pas de texte explicatif.
            - Commence par la ligne "package".
            - Corrige TOUTES les erreurs listées ci-dessous.
            - Ajoute les imports manquants.
            - Corrige les types incorrects (String vs enum, etc.).
            - Si un mock manque, ajoute-le.

            CORRECTIONS SPÉCIFIQUES PAR TYPE D'ERREUR :
            - "Wanted but not invoked" → le verify() appelle une méthode jamais exécutée.
              SOLUTION : supprime le verify() inutile ou remplace-le par la méthode réellement appelée.
            - "cannot find symbol" → import ou type manquant. Ajoute l'import correct.
            - "NullPointerException" → un objet retourné par un mock est null.
              SOLUTION : ajoute le when(...).thenReturn(...) manquant.
            - "AssertionError" / "expected [...] but found [...]" → la valeur retournée ne correspond pas.
              SOLUTION : ajuste le mock pour retourner la bonne valeur, ou ajuste l'assertion.
            - "Unnecessary stubbings" → un when() est déclaré mais jamais utilisé.
              SOLUTION : supprime le when() inutile.
            - INTERDIT d'utiliser verifyNoMoreInteractions().
            - INTERDIT d'utiliser new() pour créer le service sous test. Utilise @InjectMocks.
            - MockedStatic ne prend PAS l'annotation @Mock. Déclare : private MockedStatic<X> x;

            == SCRIPT DE TEST ORIGINAL ==
            %s

            == ERREURS MAVEN ==
            %s
            """.formatted(script, errors));

        if (fileTree != null && !fileTree.isBlank()) {
            prompt.append("""

            == ARBORESCENCE DES CLASSES JAVA DU PROJET ==
            %s

            RÈGLE POUR LES IMPORTS :
            - Pour corriger un "cannot find symbol" ou "package does not exist", utilise cette
              arborescence pour trouver le package EXACT de chaque classe.
            - N'INVENTE JAMAIS un package. Si une classe n'est pas dans cette liste, ne l'utilise pas.
            """.formatted(fileTree));
        }

        if (sourceClass != null && !sourceClass.isBlank()) {
            prompt.append("""

            == CODE SOURCE DE LA CLASSE TESTÉE ==
            %s

            Utilise ce code pour :
            - Vérifier les vrais types des champs (enum vs String)
            - Identifier les méthodes réellement appelées (y compris les méthodes privées)
            - Trouver les bons noms de setters/getters
            - Identifier TOUS les appels repository/service à mocker (UNIT) ou à préparer en DB (INTEGRATION)
            """.formatted(sourceClass));
        }

        if (relatedClasses != null && !relatedClasses.isBlank()) {
            prompt.append("""

            == CODE SOURCE DES CLASSES CITÉES DANS LES ERREURS ==
            %s

            Ces classes apparaissent dans les erreurs de compilation ci-dessus. Utilise leur VRAI code pour :
            - Construire correctement leurs instances (regarde le constructeur RÉEL ou le constructeur par défaut + setters).
            - Gérer les clés composites : si une entité a un @EmbeddedId / @IdClass (ex: une classe XxxId),
              NE passe PAS l'id composite là où un Long est attendu. Utilise les setters réels des champs.
            - Respecter les types exacts des champs et des paramètres.
            """.formatted(relatedClasses));
        }

        prompt.append("\nGénère maintenant le code Java corrigé complet.\n");
        return prompt.toString();
    }

    private String callOllama(String targetModel, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> options = Map.of(
                    "temperature", 0.15,
                    "top_p", 0.9,
                    "num_ctx", 32768
            );
            Map<String, Object> body = Map.of(
                    "model", targetModel,
                    "prompt", prompt,
                    "stream", false,
                    "options", options
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            @SuppressWarnings("rawtypes")
            Map response = restTemplate.postForEntity(ollamaUrl, request, Map.class).getBody();

            if (response != null && response.containsKey("response")) {
                String raw = (String) response.get("response");
                return cleanResponse(raw);
            }
            return null;
        } catch (Exception e) {
            log.error("Ollama correction call failed with model {}: {}", targetModel, e.getMessage());
            return null;
        }
    }

    private String cleanResponse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.replaceAll("(?i)```java\\s*", "")
                .replaceAll("```", "")
                .replaceAll("<｜[^｜]*｜>", "")
                .trim();
        return raw.isBlank() ? null : raw;
    }
}
