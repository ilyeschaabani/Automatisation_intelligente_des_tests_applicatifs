package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.service.LlmClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmClient llmClient;

    @PostMapping({"/generate-functional-script", "/generate-ux-script"})
    public ResponseEntity<GenerateFunctionalScriptResponse> generateFunctionalScript(@RequestBody GenerateFunctionalScriptRequest req) {
        String prompt = buildFunctionalPrompt(req.getPlatform(), req.getUrl(), req.getDescription());
        String script = llmClient.generateFunctionalTestCode(req.getPlatform(), prompt);
        return ResponseEntity.ok(new GenerateFunctionalScriptResponse(script));
    }

    private String buildFunctionalPrompt(String platform, String url, String description) {
        return "Tu es un testeur QA humain expert chargé de tester le fonctionnement complet d'une application " + safeValue(platform) + ".\n\n"
                + "Ton objectif : tester les fonctionnalités, détecter les anomalies, et produire un résumé détaillé.\n\n"
                + "Contexte:\n"
                + "URL: " + safeValue(url) + "\n"
                + "Description: " + safeValue(description) + "\n\n"
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

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateFunctionalScriptRequest {
        private String platform;
        private String url;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateFunctionalScriptResponse {
        private String script;
    }
}
