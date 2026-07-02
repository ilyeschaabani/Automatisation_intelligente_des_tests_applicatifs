package com.pfe.platform.ms_gestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ollama.url:http://localhost:11434/api/generate}")
    private String ollamaUrl;

    @Value("${ollama.model:qwen3-coder-next:cloud}")
    private String model;

    @Value("${ollama.model.fallback:deepseek-coder:6.7b}")
    private String fallbackModel;

    public String generateTestCode(String type, String description) {
        return generateTestCode(type, description, null, null, null, null, null, null, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType) {
        return generateTestCode(type, description, databaseType, null, null, null, null, null, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType, String classSkeleton) {
        return generateTestCode(type, description, databaseType, classSkeleton, null, null, null, null, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType, String classSkeleton, String testData) {
        return generateTestCode(type, description, databaseType, classSkeleton, testData, null, null, null, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType,
                                   String classSkeleton, String testData,
                                   String methodName, String scenarioType, String expectedBehavior) {
        return generateTestCode(type, description, databaseType, classSkeleton, testData,
                                methodName, scenarioType, expectedBehavior, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType,
                                   String classSkeleton, String testData,
                                   String methodName, String scenarioType, String expectedBehavior,
                                   String fileTree) {
        return generateTestCode(type, description, databaseType, classSkeleton, testData,
                                methodName, scenarioType, expectedBehavior, fileTree, null);
    }

    public String generateTestCode(String type, String description, String databaseType,
                                   String classSkeleton, String testData,
                                   String methodName, String scenarioType, String expectedBehavior,
                                   String fileTree, String dependencySources) {
        log.info("[LlmService] === Building prompt ===");
        log.info("[LlmService] type={}, method={}, scenario={}, dbType={}", type, methodName, scenarioType, databaseType);
        log.info("[LlmService] skeleton={} chars, testData={} chars, fileTree={} chars, deps={} chars",
                classSkeleton != null ? classSkeleton.length() : 0,
                testData != null ? testData.length() : 0,
                fileTree != null ? fileTree.length() : 0,
                dependencySources != null ? dependencySources.length() : 0);

        String prompt = buildPrompt(type, description, databaseType, classSkeleton, testData,
                                    methodName, scenarioType, expectedBehavior, fileTree, dependencySources);
        log.info("[LlmService] Prompt built: {} chars", prompt.length());
        log.debug("[LlmService] Full prompt:\n{}", prompt);

        log.info("[LlmService] Calling Ollama primary model={} at url={}", model, ollamaUrl);
        String result = callOllama(model, prompt);
        if (result == null) {
            log.warn("[LlmService] Primary model {} failed, trying fallback {}", model, fallbackModel);
            result = callOllama(fallbackModel, prompt);
        }
        if (result == null) {
            log.error("[LlmService] Both models failed — no code generated");
            throw new RuntimeException("Ollama n'a pas renvoyé de code.");
        }
        log.info("[LlmService] LLM returned {} chars of code", result.length());
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String validated = postValidate(result, normalizedType);
        log.info("[LlmService] postValidate done: {} chars (was {} chars)", validated.length(), result.length());
        return validated;
    }

    private String postValidate(String code, String type) {
        if ("UNIT".equals(type)) {
            code = fixUnitStructure(code);
        }

        StringBuilder imports = new StringBuilder();
        boolean modified = false;

        if ("UNIT".equals(type)) {
            if (!code.contains("import org.mockito.InjectMocks")) {
                imports.append("import org.mockito.InjectMocks;\n");
                modified = true;
            }
            if (!code.contains("import org.mockito.Mock;") && !code.contains("import org.mockito.Mock\n")) {
                imports.append("import org.mockito.Mock;\n");
                modified = true;
            }
            if (!code.contains("import org.mockito.MockitoAnnotations")) {
                imports.append("import org.mockito.MockitoAnnotations;\n");
                modified = true;
            }
            if (code.contains("MockedStatic") && !code.contains("import org.mockito.MockedStatic")) {
                imports.append("import org.mockito.MockedStatic;\n");
                modified = true;
            }
            if (!code.contains("import static org.mockito.Mockito")) {
                imports.append("import static org.mockito.Mockito.*;\n");
                modified = true;
            }
            if (!code.contains("import static org.mockito.ArgumentMatchers")) {
                imports.append("import static org.mockito.ArgumentMatchers.*;\n");
                modified = true;
            }
        }

        if ("INTEGRATION".equals(type)) {
            if (!code.contains("import org.springframework.test.context.testng.AbstractTestNGSpringContextTests")) {
                imports.append("import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.boot.test.context.SpringBootTest")) {
                imports.append("import org.springframework.boot.test.context.SpringBootTest;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.test.context.ActiveProfiles")) {
                imports.append("import org.springframework.test.context.ActiveProfiles;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.beans.factory.annotation.Autowired")) {
                imports.append("import org.springframework.beans.factory.annotation.Autowired;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.transaction.annotation.Transactional")) {
                imports.append("import org.springframework.transaction.annotation.Transactional;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.test.annotation.Rollback")) {
                imports.append("import org.springframework.test.annotation.Rollback;\n");
                modified = true;
            }
            if (!code.contains("import org.springframework.test.context.TestPropertySource")) {
                imports.append("import org.springframework.test.context.TestPropertySource;\n");
                modified = true;
            }
            if (code.contains("SecurityContextHolder") && !code.contains("import org.springframework.security.core.context.SecurityContextHolder")) {
                imports.append("import org.springframework.security.core.context.SecurityContextHolder;\n");
                imports.append("import org.springframework.security.core.context.SecurityContextImpl;\n");
                imports.append("import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;\n");
                imports.append("import java.util.Collections;\n");
                modified = true;
            }
        }

        if (!code.contains("import static org.testng.Assert")) {
            imports.append("import static org.testng.Assert.*;\n");
            modified = true;
        }
        if (!code.contains("import java.util.Optional") && code.contains("Optional.of")) {
            imports.append("import java.util.Optional;\n");
            modified = true;
        }
        if (!code.contains("import java.util.List") && code.contains("List.of")) {
            imports.append("import java.util.List;\n");
            modified = true;
        }
        if (!code.contains("import java.util.ArrayList") && code.contains("new ArrayList")) {
            imports.append("import java.util.ArrayList;\n");
            modified = true;
        }

        if (modified) {
            int pkgEnd = code.indexOf(";");
            if (pkgEnd > 0 && code.substring(0, pkgEnd).contains("package")) {
                code = code.substring(0, pkgEnd + 1) + "\n\n" + imports + code.substring(pkgEnd + 1);
            }
        }

        if ("INTEGRATION".equals(type) && code.contains("AbstractTestNGSpringContextTests")
                && !code.contains("extends AbstractTestNGSpringContextTests")) {
            code = code.replaceFirst("public\\s+class\\s+(\\w+)\\s*\\{",
                    "public class $1 extends AbstractTestNGSpringContextTests {");
        }
        if ("INTEGRATION".equals(type) && !code.contains("AbstractTestNGSpringContextTests")
                && !code.contains("extends ")) {
            code = code.replaceFirst("public\\s+class\\s+(\\w+)\\s*\\{",
                    "public class $1 extends AbstractTestNGSpringContextTests {");
            if (!code.contains("import org.springframework.test.context.testng.AbstractTestNGSpringContextTests")) {
                int pkgEnd2 = code.indexOf(";");
                if (pkgEnd2 > 0) {
                    code = code.substring(0, pkgEnd2 + 1) + "\nimport org.springframework.test.context.testng.AbstractTestNGSpringContextTests;\n" + code.substring(pkgEnd2 + 1);
                }
            }
        }

        if ("INTEGRATION".equals(type)) {
            code = ensureIntegrationClassAnnotations(code);
        }

        return code;
    }

    private String ensureIntegrationClassAnnotations(String code) {
        String[] required = {"@SpringBootTest", "@ActiveProfiles", "@Transactional", "@Rollback"};
        StringBuilder missing = new StringBuilder();
        for (String ann : required) {
            if (!code.contains(ann)) {
                missing.append(ann.equals("@ActiveProfiles") ? "@ActiveProfiles(\"test\")\n"
                        : ann.equals("@Rollback") ? "@Rollback(true)\n"
                        : ann + "\n");
            }
        }
        if (missing.length() > 0) {
            java.util.regex.Matcher classMatcher = java.util.regex.Pattern
                    .compile("(?m)^(\\s*)(public\\s+class\\s+)")
                    .matcher(code);
            if (classMatcher.find()) {
                String indent = classMatcher.group(1);
                String annotationBlock = missing.toString().lines()
                        .map(line -> indent + line)
                        .collect(Collectors.joining("\n")) + "\n";
                code = code.substring(0, classMatcher.start())
                        + annotationBlock
                        + code.substring(classMatcher.start());
                log.info("[postValidate] Injected missing INTEGRATION annotations: {}",
                        missing.toString().replace("\n", ", ").trim());
            }
        }
        return code;
    }

    private String fixUnitStructure(String code) {
        int fixes = 0;

        // Fix 1: Replace @MockedStatic<X> with private MockedStatic<X>
        // @MockedStatic is not a real Mockito annotation
        if (code.contains("@MockedStatic")) {
            code = code.replaceAll("(?m)^\\s*@MockedStatic<([\\w.]+)>\\s+(\\w+)\\s*;",
                    "    private MockedStatic<$1> $2;");
            fixes++;
            log.info("[postValidate] Fixed @MockedStatic → private MockedStatic");
        }

        // Fix 2: Replace "service = new XxxService()" or "service = new XxxService(repo1, repo2...)"
        // and replace with @InjectMocks pattern
        java.util.regex.Matcher newServiceMatcher = java.util.regex.Pattern
                .compile("(?m)^\\s*(\\w+)\\s*=\\s*new\\s+(\\w+Service)\\s*\\([^)]*\\)\\s*;")
                .matcher(code);
        if (newServiceMatcher.find()) {
            String fieldName = newServiceMatcher.group(1);
            String serviceClass = newServiceMatcher.group(2);
            // Remove the "new XxxService(...)" line
            code = code.replaceAll("(?m)^\\s*" + java.util.regex.Pattern.quote(fieldName)
                    + "\\s*=\\s*new\\s+" + serviceClass + "\\s*\\([^)]*\\)\\s*;\\s*\\n?", "");
            // Remove direct field assignments like "service.repo = repo;"
            code = code.replaceAll("(?m)^\\s*" + java.util.regex.Pattern.quote(fieldName)
                    + "\\.\\w+\\s*=\\s*\\w+\\s*;\\s*\\n?", "");
            // Ensure the field declaration has @InjectMocks
            java.util.regex.Pattern fieldDeclPattern = java.util.regex.Pattern
                    .compile("(?m)^(\\s*)(private\\s+)?" + serviceClass + "\\s+" + java.util.regex.Pattern.quote(fieldName) + "\\s*;");
            java.util.regex.Matcher fieldDeclMatcher = fieldDeclPattern.matcher(code);
            if (fieldDeclMatcher.find()) {
                String indent = fieldDeclMatcher.group(1);
                // Check if @InjectMocks is already on the line above
                int lineStart = fieldDeclMatcher.start();
                String before = code.substring(Math.max(0, lineStart - 50), lineStart);
                if (!before.contains("@InjectMocks")) {
                    code = code.substring(0, fieldDeclMatcher.start())
                            + indent + "@InjectMocks\n"
                            + indent + "private " + serviceClass + " " + fieldName + ";"
                            + code.substring(fieldDeclMatcher.end());
                }
            }
            fixes++;
            log.info("[postValidate] Fixed new {}() → @InjectMocks + removed direct field assignments", serviceClass);
        }

        // Fix 3: Remove any remaining direct field assignments to the @InjectMocks service
        // Pattern: serviceName.fieldName = mockField; (outside of test methods)
        // Only remove if they appear in setUp/@BeforeMethod context
        java.util.regex.Matcher injectMocksMatcher = java.util.regex.Pattern
                .compile("@InjectMocks\\s+private\\s+\\w+\\s+(\\w+)\\s*;")
                .matcher(code);
        if (injectMocksMatcher.find()) {
            String serviceName = injectMocksMatcher.group(1);
            String pattern = "(?m)^\\s*" + java.util.regex.Pattern.quote(serviceName) + "\\.\\w+\\s*=\\s*\\w+\\s*;\\s*\\n?";
            if (code.matches("(?s).*" + pattern + ".*")) {
                code = code.replaceAll(pattern, "");
                fixes++;
                log.info("[postValidate] Removed direct field assignments to {}", serviceName);
            }
        }

        if (fixes > 0) {
            log.info("[postValidate] Applied {} structural fixes for UNIT test", fixes);
        }
        return code;
    }

    private String callOllama(String targetModel, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Low temperature → less hallucinated API; large num_ctx → no truncation of the
            // skeleton + dependencies + exemplar prompt (Ollama Cloud supports a big context).
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

            log.info("[LlmService] Sending request to Ollama model={} url={} prompt_length={}", targetModel, ollamaUrl, prompt.length());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(ollamaUrl, request, Map.class);

            log.info("[LlmService] Ollama response status={}", response.getStatusCode());
            if (response.getBody() != null && response.getBody().containsKey("response")) {
                String raw = (String) response.getBody().get("response");
                log.info("[LlmService] Raw response from {}: {} chars", targetModel, raw != null ? raw.length() : 0);
                String cleaned = cleanResponse(raw);
                log.info("[LlmService] Cleaned response: {} chars", cleaned != null ? cleaned.length() : 0);
                return cleaned;
            }
            log.warn("[LlmService] Response body missing 'response' key. Body keys={}",
                    response.getBody() != null ? response.getBody().keySet() : "null");
            return null;
        } catch (Exception e) {
            log.error("[LlmService] Ollama call FAILED with model {}: {}", targetModel, e.getMessage());
            return null;
        }
    }

    private String cleanResponse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.replaceAll("(?i)```java\\s*", "")
                .replaceAll("```", "")
                .trim();
        raw = raw.replaceAll("<｜[^｜]*｜>", "")
                .replaceAll("\\|begin▁of▁sentence\\|", "")
                .replaceAll("\\|end▁of▁sentence\\|", "")
                .trim();
        return raw.isBlank() ? null : raw;
    }

    private String buildPrompt(String type, String description, String databaseType,
                                String classSkeleton, String testData,
                                String methodName, String scenarioType, String expectedBehavior,
                                String fileTree, String dependencySources) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
            Tu es un expert en automatisation de tests Java. Tu génères du code de test compilable.

            AVANT DE CODER, fais mentalement cette analyse (ne l'écris pas, applique-la dans le code) :
            1. Lis la méthode cible ligne par ligne.
            2. Pour chaque appel de méthode (y compris les méthodes privées de la même classe), note :
               - Quel repository/service est appelé ?
               - Quelle méthode ? (findById, save, existsBy, findByXxxAndYyy...)
               - Quel type de retour ? (Optional, boolean, Entity, void, List...)
            3. Pour les méthodes privées : descends dans leur code et répète l'étape 2.
            4. Résultat : tu obtiens la LISTE COMPLÈTE de tous les mocks nécessaires.
               CHACUN de ces appels DOIT avoir un when(...).thenReturn(...) dans la section Given.

            RÈGLES ABSOLUES :
            - Réponds UNIQUEMENT avec du code Java compilable (un seul fichier).
            - Pas de markdown, pas de backticks, pas de texte explicatif.
            - Commence TOUJOURS par la ligne "package".
            - Utilise TestNG (org.testng.annotations.*), JAMAIS JUnit.
            - Assertions : import static org.testng.Assert.*;
            - Chaque méthode de test DOIT avoir @Test.
            """);

        if (fileTree != null && !fileTree.isBlank()) {
            prompt.append("""

                == ARBORESCENCE DES CLASSES JAVA DU PROJET ==
                %s

                RÈGLE CRITIQUE POUR LES IMPORTS :
                - Utilise cette arborescence pour déterminer le package EXACT de chaque classe.
                - Exemple : si tu vois "com.example.repository.UserRepository" dans la liste,
                  alors l'import est : import com.example.repository.UserRepository;
                - N'INVENTE JAMAIS un package. Si une classe n'est pas dans cette liste, ne l'utilise pas.
                """.formatted(fileTree));
        }

        prompt.append("\n== TYPE DE TEST : ").append(normalizedType).append(" ==\n");
        if (description != null && !description.isBlank()) {
            prompt.append("Description fonctionnelle : ").append(description.trim()).append("\n");
        }

        if (classSkeleton != null && !classSkeleton.isBlank()) {
            prompt.append("""

                == CODE SOURCE COMPLET DE LA CLASSE À TESTER ==
                %s

                INSTRUCTIONS D'ANALYSE DU CODE SOURCE :
                - Lis le code COMPLET de chaque méthode, Y COMPRIS les méthodes privées appelées par la méthode cible.
                - TRACE L'EXÉCUTION complète : si add() appelle checkRole() qui appelle SecurityUtils.getCurrentUserId(),
                  tu DOIS gérer SecurityUtils (MockedStatic pour UNIT, SecurityContext pour INTEGRATION).
                - Identifie TOUS les appels à des repositories/services dans TOUTE la chaîne d'appels (méthode cible + méthodes privées).
                  Exemples : save, findById, existsBy, delete, findByProjectIdAndUserId...
                  Pour UNIT : tu DOIS mocker CHACUN de ces appels avec les bons types de retour.
                  Pour INTEGRATION : tu DOIS préparer les données en DB pour que ces appels réussissent.
                - Identifie les vérifications métier (if/throw) : ton test doit satisfaire ces conditions (Happy Path) ou les déclencher (Exception).
                - Identifie les objets créés/modifiés dans la méthode : ton test doit vérifier leurs valeurs.
                - Remplis les champs du request DTO avec les valeurs du testData (request.setName(...), request.setBaseUrlWeb(...), etc.).
                - ANTI-NPE : si le code fait entity.getRelation().getId(), l'entité DOIT avoir cette relation configurée
                  (pour UNIT : dans le mock, pour INTEGRATION : dans le save en DB).
                - N'invente PAS de méthodes, champs ou classes absents de ce code source.
                - Pour les imports : consulte l'ARBORESCENCE ci-dessus pour trouver le bon package.
                """.formatted(classSkeleton));
        }

        if (dependencySources != null && !dependencySources.isBlank()) {
            prompt.append("""

                == CLASSES DÉPENDANTES (DTOs, Entités, Repositories, Utils) ==
                %s

                UTILISATION DES CLASSES DÉPENDANTES :
                - Ces classes sont les DTOs, entités, repositories et utilitaires importés par la classe cible.
                - Utilise les VRAIS setters/getters visibles dans ces classes (pas de méthodes inventées).
                - Pour les DTOs request : appelle CHAQUE setter correspondant aux valeurs du testData.
                - Pour les entités : crée des instances avec TOUS les champs nécessaires.
                  UNIT → pour les objets retournés par les mocks. INTEGRATION → pour les save() en DB dans @BeforeMethod.
                  Configure TOUTES les relations (setProject, setSuite, setEnvironment...) pour éviter les NPE.
                - Pour les repositories : regarde les méthodes déclarées pour savoir EXACTEMENT quoi mocker (UNIT) ou quelles données préparer (INTEGRATION).
                - Pour les enums (comme Role) : utilise les valeurs EXACTES listées (ADMIN, TESTER, etc.).
                """.formatted(dependencySources));
        }

        if (testData != null && !testData.isBlank()) {
            prompt.append("""

                == DONNÉES DE TEST (JSON fourni par le testeur) ==
                %s

                - Utilise EXACTEMENT ces valeurs dans le test (pas de valeurs inventées).
                - Les clés JSON correspondent aux setters de la request (ex: "name" → request.setName("...")).
                """.formatted(testData));
        }

        if (methodName != null && !methodName.isBlank()) {
            String scenarioLabel = resolveScenarioLabel(scenarioType);
            String expectedLine = (expectedBehavior != null && !expectedBehavior.isBlank())
                    ? expectedBehavior.trim()
                    : "le comportement normal de la méthode";

            prompt.append("""

                == CIBLE PRÉCISE DU TEST ==
                - Méthode à tester : %s
                - Scénario : %s
                - Résultat attendu : %s
                - Génère UNE SEULE méthode @Test nommée : test_%s_%s()
                - Ne génère PAS d'autres scénarios.
                """.formatted(methodName, scenarioLabel, expectedLine,
                    methodName, scenarioType != null ? scenarioType.toLowerCase() : "happyPath"));
        }

        prompt.append("\n").append(buildTypeRules(normalizedType, databaseType));

        // Few-shot: inject one fully compilable "gold" exemplar matching the test type + scenario.
        // Ollama Cloud has a large context window, so we can afford a complete reference example.
        String exemplar = TestExemplarLibrary.select(normalizedType, scenarioType);
        if (exemplar != null && !exemplar.isBlank()) {
            prompt.append("""

                == EXEMPLE DE RÉFÉRENCE (imite la STRUCTURE, n'en copie PAS les noms) ==
                Voici un test %s parfaitement structuré et compilable. Imite sa STRUCTURE :
                annotations de classe, @BeforeMethod / @AfterMethod, organisation Given / When / Then,
                gestion des mocks (UNIT) ou des données en base (INTEGRATION), et gestion de SecurityUtils.
                MAIS adapte TOUT au CODE SOURCE réel fourni plus haut : noms de classes, méthodes,
                champs, setters et packages réels.
                ⚠ Les classes de cet exemple (ProductService, CreateProductRequest, Category…) sont
                FICTIVES : ne les importe pas et ne les réutilise pas.

                %s
                """.formatted(normalizedType.isBlank() ? "UNIT" : normalizedType, exemplar));
        }

        prompt.append("\nGénère maintenant le code Java complet.\n");

        return prompt.toString();
    }

    private String buildTypeRules(String type, String databaseType) {
        return switch (type) {
            case "UNIT" -> """
                == RÈGLES UNIT ==
                - Package du test : package suites.unit;
                - Framework : TestNG + Mockito (pas de Spring context).

                == IMPORTS OBLIGATOIRES (copie-les TOUS) ==
                import org.mockito.Mock;
                import org.mockito.InjectMocks;
                import org.mockito.MockedStatic;
                import org.mockito.MockitoAnnotations;
                import org.testng.annotations.BeforeMethod;
                import org.testng.annotations.AfterMethod;
                import org.testng.annotations.Test;
                import static org.mockito.Mockito.*;
                import static org.mockito.ArgumentMatchers.*;
                import static org.testng.Assert.*;
                import java.util.Optional;

                == STRUCTURE OBLIGATOIRE DE LA CLASSE ==
                - Importe la classe source avec son package COMPLET (consulte l'ARBORESCENCE).
                - Crée un champ @Mock pour CHAQUE dépendance (repository, service) injectée dans la classe source.
                - INTERDIT d'utiliser new() pour créer le service testé. Utilise UNIQUEMENT :
                    @InjectMocks
                    private XxxService xxxService;
                  Mockito injecte automatiquement les @Mock dans le service via @InjectMocks.
                  NE FAIS JAMAIS : xxxService = new XxxService(); ou xxxService = new XxxService(repo1, repo2);
                - Déclare "private AutoCloseable mocks;" comme champ.

                == SecurityUtils (MockedStatic) ==
                - Cherche SecurityUtils.getCurrentUserId() dans TOUTE la classe (y compris les méthodes privées).
                - Si elle est appelée N'IMPORTE OÙ :
                    → Déclare le champ SANS @Mock : private MockedStatic<SecurityUtils> mockedSecurity;
                      INTERDIT : @Mock private MockedStatic<SecurityUtils> — ça ne compile pas.
                      MockedStatic n'est PAS un mock classique, c'est un wrapper pour les méthodes statiques.
                    → Dans @BeforeMethod : mockedSecurity = mockStatic(SecurityUtils.class);
                                           mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                    → Dans @AfterMethod : mockedSecurity.close(); (AVANT mocks.close())

                == @BeforeMethod / @AfterMethod ==
                @BeforeMethod :
                    mocks = MockitoAnnotations.openMocks(this);
                    + mockedSecurity = mockStatic(...) si nécessaire (voir ci-dessus)
                @AfterMethod (throws Exception) :
                    mockedSecurity.close(); // si SecurityUtils utilisé, AVANT mocks.close()
                    mocks.close();

                == CONFIGURATION DES MOCKS (section Given) ==
                - TRACE TOUTES LES MÉTHODES PRIVÉES appelées par la méthode cible. Lis leur code et identifie
                  CHAQUE appel repository/service. Exemples courants à ne PAS oublier :
                    → checkMembership() appelle existsByProjectIdAndUserId → mock avec thenReturn(true)
                    → getProjectOrThrow() appelle projectRepository.findById → mock avec Optional.of(project)
                  Tu DOIS identifier et mocker TOUTES ces méthodes, pas seulement les plus évidentes.
                - Pour save() : thenReturn doit retourner une entité avec un ID (jamais null).
                - Pour findById() : thenReturn(Optional.of(entité)) ou Optional.empty() selon le scénario.
                - Pour existsBy...() : TOUJOURS mocker explicitement avec thenReturn(true) ou thenReturn(false).
                  Happy Path → false (pas de doublon). Exception → true (doublon détecté).
                  NE JAMAIS compter sur le retour par défaut de Mockito.

                == BOUCLES ET LISTES ==
                - Si le code boucle sur une liste (ex: for (Long id : request.getTestCaseIds())) et appelle
                  repository.findById(id) à chaque itération, tu DOIS mocker findById pour CHAQUE ID de la liste.
                  Exemple : si testCaseIds = [10L, 11L], tu dois :
                    TestCase tc1 = new TestCase(); tc1.setId(10L); ...
                    TestCase tc2 = new TestCase(); tc2.setId(11L); ...
                    when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc1));
                    when(testCaseRepository.findById(11L)).thenReturn(Optional.of(tc2));
                - Si le code fait save() dans une boucle (ex: campaignTestCaseRepository.save(ctc)),
                  mocke save() avec any() : when(repo.save(any(...))).thenReturn(new Entity());
                  NE PAS utiliser saveAll() si le code appelle save() individuellement.

                == ANTI-NPE (très important) ==
                - Quand un mock retourne un objet (ex: environment), et que le code appelle getProject().getId() dessus,
                  tu DOIS configurer toute la chaîne : environment.setProject(project); project.setId(1L);
                - Fais ça pour CHAQUE niveau d'imbrication : tc.getSuite().getProject().getId() nécessite
                  tc.setSuite(suite); suite.setProject(project); project.setId(projectId);
                - Pour les enums : si le code fait Enum.valueOf(request.getXxx().toUpperCase()), utilise une valeur EXACTE de l'enum.
                  Exemple : TriggerMode.valueOf("MANUAL") → request.setTriggerMode("MANUAL").
                  Regarde les valeurs de l'enum dans les CLASSES DÉPENDANTES ci-dessus.
                - Pour DTO request : lis CHAQUE ligne du code source qui appelle request.getXxx() et configure le setter correspondant.
                  Si tu oublies un getter, le test aura un NullPointerException. Vérifie TOUS les getters un par un.

                == TYPES (enums vs String) ==
                - Si un setter d'entité attend un enum (ex: campaign.setTriggerMode(Campaign.TriggerMode.MANUAL)),
                  utilise l'enum, PAS un String. Regarde le type du champ dans les CLASSES DÉPENDANTES.
                - Si un setter de DTO request attend un String (ex: request.setTriggerMode("MANUAL")), utilise un String.

                == VERIFY (section Then) ==
                - INTERDIT d'utiliser verifyNoMoreInteractions() — ça casse quand un mock a des appels internes.
                - Ne verify QUE les méthodes qui sont RÉELLEMENT appelées dans le chemin d'exécution testé.
                - TRACE le code : si add() appelle checkMembership() qui appelle existsByProjectIdAndUserId(),
                  verify existsByProjectIdAndUserId() (car c'est RÉELLEMENT appelé).
                - En cas de doute, ne verify PAS — les assertions sur le résultat suffisent.

                == STRUCTURE DU TEST ==
                Given (prépare objets + configure mocks) / When (appelle la méthode) / Then (assertions + verify).
                """;

            case "INTEGRATION" -> """
                == RÈGLES INTEGRATION ==
                - Package du test : package suites.integration;
                - Framework : TestNG + Spring Boot (PAS de Mockito pour les repositories — ils sont de vrais beans Spring).
                - La classe de test DOIT étendre AbstractTestNGSpringContextTests.
                - Imports obligatoires :
                    import org.testng.annotations.*;
                    import static org.testng.Assert.*;
                    import org.springframework.beans.factory.annotation.Autowired;
                    import org.springframework.boot.test.context.SpringBootTest;
                    import org.springframework.test.context.ActiveProfiles;
                    import org.springframework.test.context.TestPropertySource;
                    import org.springframework.test.annotation.Rollback;
                    import org.springframework.transaction.annotation.Transactional;
                    import org.testng.annotations.Test;
                - Importe la classe source, ses DTOs, ses entités avec leur package COMPLET (consulte l'ARBORESCENCE).

                == ANNOTATIONS DE LA CLASSE DE TEST ==
                - @SpringBootTest(classes = XxxApplication.class) — OBLIGATOIRE. Le test est dans le package
                  suites.integration, HORS de l'arborescence de l'application, donc un @SpringBootTest seul
                  échoue avec "Unable to find a @SpringBootConfiguration". Repère la classe annotée
                  @SpringBootApplication dans l'ARBORESCENCE (elle se termine par "Application"), importe-la
                  avec son package complet, et passe-la dans classes = ...class.
                - @ActiveProfiles("test")
                - @Transactional (rollback automatique après chaque test)
                - @Rollback(true)
                - N'ajoute PAS @TestPropertySource : la base de test est fournie automatiquement par le
                  runner (fichier application-test.properties chargé via @ActiveProfiles("test")).

                == INJECTION ==
                - @Autowired pour le service testé (ex: @Autowired private EnvironmentService environmentService;)
                - @Autowired pour CHAQUE repository nécessaire pour préparer les données de test.
                - Ne mocke PAS les repositories. Les vrais repositories Spring accèdent à la vraie DB (H2/Testcontainers).

                == PRÉPARATION DES DONNÉES (@BeforeMethod) ==
                - Trace l'exécution de la méthode cible (Y COMPRIS les méthodes privées) pour identifier TOUTES les entités pré-requises.
                - Exemple : si create() appelle getProjectOrThrow(projectId), tu DOIS insérer un Project en DB avant le test.
                - Exemple : si checkMembership() appelle existsByProjectIdAndUserId(), tu DOIS insérer un ProjectMember en DB.
                - Utilise les repositories @Autowired pour faire les save() dans @BeforeMethod.
                - Stocke les IDs générés dans des champs de la classe de test (private Long projectId, private Long envId...).
                - ANTI-NPE : si le code fait env.getProject().getId(), l'entité Environment en DB DOIT avoir un Project associé.
                  Configure TOUTES les relations (setProject, setSuite, etc.) AVANT le save().
                - Pour les DTO request : remplis TOUS les champs que le code source appelle via getters.
                  Lis chaque request.getXxx() dans le code et assure-toi que setXxx() est appelé dans le test.

                == SÉCURITÉ (SecurityUtils) ==
                - IMPORTANT : cherche SecurityUtils.getCurrentUserId() dans TOUTE la classe (y compris les méthodes privées).
                - Si elle est appelée N'IMPORTE OÙ :
                    → import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                    → import org.springframework.security.core.context.SecurityContextHolder;
                    → import org.springframework.security.core.context.SecurityContextImpl;
                    → import java.util.Collections;
                    → Dans @BeforeMethod :
                      Long userId = 1L;
                      SecurityContextHolder.setContext(new SecurityContextImpl(
                          new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())));
                    → Dans @AfterMethod : SecurityContextHolder.clearContext();
                - L'userId utilisé dans SecurityContext DOIT correspondre au userId du ProjectMember inséré en DB.

                == ASSERTIONS ET VÉRIFICATIONS ==
                - Vérifie le résultat retourné (assertNotNull, assertEquals sur chaque champ important).
                - Vérifie l'état de la DB après l'appel si pertinent (repository.findById, repository.count, etc.).
                - Pour les scénarios d'exception : utilise @Test(expectedExceptions = RuntimeException.class).

                == STRUCTURE DU TEST ==
                - @BeforeMethod : insertions en DB + SecurityContext
                - @Test : crée le request DTO, appelle la méthode, vérifie le résultat
                - @AfterMethod : SecurityContextHolder.clearContext()
                - Pas besoin de cleanup des données (rollback automatique grâce à @Transactional).

                %s
                """.formatted(buildDbConfig(databaseType));

            case "API" -> """
                == RÈGLES API (tests REST) ==
                - Package du test : package suites.api;
                - Framework : TestNG + REST Assured.
                - URL de base : System.getProperty("BASE_URL", "http://localhost:8082")
                - Requêtes en JSON (contentType JSON).
                - Vérifie : status code, headers pertinents, champs du body.
                - Assertions avec Hamcrest (org.hamcrest.Matchers.*).
                """;

            case "UX_WEB" -> """
                == RÈGLES UX_WEB ==
                - Package du test : package suites.ux;
                - Framework : TestNG + HtmlUnitDriver (PAS de ChromeDriver).
                - URL cible : System.getProperty("UX_URL")
                - Mesure les temps de chargement des pages.
                - Vérifie la présence des éléments clés (boutons, champs, messages).
                - Évalue la clarté des messages d'erreur et de succès.
                - Construit une variable String uxSummary avec le format :
                  UX_SUMMARY: TempsChargement=...; ElementsPrésents=...; Messages=...; Clarté=...; Screenshots=...
                """;

            case "UX_MOBILE" -> """
                == RÈGLES UX_MOBILE ==
                - Package du test : package suites.ux;
                - Framework : TestNG + AppiumDriver (PAS de Selenium pur).
                - Package app : System.getProperty("UX_PACKAGE")
                - Activité : System.getProperty("UX_ACTIVITY")
                - Vérifie la taille des zones tactiles (minimum 48dp).
                - Construit une variable String uxSummary avec le format :
                  UX_SUMMARY: TempsChargement=...; ElementsPrésents=...; Messages=...; Clarté=...; Screenshots=...
                """;

            default -> """
                == RÈGLES PAR DÉFAUT ==
                - Génère un test TestNG minimal, compilable, cohérent avec la description.
                """;
        };
    }

    private String buildDbConfig(String databaseType) {
        String db = databaseType == null ? "" : databaseType.trim().toUpperCase();
        return switch (db) {
            case "POSTGRESQL" -> """
                - Base de données de test : Testcontainers PostgreSQL
                - Ajoute @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:postgresql:14:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa", "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                  })
                - N'utilise PAS @Testcontainers ni @Container (la connexion se fait via l'URL tc:).
                """;
            case "MYSQL" -> """
                - Base de données de test : Testcontainers MySQL
                - Ajoute @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:mysql:8.0.33:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa", "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                  })
                """;
            case "MONGODB" -> """
                - Base de données de test : Testcontainers MongoDB
                - Démarre MongoDBContainer dans @BeforeSuite(alwaysRun = true)
                - Injecte l'URI via System.setProperty("spring.data.mongodb.uri", mongo.getReplicaSetUrl())
                - N'utilise PAS @Testcontainers, @Container, @DynamicPropertySource.
                """;
            default -> """
                - Base de données de test : H2 en mémoire
                - Ajoute @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                    "spring.datasource.driverClassName=org.h2.Driver",
                    "spring.datasource.username=sa", "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
                  })
                """;
        };
    }

    private String resolveScenarioLabel(String scenarioType) {
        if (scenarioType == null) return "Cas nominal (Happy Path)";
        return switch (scenarioType.toUpperCase()) {
            case "HAPPY_PATH"  -> "Cas nominal — la méthode s'exécute sans erreur et retourne un résultat valide";
            case "EXCEPTION"   -> "Exception — la méthode doit lever une exception dans ce contexte";
            case "NULL_INPUT"  -> "Entrée nulle — un paramètre obligatoire est null, une exception est attendue";
            case "WRONG_INPUT" -> "Mauvaise entrée — valeur invalide (hors enum, format incorrect, etc.)";
            case "BOUNDARY"    -> "Valeur limite — tester aux valeurs minimales ou maximales autorisées";
            default            -> scenarioType;
        };
    }
}
