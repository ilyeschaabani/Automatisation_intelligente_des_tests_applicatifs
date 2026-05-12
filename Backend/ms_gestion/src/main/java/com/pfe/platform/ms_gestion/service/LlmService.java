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
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String normalizedDescription = description == null ? "" : description.trim();

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
                - Utilise Selenium avec HtmlUnitDriver (headless) : org.openqa.selenium.htmlunit.HtmlUnitDriver.
                - N'utilise PAS ChromeDriver ni WebDriverManager (sauf demande explicite).
                - Lis l'URL de base depuis System.getProperty("BASE_URL").
                - Utilise By.id / By.name / By.cssSelector (pas de XPath sauf nécessité).
                - Configure une attente implicite pour la fiabilité (driver.manage().timeouts().implicitlyWait(...)).
                - Ferme le driver dans @AfterMethod.
                - Assertions TestNG sur le contenu (titre, éléments, textes) et sur les navigations.
                """;

            case "API" -> """
                CONSIGNES API (tests REST) :
                - Utilise REST Assured (io.rest-assured.*).
                - Lis l'URL de base depuis System.getProperty("BASE_URL").
                - Envoie des requêtes JSON (contentType JSON) et vérifie : status code, headers, body.
                - Utilise des assertions Hamcrest (org.hamcrest.Matchers.*) avec RestAssured (then().body(...)).
                - Le test doit être robuste (valide au moins un champ du JSON et/ou un header pertinent).
                """;

            default -> """
                CONSIGNES PAR DÉFAUT :
                - Génère un test TestNG minimal, compilable, cohérent avec la description.
                """;
        };

        return commonRules + header + "\n" + specifics + "\n" + "Génère maintenant le code Java.";
    }
}