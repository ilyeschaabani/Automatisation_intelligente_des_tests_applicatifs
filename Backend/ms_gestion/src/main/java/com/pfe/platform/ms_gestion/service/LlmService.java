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
            - CHAQUE méthode de test DOIT avoir l'annotation @Test (org.testng.annotations.Test). Sans @Test, TestNG ne l'exécute pas.
            - Les assertions TestNG s'utilisent avec un import STATIQUE : import static org.testng.Assert.*;
              CORRECT   : import static org.testng.Assert.*;  puis  assertNotNull(x);
              INCORRECT : import org.testng.Assert.*;          (non-static — ne compile pas)
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

                CLASSE SOURCE À TESTER :
                ```java
                %s
                ```
                ⚠ Le package affiché ci-dessus EST LE PACKAGE SOURCE — NE PAS le copier dans le test.
                  Le package du test est TOUJOURS soit "package suites.unit;" soit "package suites.integration;"
                - Importe cette classe avec son chemin complet, utilise ses méthodes telles quelles.
                - N'invente pas de méthodes absentes du squelette.
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
                Génère un test UNIT en complétant ce squelette. Remplace les {placeholders} uniquement.

                SQUELETTE (respecte-le exactement — le package est TOUJOURS suites.unit) :
                package suites.unit;

                import {basePackage}.service.{TestedClass};
                import {basePackage}.repository.*;
                import {basePackage}.entity.*;
                import {basePackage}.dto.request.*;
                import {basePackage}.dto.response.*;
                import org.mockito.*;
                import org.testng.annotations.*;
                import static org.testng.Assert.*;
                import static org.mockito.Mockito.*;
                import java.util.Optional;

                public class {TestedClass}Test {

                    private AutoCloseable mocks;  // TOUJOURS présent

                    @Mock private {Repo1} {repo1Field};  // un @Mock par dépendance du service
                    @InjectMocks private {TestedClass} sut;  // PAS de = new {TestedClass}()

                    @BeforeMethod
                    public void setUp() throws Exception {
                        mocks = MockitoAnnotations.openMocks(this);  // stocker la référence
                    }

                    @AfterMethod
                    public void tearDown() throws Exception {
                        mocks.close();  // TOUJOURS fermer — ne pas laisser vide
                    }

                    @Test
                    public void test_{methodName}_{scenario}() {
                        // given — configure les mocks (jamais thenReturn(null) pour save())
                        // when  — appelle sut.{methodName}(...)
                        // then  — assertNotNull(result) ET verify(repo).save(any())
                    }
                }

                RÈGLES pour remplir les placeholders :
                - @Mock : un par champ final visible dans le squelette source (repositories, services)
                - Pour save() : thenReturn(entité avec setId(1L) et les champs du testData) — jamais null
                - Pour findById() : thenReturn(Optional.of(entité)) ou Optional.empty() selon le scénario
                - Si la méthode utilise SecurityUtils.getCurrentUserId() : ajouter AVANT @BeforeMethod :
                    private MockedStatic<SecurityUtils> mockedSecurity;
                    import {basePackage}.security.SecurityUtils;
                  Et dans setUp() : mockedSecurity = mockStatic(SecurityUtils.class);
                                    mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                  Et dans tearDown() : mockedSecurity.close();  (avant mocks.close())
                """;

            case "INTEGRATION" -> ("""
                Génère un test INTEGRATION en complétant ce squelette. Respecte-le à la lettre.

                SQUELETTE (structure fixe — NE PAS modifier les annotations ni les imports) :
                package suites.integration;

                import {basePackage}.entity.*;
                import {basePackage}.repository.*;
                import {basePackage}.service.*;
                import {basePackage}.dto.request.*;
                import {basePackage}.dto.response.*;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.core.context.SecurityContextHolder;
                import org.springframework.security.core.context.SecurityContextImpl;
                import org.springframework.test.context.ActiveProfiles;
                import org.springframework.test.context.TestPropertySource;
                import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.Collections;
                import org.testng.annotations.AfterMethod;
                import org.testng.annotations.BeforeMethod;
                import org.testng.annotations.Test;
                import static org.testng.Assert.*;

                @SpringBootTest
                @ActiveProfiles("test")
                @Transactional
                """ + buildTestPropertySourceAnnotation(normalizedDatabaseType) + """

                public class {ClassName}Test extends AbstractTestNGSpringContextTests {

                    private final Long TEST_USER_ID = 1L;
                    private Long suiteId;

                    @Autowired private {TestedService} testedService;
                    @Autowired private ProjectRepository projectRepository;
                    @Autowired private TestSuiteRepository testSuiteRepository;
                    @Autowired private ProjectMemberRepository projectMemberRepository;

                    @BeforeMethod
                    public void setUp() {
                        SecurityContextHolder.setContext(new SecurityContextImpl(
                            new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, Collections.emptyList())
                        ));
                        Project project = new Project();
                        project.setName("Test"); project.setDescription("desc");
                        project = projectRepository.save(project);
                        TestSuite suite = new TestSuite();
                        suite.setName("Suite"); suite.setProject(project);
                        suite.setType(TestSuite.TestType.INTEGRATION);
                        suite = testSuiteRepository.save(suite);
                        suiteId = suite.getId();
                        ProjectMember member = new ProjectMember();
                        member.setProject(project); member.setUserId(TEST_USER_ID);
                        member.setRole(ProjectMember.Role.ADMIN);
                        projectMemberRepository.save(member);
                    }

                    @AfterMethod
                    public void tearDown() { SecurityContextHolder.clearContext(); }

                    @Test
                    public void test_{methodName}_{scenario}() {
                        // TON CODE ICI
                    }
                }

                RÈGLES pour remplir les placeholders :
                - {basePackage} : déduis-le depuis "package ..." dans le squelette fourni — copie-le EXACTEMENT, ne l'invente pas
                - {TestedService} : la classe du service visible dans le squelette (ex: TestCaseService)
                - {ClassName} : nom de la classe testée (ex: TestCaseService → TestCaseServiceTest)
                - Champs du Request : utilise UNIQUEMENT les clés du testData fourni comme noms de setters
                  Exemple : testData={"title":"X","type":"INTEGRATION"} → request.setTitle("X"); request.setType("INTEGRATION");
                  INTERDIT : inventer des setters comme setName(), setActive(), setFlaky() s'ils ne sont pas dans testData
                  INTERDIT : utiliser des enums internes (XxxRequest.RiskLevel.LOW) — les champs sont des String
                - Pour les suites INTEGRATION, la request DOIT avoir setGeneratedCode("package suites.integration; public class Stub {}")
                  OU setDescriptionAI("...") — sans l'un des deux, le service lève une erreur 400
                - Assertions minimales : assertNotNull(response); assertEquals(response.getTitle(), valeurAttendue);
                """);

            case "WEB" -> """
                Génère un test E2E Web en complétant ce squelette. Respecte-le à la lettre.

                SQUELETTE :
                package suites.herapp;

                import io.github.bonigarcia.wdm.WebDriverManager;
                import org.openqa.selenium.*;
                import org.openqa.selenium.chrome.ChromeDriver;
                import org.openqa.selenium.chrome.ChromeOptions;
                import org.testng.annotations.*;
                import static org.testng.Assert.*;
                import java.io.*;
                import java.nio.file.*;

                public class {ClassName}Test {

                    private WebDriver driver;
                    private final String BASE_URL = System.getProperty("BASE_URL", "http://localhost:3000");

                    @BeforeMethod
                    public void setUp() {
                        WebDriverManager.chromedriver().setup();
                        ChromeOptions opts = new ChromeOptions();
                        opts.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
                        driver = new ChromeDriver(opts);
                        driver.manage().window().maximize();
                    }

                    @AfterMethod
                    public void tearDown() {
                        if (driver != null) driver.quit();
                    }

                    private String takeScreenshot(String testName) throws IOException {
                        if (!(driver instanceof TakesScreenshot ts)) return "";
                        byte[] bytes = ts.getScreenshotAs(OutputType.BYTES);
                        Path dir = Paths.get("screenshots"); Files.createDirectories(dir);
                        Path file = dir.resolve(testName + "_" + System.currentTimeMillis() + ".png");
                        Files.write(file, bytes);
                        return file.toAbsolutePath().toString();
                    }

                    @Test
                    public void test_{scenario}() {
                        // TON CODE ICI
                        // en cas d'AssertionError : try { ... } catch (AssertionError e) { takeScreenshot("test_{scenario}"); throw e; }
                    }
                }

                RÈGLES pour remplir les placeholders :
                - {ClassName} : nom fonctionnel du test (ex: Login, Checkout)
                - Utilise BASE_URL comme point d'entrée de toutes les navigations
                - Sélecteurs : By.id > By.cssSelector > By.xpath (dans cet ordre de préférence)
                - Chaque interaction importante → takeScreenshot() en cas d'échec
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

        // ── Resolve placeholders using information already available on the server ──
        // The LLM should never guess what {basePackage} or {TestedService} are —
        // we extract them from the skeleton and inject them directly into the prompt.
        String basePackage   = extractBasePackage(classSkeleton);
        String testedClass   = extractClassName(classSkeleton);
        String scenarioLabel = scenarioType != null ? scenarioType.toLowerCase() : "test";
        String methodLabel   = methodName   != null ? methodName                 : "method";

        specifics = specifics
            .replace("{basePackage}",   basePackage  != null ? basePackage  : "com.example")
            .replace("{TestedService}", testedClass  != null ? testedClass  : "ServiceUnderTest")
            .replace("{ClassName}",     testedClass  != null ? testedClass  : "TestedClass")
            .replace("{methodName}",    methodLabel)
            .replace("{scenario}",      scenarioLabel);

        return commonRules + mongoIntegrationRules + header + skeletonBlock + testDataBlock + scenarioBlock + "\n" + specifics + "\n" + "Génère maintenant le code Java.";
    }

    /**
     * Extracts the base package from a skeleton like:
     *   "package com.pfe.platform.ms_gestion.service;" → "com.pfe.platform.ms_gestion"
     * Removes the last segment (service/repository/etc.) to get the root module package.
     */
    private String extractBasePackage(String skeleton) {
        if (skeleton == null || skeleton.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^\\s*package\\s+([\\w.]+)\\s*;", java.util.regex.Pattern.MULTILINE)
            .matcher(skeleton);
        if (!m.find()) return null;
        String pkg = m.group(1); // e.g. "com.pfe.platform.ms_gestion.service"
        int lastDot = pkg.lastIndexOf('.');
        return lastDot > 0 ? pkg.substring(0, lastDot) : pkg;
    }

    /**
     * Extracts the simple class name from a skeleton like:
     *   "public class TestCaseService {" → "TestCaseService"
     */
    private String extractClassName(String skeleton) {
        if (skeleton == null || skeleton.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:public\\s+)?(?:abstract\\s+)?(?:class|interface)\\s+(\\w+)")
            .matcher(skeleton);
        return m.find() ? m.group(1) : null;
    }

    /** Returns the actual Java @TestPropertySource annotation code for the class header. */
    private String buildTestPropertySourceAnnotation(String dbType) {
        return switch (dbType == null ? "" : dbType.trim().toUpperCase()) {
            case "POSTGRESQL" -> """
                @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:postgresql:14:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                })""";
            case "MYSQL" -> """
                @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:tc:mysql:8.0.33:///testdb",
                    "spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
                })""";
            case "MONGODB" -> ""; // MongoDB uses @BeforeSuite + System.setProperty — handled separately
            default -> """
                @TestPropertySource(properties = {
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                    "spring.datasource.driverClassName=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
                })""";
        };
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