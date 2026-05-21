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
        return generateTestCode(type, description, null);
    }

    public String generateTestCode(String type, String description, String databaseType) {
        String prompt = buildPrompt(type, description, databaseType);

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

    private String buildPrompt(String type, String description, String databaseType) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String normalizedDescription = description == null ? "" : description.trim();
        String normalizedDatabaseType = databaseType == null ? "" : databaseType.trim().toUpperCase();

        String commonRules = """
            Tu es un assistant spécialisé en automatisation de tests Java.

            RÈGLES ABSOLUES (tu dois les respecter) :
            - Réponds UNIQUEMENT avec du code Java compilable (un seul fichier) : pas de texte, pas de titre.
            - Ne mets PAS de markdown, PAS de backticks, PAS de blocs ```.
            - Commence directement par 'package' OU 'import'.
            - Utilise TestNG (org.testng.annotations.*) : pas de JUnit.
            - Le code doit contenir : imports, une classe publique, et des méthodes de test annotées @Test.
            - Utilise des assertions TestNG (org.testng.Assert.*) et/ou des vérifications pertinentes.
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

        String specifics = switch (normalizedType) {
            case "UNIT" -> """
                CONSIGNES UNIT (test unitaire pur) :
                - N'utilise PAS Spring (pas de @SpringBootTest, pas de contexte, pas d'@Autowired).
                - Mocker TOUTES les dépendances (repositories, clients externes, autres services) avec Mockito.
                - Utilise @Mock et MockitoAnnotations.openMocks(this) dans une méthode @BeforeMethod.
                - Si possible, utilise @InjectMocks pour la classe sous test.
                - N'utilise PAS de base de données.
                - Chaque test doit vérifier un résultat (assertEquals/assertNotNull/...) ET les interactions (verify(...)).
                - Imports autorisés : TestNG + Mockito + classes métier nécessaires (pas d'imports inutiles).
                """;

            case "INTEGRATION" -> """
                CONSIGNES INTEGRATION (test d'intégration Spring) :
                - Utilise Spring Test : @SpringBootTest (ou @DataJpaTest si c'est uniquement la couche JPA).
                - Ne mocke PAS les repositories : utilise une vraie base H2 en mémoire.
                - Active le profil 'test' via @ActiveProfiles("test").
                - Configure H2 en mémoire directement dans le test (pour être autonome) avec @TestPropertySource(properties = { ... }).
                  Propriétés attendues (exemple) :
                  - spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
                  - spring.datasource.driverClassName=org.h2.Driver
                  - spring.datasource.username=sa
                  - spring.datasource.password=
                  - spring.jpa.hibernate.ddl-auto=create-drop
                  - spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
                - Injecte les beans avec @Autowired.
                - Utilise @Transactional pour isoler/rollback les tests.
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

        return commonRules + mongoIntegrationRules + header + "\n" + specifics + "\n" + "Génère maintenant le code Java.";
    }
}