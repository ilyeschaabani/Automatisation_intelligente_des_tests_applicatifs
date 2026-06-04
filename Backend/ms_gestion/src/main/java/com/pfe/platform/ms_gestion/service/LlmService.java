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
        return generateTestCode(type, description, null, null);
    }

    public String generateTestCode(String type, String description, String databaseType) {
        return generateTestCode(type, description, databaseType, null);
    }

    public String generateTestCode(String type, String description, String databaseType, String classSkeleton) {
        return generateTestCode(type, description, databaseType, classSkeleton, null);
    }

    public String generateTestCode(String type, String description, String databaseType, String classSkeleton, String testData) {
        return generateTestCode(type, description, databaseType, classSkeleton, testData, null, null, null);
    }

    /**
     * Full structured generation: method + scenario + expectedBehavior give the LLM
     * enough context to produce a precise, targeted test instead of a generic one.
     */
    public String generateTestCode(String type, String description, String databaseType,
                                   String classSkeleton, String testData,
                                   String methodName, String scenarioType, String expectedBehavior) {
        String prompt = buildPrompt(type, description, databaseType, classSkeleton, testData,
                                    methodName, scenarioType, expectedBehavior);

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
            // Supprimer les tokens spéciaux deepseek-coder qui fuient parfois dans la sortie
            raw = raw.replaceAll("<｜[^｜]*｜>", "")
                    .replaceAll("\\|begin▁of▁sentence\\|", "")
                    .replaceAll("\\|end▁of▁sentence\\|", "")
                    .trim();
            return raw;
        }
        throw new RuntimeException("Ollama n’a pas renvoyé de code.");
    }

    private String buildPrompt(String type, String description, String databaseType, String classSkeleton, String testData) {
        return buildPrompt(type, description, databaseType, classSkeleton, testData, null, null, null);
    }

    private String buildPrompt(String type, String description, String databaseType,
                                String classSkeleton, String testData,
                                String methodName, String scenarioType, String expectedBehavior) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String normalizedDescription = description == null ? "" : description.trim();
        String normalizedDatabaseType = databaseType == null ? "" : databaseType.trim().toUpperCase();

        String commonRules = """
            Tu es un assistant spécialisé en automatisation de tests Java.

            RÈGLES ABSOLUES (tu dois les respecter) :
            - Réponds UNIQUEMENT avec du code Java compilable (un seul fichier) : pas de texte, pas de titre.
            - Ne mets PAS de markdown, PAS de backticks, PAS de blocs ```.
            - Commence TOUJOURS par la déclaration 'package'.
            - Utilise TestNG (org.testng.annotations.*) : pas de JUnit.
            - Le code doit contenir : package, imports complets, une classe publique, et des méthodes @Test.
            - Utilise des assertions TestNG (org.testng.Assert.*).
            - N'ajoute JAMAIS de commentaires ou d'explications en dehors du code Java.
            """;

        String header = """

            CONTEXTE :
            - Type de test : %s
            - Description fonctionnelle : %s
            """.formatted(normalizedType, normalizedDescription);

        String mongoIntegrationRules = "";
        if ("INTEGRATION".equals(normalizedType) && "MONGODB".equals(normalizedDatabaseType)) {
            mongoIntegrationRules = """

                CONSIGNES SUPPLÉMENTAIRES (INTEGRATION + MONGODB) :
                - La classe doit utiliser TestNG, étendre AbstractTestNGSpringContextTests, et être annotée @SpringBootTest et @ActiveProfiles("test").
                - Le MongoDBContainer doit être démarré dans une méthode @BeforeSuite(alwaysRun = true).
                - L'URI MongoDB doit être injectée via System.setProperty("spring.data.mongodb.uri", ...) avant le chargement du contexte Spring.
                - Ne pas utiliser d'annotations JUnit.
                - N'utilise PAS d'annotations Testcontainers/JUnit comme @Testcontainers, @Container, @DynamicPropertySource.
                - Le code final doit être directement compilable, autonome, et inclure tous les imports nécessaires.

                EXEMPLE DE STRUCTURE À SUIVRE (guide, adapte les noms métier) :
                // import org.springframework.beans.factory.annotation.Autowired;
                // import org.springframework.boot.test.context.SpringBootTest;
                // import org.springframework.test.context.ActiveProfiles;
                // import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
                // import org.testng.Assert;
                // import org.testng.annotations.BeforeSuite;
                // import org.testng.annotations.Test;
                // import org.testcontainers.containers.MongoDBContainer;
                //
                // @SpringBootTest
                // @ActiveProfiles("test")
                // public class UserRepositoryIntegrationTest extends AbstractTestNGSpringContextTests {
                //
                //     private static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");
                //
                //     @BeforeSuite(alwaysRun = true)
                //     public void beforeSuite() {
                //         if (!mongo.isRunning()) {
                //             mongo.start();
                //         }
                //         System.setProperty("spring.data.mongodb.uri", mongo.getReplicaSetUrl());
                //     }
                //
                //     @Autowired
                //     private UserRepository userRepository;
                //
                //     @Test
                //     public void shouldSaveUser() {
                //         UserEntity saved = userRepository.save(new UserEntity(null, "alice@example.com"));
                //         Assert.assertNotNull(saved.getId());
                //     }
                // }
                """;
        }

        String skeletonBlock = "";
        if (classSkeleton != null && !classSkeleton.isBlank()
                && ("UNIT".equals(normalizedType) || "INTEGRATION".equals(normalizedType))) {
            skeletonBlock = """

                CLASSE SOURCE À TESTER (squelette extrait automatiquement du repo) :
                ```java
                %s
                ```
                - Importe et utilise EXACTEMENT cette classe dans ton test (même package, même nom de méthodes).
                - N'invente pas de méthodes qui n'existent pas dans ce squelette.
                """.formatted(classSkeleton);
        }

        String testDataBlock = "";
        if (testData != null && !testData.isBlank()) {
            testDataBlock = """

                DONNÉES DE TEST À UTILISER DANS LE CODE (JSON fourni par le testeur) :
                ```json
                %s
                ```
                - Utilise ces valeurs comme inputs dans tes assertions et appels de méthodes.
                - Ex: si tu vois {"username":"admin","password":"123"}, utilise exactement ces valeurs dans le test.
                - Ne génère pas de données aléatoires si des données sont fournies ici.
                """.formatted(testData);
        }

        // ── Structured scenario block (highest priority — overrides vague description) ──
        String scenarioBlock = "";
        if (methodName != null && !methodName.isBlank()) {
            String scenarioLabel = resolveScenarioLabel(scenarioType);
            String expectedLine = (expectedBehavior != null && !expectedBehavior.isBlank())
                    ? expectedBehavior.trim()
                    : "le comportement normal de la méthode";

            scenarioBlock = """

                CIBLE DU TEST (instructions prioritaires — respecter exactement) :
                - Méthode à tester    : %s
                - Scénario            : %s
                - Résultat attendu    : %s
                - Génère UNE SEULE méthode @Test qui couvre CE scénario précis.
                - Nomme la méthode de test : test_%s_%s()
                - Ne génère PAS d'autres scénarios.
                """.formatted(
                    methodName,
                    scenarioLabel,
                    expectedLine,
                    methodName,
                    (scenarioType != null ? scenarioType.toLowerCase() : "happyPath")
            );
        }

        String integrationDbRule = buildIntegrationDbRule(normalizedDatabaseType);

        String specifics = switch (normalizedType) {
            case "UNIT" -> """
                CONSIGNES UNIT (test unitaire pur) :

                RÈGLE ABSOLUE SUR LE PACKAGE :
                - Le test sera écrit dans src/test/java/suites/unit/ du projet source cloné.
                - DONC le package OBLIGATOIRE est : package suites.unit;
                - N'utilise JAMAIS le package du projet source comme package du test.

                RÈGLE ABSOLUE SUR LES IMPORTS :
                - Le test est compilé DANS le projet source — tu as accès à toutes ses classes.
                - Déduis les imports full-qualified depuis le package du squelette fourni.
                  Exemple : si le squelette montre "package com.pfe.platform.ms_gestion.service;"
                  alors importe : import com.pfe.platform.ms_gestion.service.TestCaseService;
                  et aussi : import com.pfe.platform.ms_gestion.repository.TestCaseRepository; etc.
                - Importe chaque classe utilisée avec son chemin complet.

                RÈGLES MOCKITO + TESTNG :
                - N'utilise PAS Spring (pas de @SpringBootTest, pas d'@Autowired).
                - Mocke TOUTES les dépendances avec @Mock (repositories, services, SecurityUtils).
                - Utilise @InjectMocks pour la classe sous test — JAMAIS de new() explicite.
                  CORRECT   : @InjectMocks private TestSuiteService testSuiteService;
                  INCORRECT : @InjectMocks private TestSuiteService testSuiteService = new TestSuiteService();
                - Si la méthode testée appelle SecurityUtils.getCurrentUserId(), ajoute OBLIGATOIREMENT :
                    private MockedStatic<SecurityUtils> mockedSecurity;
                    @BeforeMethod public void setUp() {
                        mocks = MockitoAnnotations.openMocks(this);
                        mockedSecurity = Mockito.mockStatic(SecurityUtils.class);
                        mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                    }
                    @AfterMethod public void tearDown() throws Exception {
                        mockedSecurity.close(); mocks.close();
                    }
                - OBLIGATOIRE : génère TOUJOURS cette structure setUp/tearDown si SecurityUtils est utilisé.
                - Mock TOUS les repositories utilisés par la méthode (findById, save, existsBy...).
                - Si la méthode cherche un projet : mock projectRepository.findById(projectId).thenReturn(Optional.of(project)).
                - Si la méthode vérifie les droits : mock projectMemberRepository.findByProjectIdAndUserId(...).thenReturn(Optional.of(member)).
                - UTILISE les valeurs du testData dans le test : si testData contient name='X', crée request.setName("X").
                - Chaque test vérifie un résultat (assertEquals/assertNotNull) ET les interactions (verify).
                - Pour les méthodes retournant Optional, mocke avec Optional.of(...) ou Optional.empty().
                """;

            case "INTEGRATION" -> """
                CONSIGNES INTEGRATION (test d'intégration Spring) :

                RÈGLE ABSOLUE SUR LE PACKAGE :
                - Le test sera écrit dans src/test/java/suites/integration/ du projet source cloné.
                - DONC le package OBLIGATOIRE est : package suites.integration;
                - N'utilise JAMAIS le package du projet source comme package du test.

                RÈGLE ABSOLUE SUR LES IMPORTS :
                - Le test est compilé DANS le projet source — toutes ses classes sont disponibles.
                - Déduis les imports depuis le package du squelette fourni.
                  Exemple : si le squelette montre "package com.pfe.platform.ms_gestion.repository;"
                  alors importe : import com.pfe.platform.ms_gestion.repository.TestCaseRepository;
                - Importe chaque classe utilisée avec son chemin complet.

                IMPORTS OBLIGATOIRES (ajoute-les tous) :
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.test.context.ActiveProfiles;
                import org.springframework.test.context.TestPropertySource;
                import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.transaction.annotation.Transactional;

                RÈGLES SPRING TEST :
                - La classe doit étendre AbstractTestNGSpringContextTests.
                - Annote la classe avec @SpringBootTest et @ActiveProfiles("test").
                - Ne mocke PAS les repositories : injecte-les avec @Autowired.
                """ + integrationDbRule + """
                - Annote la classe avec @Transactional pour rollback automatique après chaque test.
                - Assertions complètes (assertNotNull, assertEquals, etc.).
                """;

            case "WEB" -> """
                                CONSIGNES WEB (E2E Web) :
                                Tu es un expert en automatisation de tests web avec Selenium et TestNG.

                                RÈGLES STRICTES (tu dois les respecter) :
                                - Utilise ChromeDriver en mode headless avec WebDriverManager.
                                - Lis l'URL de base avec System.getProperty("BASE_URL").
                                - Inclus @BeforeMethod (créer le driver) et @AfterMethod (driver.quit()).
                                - Utilise des sélecteurs By.id, By.name, By.cssSelector.
                                - Inclus TOUS les imports nécessaires.
                                - Encadre les assertions dans un try/catch.
                                    En cas d'échec (AssertionError), appelle takeScreenshot(driver, "NomDuTest") puis relance l'erreur.
                                - Implémente une méthode takeScreenshot(WebDriver driver, String testName) qui :
                                    - Vérifie (driver instanceof TakesScreenshot)
                                    - Récupère les bytes (OutputType.BYTES)
                                    - Sauvegarde un PNG dans un dossier relatif "screenshots" (à créer si absent)
                                    - Retourne le chemin absolu du fichier
                                - Réponds UNIQUEMENT avec le code Java (un seul fichier), sans aucune explication.
                """;

            case "API" -> """
                CONSIGNES API (tests REST) :
                - Utilise REST Assured (io.rest-assured.*).
                - Lis l'URL de base depuis System.getProperty("BASE_URL").
                - Envoie des requêtes JSON (contentType JSON) et vérifie : status code, headers, body.
                - Utilise des assertions Hamcrest (org.hamcrest.Matchers.*) avec RestAssured (then().body(...)).
                - Le test doit être robuste (valide au moins un champ du JSON et/ou un header pertinent).
                """;

            case "UX_WEB" -> """
                Tu es un expert senior en automatisation UX web. Tu dois générer UN SEUL fichier Java compilable, en français, sans explication.

                CONTRAINTES ABSOLUES :
                - Utilise EXCLUSIVEMENT HtmlUnitDriver. Interdiction de ChromeDriver, FirefoxDriver, EdgeDriver ou tout autre driver.
                - Utilise EXCLUSIVEMENT TestNG. Interdiction de JUnit.
                - Utilise WebDriverWait pour toutes les attentes. Interdiction de Thread.sleep().
                - Utilise uniquement les imports nécessaires et commençant directement par package ou import.
                - N'écris aucun backtick markdown, aucun bloc ``` et aucun texte hors code.
                - Le code doit être immédiatement compilable.

                EXIGENCES FONCTIONNELLES :
                - Lire l'URL cible depuis System.getProperty("UX_URL").
                - Naviguer sur l'application et mesurer précisément le temps de chargement des pages et des composants visibles.
                - Vérifier la présence des éléments clés : boutons, champs, liens, messages, libellés et indicateurs d'état.
                - Évaluer la clarté des messages d'erreur, de validation et de succès.
                - Capturer une capture d'écran avec TakesScreenshot après chaque étape importante.
                - Sauvegarder les captures dans le répertoire temporaire de System.getProperty("java.io.tmpdir").
                - Construire une variable String uxSummary contenant des métriques détaillées et lisibles.

                FORMAT EXACT DE LA SORTIE UX_SUMMARY :
                UX_SUMMARY: TempsChargement=...; ElementsPrésents=...; Messages=...; Clarté=...; Screenshots=...

                RÈGLES DE QUALITÉ :
                - Le test doit être structuré avec @BeforeMethod et @AfterMethod.
                - Le test doit gérer les erreurs avec des assertions TestNG.
                - Le test doit rester simple, directif, et sans logique inutile.
                - Le premier caractère utile du fichier doit être package ou import.
                """;

            case "UX_MOBILE" -> """
                Tu es un expert senior en automatisation UX mobile. Tu dois générer UN SEUL fichier Java compilable, en français, sans explication.

                CONTRAINTES ABSOLUES :
                - Utilise EXCLUSIVEMENT AppiumDriver. Interdiction de ChromeDriver, FirefoxDriver, Selenium pur sans Appium, ou tout autre driver.
                - Utilise EXCLUSIVEMENT TestNG. Interdiction de JUnit.
                - Utilise WebDriverWait pour les attentes. Interdiction de Thread.sleep().
                - Utilise uniquement les imports nécessaires et commençant directement par package ou import.
                - N'écris aucun backtick markdown, aucun bloc ``` et aucun texte hors code.
                - Le code doit être immédiatement compilable.

                EXIGENCES FONCTIONNELLES :
                - Lire le package via System.getProperty("UX_PACKAGE").
                - Lire l'activité via System.getProperty("UX_ACTIVITY").
                - Se connecter à l'application cible et mesurer précisément le temps de chargement des écrans.
                - Vérifier la présence des éléments clés : boutons, champs, icônes, zones tactiles et messages.
                - Vérifier la clarté des messages d'erreur, d'information et de succès.
                - Vérifier que les zones tactiles ont une taille minimale de 48dp quand c'est applicable.
                - Capturer une capture d'écran avec TakesScreenshot après chaque étape importante.
                - Sauvegarder les captures dans le répertoire temporaire de System.getProperty("java.io.tmpdir").
                - Construire une variable String uxSummary contenant des métriques détaillées et lisibles.

                FORMAT EXACT DE LA SORTIE UX_SUMMARY :
                UX_SUMMARY: TempsChargement=...; ElementsPrésents=...; Messages=...; Clarté=...; Screenshots=...

                RÈGLES DE QUALITÉ :
                - Le test doit être structuré avec @BeforeMethod et @AfterMethod.
                - Le test doit gérer les erreurs avec des assertions TestNG.
                - Le test doit rester simple, directif, et sans logique inutile.
                - Le premier caractère utile du fichier doit être package ou import.
                """;

            default -> """
                CONSIGNES PAR DÉFAUT :
                - Génère un test TestNG minimal, compilable, cohérent avec la description.
                """;
        };

        return commonRules + mongoIntegrationRules + header + skeletonBlock + testDataBlock + scenarioBlock + "\n" + specifics + "\n" + "Génère maintenant le code Java.";
    }

    private String buildIntegrationDbRule(String dbType) {
        return switch (dbType == null ? "" : dbType.trim().toUpperCase()) {
            case "POSTGRESQL" -> """
                - Configure Testcontainers PostgreSQL avec @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:postgresql:14:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                  })
                - N'ajoute PAS @Testcontainers ou @Container : la connexion se fait automatiquement via l'URL tc:.
                """;
            case "MYSQL" -> """
                - Configure Testcontainers MySQL avec @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:mysql:8.0.33:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                  })
                - N'ajoute PAS @Testcontainers ou @Container : la connexion se fait automatiquement via l'URL tc:.
                """;
            case "MONGODB" -> "";
            default -> """
                - Configure H2 avec @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                    "spring.datasource.driverClassName=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
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