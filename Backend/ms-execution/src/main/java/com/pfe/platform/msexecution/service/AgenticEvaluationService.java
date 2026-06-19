package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.entity.UxEvaluation.Status;
import com.pfe.platform.msexecution.entity.UxNavigationStep;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import com.pfe.platform.msexecution.repository.UxNavigationStepRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgenticEvaluationService {

    private final VisionProvider visionProvider;
    private final UxEvaluationRepository evaluationRepository;
    private final UxNavigationStepRepository stepRepository;
    private final EvaluationStreamService streamService;
    private final HumanInputService humanInputService;

    /**
     * Plafond de sécurité (anti-boucle infinie) — PAS une limite fonctionnelle.
     * L'agent fait un tour complet et s'arrête de lui-même à saturation bien avant ce nombre.
     */
    @Value("${ux.max-steps:60}")
    private int maxSteps;

    /**
     * Nombre d'étapes consécutives sans nouvelle découverte (page/élément) avant de
     * considérer le tour comme complet. C'est ça qui termine l'exploration, pas un plafond fixe.
     */
    @Value("${ux.saturation-threshold:6}")
    private int saturationThreshold;

    /** Set of evaluation IDs that have been requested to stop. */
    private final Set<Long> stopRequested = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void stopEvaluation(Long evaluationId) {
        stopRequested.add(evaluationId);
        humanInputService.cancel(evaluationId); // unblock any pending HITL wait
        // Also mark in DB so the async thread sees it even during a long Gemini call
        evaluationRepository.findById(evaluationId).ifPresent(eval -> {
            if (eval.getStatus() == Status.RUNNING) {
                eval.setStatus(Status.COMPLETED);
                eval.setErrorMessage("⛔ Arrêté par le testeur");
                evaluationRepository.save(eval);
                log.info("[UX-AGENT {}] Stop: status set to COMPLETED in DB", evaluationId);
            }
        });
        streamService.sendInfo(evaluationId, "⛔ Arrêt demandé par le testeur");
        log.info("[UX-AGENT {}] Stop requested", evaluationId);
    }

    public UxEvaluation createEvaluation(String url, String description, Long projectId) {
        UxEvaluation evaluation = UxEvaluation.builder()
                .url(url)
                .description(description)
                .projectId(projectId)
                .platform(UxEvaluation.Platform.WEB)
                .status(Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        return evaluationRepository.save(evaluation);
    }

    @Async
    public void executeEvaluation(Long evaluationId) {
        log.info("[UX-AGENT {}] Starting agentic evaluation", evaluationId);

        UxEvaluation evaluation = evaluationRepository.findById(evaluationId).orElse(null);
        if (evaluation == null) {
            log.warn("[UX-AGENT {}] Evaluation not found", evaluationId);
            return;
        }

        // Clean up old steps from a previous run
        List<UxNavigationStep> oldSteps = stepRepository.findByEvaluationIdOrderByStepNumberAsc(evaluationId);
        if (!oldSteps.isEmpty()) {
            stepRepository.deleteAll(oldSteps);
            log.info("[UX-AGENT {}] Deleted {} old steps from previous run", evaluationId, oldSteps.size());
        }

        evaluation.setStatus(Status.RUNNING);
        evaluation.setExecutedAt(LocalDateTime.now());
        evaluation.setErrorMessage(null);
        evaluation.setAiAnalysis(null);
        evaluation.setLogs(null);
        evaluationRepository.save(evaluation);

        Instant startedAt = Instant.now();
        WebDriver driver = null;
        List<UxNavigationStep> steps = new ArrayList<>();

        String backend = visionProvider.activeBackend();
        streamService.sendInfo(evaluationId, "🚀 Démarrage de l'évaluation UX (moteur IA : " + backend + ")");
        log.info("[UX-AGENT {}] Using vision backend: {}", evaluationId, backend);
        try {
            // Configuration ChromeDriver
            WebDriverManager.chromedriver().setup();
            ChromeOptions opts = new ChromeOptions();
            opts.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,800",
                "--lang=fr"
            );

            // Check if mobile emulation requested
            boolean mobileMode = evaluation.getDescription() != null
                    && evaluation.getDescription().toLowerCase().contains("[mobile]");
            if (mobileMode) {
                Map<String, Object> deviceMetrics = new HashMap<>();
                deviceMetrics.put("width", 390);
                deviceMetrics.put("height", 844);
                deviceMetrics.put("pixelRatio", 3.0);
                deviceMetrics.put("mobile", true);
                Map<String, Object> mobileEmulation = new HashMap<>();
                mobileEmulation.put("deviceMetrics", deviceMetrics);
                mobileEmulation.put("userAgent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
                opts.setExperimentalOption("mobileEmulation", mobileEmulation);
                log.info("[UX-AGENT {}] Mobile emulation enabled (iPhone 14)", evaluationId);
            }

            driver = new ChromeDriver(opts);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));

            // Chargement de la page initiale
            log.info("[UX-AGENT {}] Navigating to {}", evaluationId, evaluation.getUrl());
            streamService.sendInfo(evaluationId, "🌐 Ouverture de " + evaluation.getUrl() + "…");
            driver.get(evaluation.getUrl());
            waitForPageLoad(driver);

            List<String> history = new ArrayList<>();
            int consecutiveEmptyResponses = 0;

            // Suivi de couverture pour l'exploration adaptative (tour complet)
            Set<String> visitedUrls = new LinkedHashSet<>();
            Set<String> interactedElements = new HashSet<>();
            int stepsWithoutDiscovery = 0;

            // Boucle agentique adaptative : pas de limite fixe, s'arrête à saturation
            int i = 0;
            while (true) {
                i++;

                // Plafond de sécurité (anti-boucle infinie), pas une limite fonctionnelle
                if (i > maxSteps) {
                    log.info("[UX-AGENT {}] Safety cap {} reached", evaluationId, maxSteps);
                    streamService.sendInfo(evaluationId,
                            "ℹ Plafond de sécurité atteint (" + maxSteps + " étapes) — fin du tour");
                    history.add("ℹ Plafond de sécurité atteint à l'étape " + i);
                    break;
                }

                // Check if stop was requested (in-memory flag OR DB status changed)
                if (stopRequested.remove(evaluationId) || isStoppedInDb(evaluationId)) {
                    log.info("[UX-AGENT {}] Stop detected at step {}", evaluationId, i);
                    streamService.sendInfo(evaluationId, "⛔ Exploration arrêtée par le testeur");
                    history.add("⛔ Exploration arrêtée par le testeur à l'étape " + i);
                    break;
                }
                log.info("[UX-AGENT {}] Step {} (cap {}, sans découverte {}/{})",
                        evaluationId, i, maxSteps, stepsWithoutDiscovery, saturationThreshold);

                // Screenshot + compress to reduce Gemini token usage
                byte[] rawScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                byte[] screenshot = compressScreenshot(rawScreenshot);
                String currentUrl = driver.getCurrentUrl();
                String pageTitle = driver.getTitle();

                // Suivi de couverture : nouvelle page découverte ?
                boolean newPage = visitedUrls.add(normalizeUrl(currentUrl));

                // Envoi du screenshot au frontend
                streamService.sendScreenshot(evaluationId, screenshot);
                streamService.sendThinking(evaluationId, i, currentUrl, pageTitle);

                // Petit délai de stabilisation (Ollama Cloud n'a pas le rate limit free-tier d'OpenRouter)
                if (i > 1) {
                    Thread.sleep(1_000);
                }

                // Analyse IA du screenshot
                String prompt = buildActionPrompt(currentUrl, pageTitle, history, i,
                        evaluation.getDescription(), visitedUrls);
                String geminiResponse = visionProvider.analyzeScreenshot(screenshot, prompt);

                // Info backend utilisé
                streamService.send(evaluationId,
                        com.pfe.platform.msexecution.dto.StreamEvent.backendInfo(visionProvider.getLastUsedBackend()));

                // Vérification d'arrêt après l'appel IA
                if (stopRequested.remove(evaluationId) || isStoppedInDb(evaluationId)) {
                    log.info("[UX-AGENT {}] Stop detected after Gemini call", evaluationId);
                    steps.add(saveStep(evaluationId, i, "Exploration arrêtée par le testeur",
                            "⛔ Stop", currentUrl, pageTitle, screenshot, null, "SKIP", null, null));
                    streamService.sendInfo(evaluationId, "⛔ Arrêté par le testeur");
                    history.add("⛔ Arrêté par le testeur");
                    break;
                }

                // Gestion des réponses vides avec garde de retry
                if (geminiResponse == null || geminiResponse.isBlank()) {
                    consecutiveEmptyResponses++;
                    log.warn("[UX-AGENT {}] Gemini no response at step {} (consecutive: {})",
                            evaluationId, i, consecutiveEmptyResponses);

                    if (consecutiveEmptyResponses >= 5) {
                        log.error("[UX-AGENT {}] Stopping after {} consecutive failures",
                                evaluationId, consecutiveEmptyResponses);
                        steps.add(saveStep(evaluationId, i,
                                "⚠ API indisponible — exploration arrêtée",
                                "Arrêt automatique après " + consecutiveEmptyResponses + " échecs consécutifs",
                                currentUrl, pageTitle, screenshot, null, "SKIP", null, null));
                        streamService.sendInfo(evaluationId,
                                "⚠ Tous les modèles sont surchargés (" + consecutiveEmptyResponses
                                + " tentatives) — exploration arrêtée. Réessayez dans quelques minutes.");
                        history.add("⚠ API indisponible après " + consecutiveEmptyResponses
                                + " tentatives — arrêt à l'étape " + i);
                        break;
                    }

                    int waitSecs = 20 + (consecutiveEmptyResponses * 5);
                    streamService.sendInfo(evaluationId,
                            "⏳ Rate limit atteint (tentative " + consecutiveEmptyResponses
                            + "/5) — nouvelle tentative dans " + waitSecs + "s…");
                    steps.add(saveStep(evaluationId, i, "⏳ Rate limit — attente " + waitSecs + "s…",
                            "429 rate limit — attente avant retry",
                            currentUrl, pageTitle, screenshot, null, "SKIP", null, null));
                    Thread.sleep(waitSecs * 1000L);
                    i--; // retry same step number
                    continue;
                }

                consecutiveEmptyResponses = 0; // reset on success

                log.info("[UX-AGENT {}] Gemini response: {}", evaluationId,
                        geminiResponse.length() > 200 ? geminiResponse.substring(0, 200) + "..." : geminiResponse);

                // Parse the decision
                AgentDecision decision = parseDecision(geminiResponse);

                // Envoi de l'observation
                if (decision.observation != null) {
                    streamService.sendObservation(evaluationId, i, decision.observation, currentUrl, pageTitle);
                }

                // Intervention humaine si nécessaire
                if ("NEEDS_INPUT".equalsIgnoreCase(decision.actionType)) {
                    String question = decision.question != null ? decision.question
                            : "J'ai besoin d'informations pour continuer. Pouvez-vous m'aider ?";
                    log.info("[UX-AGENT {}] NEEDS_INPUT at step {}: {}", evaluationId, i, question);

                    steps.add(saveStep(evaluationId, i,
                            "⏸ Intervention humaine requise : " + question,
                            "En attente de la réponse du testeur",
                            currentUrl, pageTitle, rawScreenshot, geminiResponse,
                            "NEEDS_INPUT", null, null));

                    streamService.sendNeedsInput(evaluationId, i, question, decision.hint);

                    String humanAnswer = null;
                    try {
                        humanAnswer = humanInputService.waitForInput(evaluationId, Duration.ofMinutes(10));
                        streamService.sendResumed(evaluationId, humanAnswer);
                        history.add("Étape " + i + ": L'IA a demandé — \"" + question
                                + "\" → Testeur a répondu : " + humanAnswer);
                        log.info("[UX-AGENT {}] Human answered, retrying step {}", evaluationId, i);
                    } catch (TimeoutException e) {
                        log.warn("[UX-AGENT {}] No human answer in 10 min, skipping step {}", evaluationId, i);
                        streamService.sendInfo(evaluationId,
                                "⏱ Aucune réponse après 10 minutes — l'IA continue sans cette information");
                        history.add("⚠ Intervention humaine non reçue (timeout) à l'étape " + i);
                        // don't retry — just advance
                        continue;
                    }
                    // Retry same step with the human's answer in history
                    i--;
                    continue;
                }

                // Envoi de l'action au frontend
                streamService.sendAction(evaluationId, i,
                        decision.actionType, decision.selector, decision.value, decision.reason);

                // Sauvegarde de l'étape
                UxNavigationStep step = saveStep(evaluationId, i,
                        decision.observation, decision.reason,
                        currentUrl, pageTitle, rawScreenshot, geminiResponse,
                        decision.actionType, decision.selector, decision.value);
                steps.add(step);

                history.add("Étape " + i + " (" + currentUrl + "): " + decision.observation
                        + " → Action: " + decision.reason);

                // Vérification fin d'exploration
                if ("DONE".equalsIgnoreCase(decision.actionType)) {
                    log.info("[UX-AGENT {}] Gemini decided to stop exploring", evaluationId);
                    streamService.sendInfo(evaluationId, "✅ L'IA a terminé l'exploration");
                    break;
                }

                // Exécution de l'action
                boolean actionOk = executeAction(driver, decision);
                streamService.sendActionResult(evaluationId, i, actionOk,
                        actionOk ? "Action exécutée avec succès"
                                 : "Action échouée sur : " + decision.selector);

                if (!actionOk) {
                    log.warn("[UX-AGENT {}] Action failed: {} on '{}'",
                            evaluationId, decision.actionType, decision.selector);
                    history.add("  ⚠ Action échouée: " + decision.actionType + " sur " + decision.selector);
                }

                streamService.sendStepDone(evaluationId, i, maxSteps);

                // Attente stabilisation page
                Thread.sleep(1500);
                waitForPageLoad(driver);

                // ── Suivi de couverture : l'agent a-t-il découvert du nouveau ? ──
                String elementSig = decision.actionType + "|" + currentUrl + "|" + decision.selector;
                boolean newElement = interactedElements.add(elementSig);
                String newUrl = normalizeUrl(driver.getCurrentUrl());
                boolean landedOnNewPage = visitedUrls.add(newUrl);

                if (newPage || newElement || landedOnNewPage) {
                    stepsWithoutDiscovery = 0; // découverte → on continue le tour
                } else {
                    stepsWithoutDiscovery++;
                    if (stepsWithoutDiscovery >= saturationThreshold) {
                        log.info("[UX-AGENT {}] Saturation atteinte ({} étapes sans découverte) — tour complet",
                                evaluationId, stepsWithoutDiscovery);
                        streamService.sendInfo(evaluationId,
                                "✅ Tour complet terminé — " + visitedUrls.size()
                                + " page(s) explorée(s), plus rien de nouveau à découvrir");
                        history.add("✅ Tour complet : " + visitedUrls.size()
                                + " pages visitées, saturation à l'étape " + i);
                        break;
                    }
                }
            }

            // Génération du rapport final
            log.info("[UX-AGENT {}] Generating final UX report ({} steps)", evaluationId, steps.size());
            streamService.sendInfo(evaluationId, "📝 Génération du rapport UX en cours…");
            String finalReport = buildFinalReport(evaluation.getUrl(), steps, evaluation.getDescription());

            // Sauvegarde des résultats
            evaluation.setAiAnalysis(finalReport);
            evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            evaluation.setStatus(Status.COMPLETED);
            evaluation.setLogs(history.stream().collect(Collectors.joining("\n")));
            evaluationRepository.save(evaluation);

            streamService.sendCompleted(evaluationId, finalReport);
            log.info("[UX-AGENT {}] Completed in {} ms with {} steps",
                    evaluationId, evaluation.getDurationMs(), steps.size());

        } catch (Exception e) {
            log.error("[UX-AGENT {}] Failed: {}", evaluationId, e.getMessage(), e);
            evaluation.setStatus(Status.FAILED);
            evaluation.setErrorMessage(e.getMessage());
            evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            evaluationRepository.save(evaluation);
            streamService.sendFailed(evaluationId, e.getMessage() != null ? e.getMessage() : "Erreur inconnue");
        } finally {
            humanInputService.cancel(evaluationId); // release any pending HITL future
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    // Construction des prompts

    private String buildActionPrompt(String url, String pageTitle,
                                     List<String> history, int step,
                                     String userDescription, Set<String> visitedUrls) {
        String historyText = history.isEmpty() ? "Aucune (première visite)"
                : String.join("\n", history);

        String userContext = (userDescription != null && !userDescription.isBlank())
                ? "\nLe testeur a précisé : " + userDescription
                : "";

        String visitedText = visitedUrls.isEmpty() ? "Aucune encore"
                : String.join("\n", visitedUrls);

        return """
            Tu es un testeur UX humain chargé de faire un TOUR COMPLET de cette application web.
            Ton objectif : visiter TOUTES les sections principales, tester les formulaires et les
            fonctionnalités, puis te forger un avis sur l'expérience utilisateur.
            Tu ne sais rien de cette application au départ : tu découvres tout en naviguant.%s

            URL actuelle : %s
            Titre de page : %s
            Étape : %d

            Pages déjà visitées (évite de tourner en rond, cherche du NOUVEAU) :
            %s

            Historique de tes actions :
            %s

            Regarde le screenshot et réponds en JSON STRICT (pas de texte avant/après) :
            {
              "observation": "Ce que tu vois sur cette page en 1-2 phrases (en français)",
              "action_type": "CLICK | FILL | SCROLL | NAVIGATE | NEEDS_INPUT | DONE",
              "selector": "Le TEXTE VISIBLE exact du bouton ou lien (ex: 'Accepter', 'Connexion', 'Envoyer'). Sinon un sélecteur CSS simple (#id, .class, input[name='email']). Pour NAVIGATE : mets l'URL complète ici. JAMAIS de :has-text() ni de sélecteurs Playwright. null si NEEDS_INPUT ou DONE",
              "value": "valeur à saisir si FILL, sinon null",
              "question": "Question précise à poser au testeur humain — UNIQUEMENT si action_type est NEEDS_INPUT",
              "hint": "Exemple de réponse attendue — UNIQUEMENT si action_type est NEEDS_INPUT",
              "reason": "Pourquoi tu fais cette action (en français)"
            }

            IMPORTANT pour le champ "selector" :
            - Pour les boutons/liens : utilise UNIQUEMENT le texte visible exact (ex: "Accepter", "Non, merci.")
            - Pour les champs de formulaire : utilise un sélecteur CSS simple (ex: input[name='email'], #password, .search-input)
            - NE JAMAIS utiliser button:has-text() ni des pseudo-sélecteurs Playwright — ils ne fonctionnent pas

            Règles pour un TOUR COMPLET :
            - Priorise les sections JAMAIS visitées (regarde la liste ci-dessus) avant de revenir sur du connu
            - Explore chaque entrée de menu / onglet principal au moins une fois
            - Si tu vois un formulaire, teste-le : soumets-le vide pour voir les messages d'erreur UX, puis avec des données valides
            - Utilise SCROLL si la page a du contenu plus bas que tu n'as pas encore vu
            - Utilise NEEDS_INPUT seulement si tu as besoin d'identifiants réels (login/password), tokens, codes 2FA,
              ou toute info confidentielle que tu ne peux pas inventer. Explique précisément ce dont tu as besoin.
            - Utilise DONE UNIQUEMENT quand tu as réellement fait le tour de TOUTE la plateforme
              (toutes les sections principales visitées et les formulaires testés)
            - Réponds UNIQUEMENT avec le JSON, pas de texte autour
            """.formatted(userContext, url, pageTitle, step, visitedText, historyText);
    }

    private String buildFinalReport(String url, List<UxNavigationStep> steps, String description) {
        String parcours = steps.stream()
                .map(s -> "Étape " + s.getStepNumber() + " (" + s.getPageUrl() + "): "
                        + s.getObservation() + " → " + s.getActionPerformed())
                .collect(Collectors.joining("\n"));

        String prompt = """
            Tu es un expert UX senior avec 10 ans d'expérience.
            Tu viens d'explorer %s en %d étapes comme un vrai utilisateur.
            %s

            Parcours effectué :
            %s

            Rédige un rapport d'expérience utilisateur complet en français :

            ## 1. PREMIÈRE IMPRESSION
            Qu'est-ce que cette application ? À quoi sert-elle ?

            ## 2. POINTS FORTS
            Ce qui est bien conçu, intuitif, agréable.

            ## 3. POINTS DE FRICTION
            Ce qui bloque, confuse, ou frustre un utilisateur.

            ## 4. CLARTÉ DES MESSAGES
            Les messages d'erreur, labels et textes sont-ils compréhensibles ?

            ## 5. NAVIGATION & FLUIDITÉ
            Est-ce facile de trouver ce qu'on cherche ?

            ## 6. RECOMMANDATIONS
            Suggestions concrètes d'amélioration (3-5 points).

            ## 7. SCORE GLOBAL
            Note sur 10 avec justification en 1 phrase.

            IMPORTANT : Écris comme un vrai testeur humain, pas comme une machine.
            Base-toi uniquement sur ce que tu as observé pendant la navigation.
            """.formatted(url, steps.size(),
                description != null ? "Contexte du testeur : " + description : "",
                parcours);

        return visionProvider.analyzeText(prompt);
    }

    // Exécution des actions Selenium

    private boolean executeAction(WebDriver driver, AgentDecision decision) {
        try {
            // Auto-detect: if selector is a full URL, treat as NAVIGATE
            if (decision.selector != null
                    && (decision.selector.startsWith("http://") || decision.selector.startsWith("https://"))) {
                log.info("[UX-AGENT] Selector is a URL — navigating directly to {}", decision.selector);
                driver.get(decision.selector);
                return true;
            }

            switch (decision.actionType.toUpperCase()) {
                case "NAVIGATE" -> {
                    String url = decision.value != null ? decision.value : decision.selector;
                    if (url == null || url.isBlank()) return false;
                    driver.get(url);
                    return true;
                }
                case "CLICK" -> {
                    WebElement el = findElement(driver, decision.selector);
                    if (el == null) return false;
                    try {
                        el.click();
                    } catch (ElementClickInterceptedException e) {
                        // Try JavaScript click as fallback
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                    }
                    return true;
                }
                case "FILL" -> {
                    WebElement el = findElement(driver, decision.selector);
                    if (el == null) return false;
                    el.clear();
                    el.sendKeys(decision.value != null ? decision.value : "test@example.com");
                    return true;
                }
                case "SCROLL" -> {
                    ((JavascriptExecutor) driver).executeScript(
                            "window.scrollBy(0, 400);");
                    return true;
                }
                default -> {
                    log.info("Unknown action type: {}", decision.actionType);
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Action execution failed: {}", e.getMessage());
            return false;
        }
    }

    private WebElement findElement(WebDriver driver, String selector) {
        if (selector == null || selector.isBlank()) return null;

        // Pre-process: extract text from Playwright-style selectors the LLM may produce
        // e.g. "button:has-text('Accepter')" → try "Accepter" as text + "button" as tag
        List<String> textCandidates = extractTextFromSelector(selector);

        // 1) Try each comma-separated part as CSS selector
        for (String part : selector.split(",")) {
            String css = part.trim();
            if (css.isEmpty() || css.contains(":has-text(") || css.contains(":has(")) continue;
            try {
                WebElement el = driver.findElement(By.cssSelector(css));
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {}
        }

        // 2) Try extracted text candidates via XPath (buttons, links, any clickable)
        for (String text : textCandidates) {
            String escaped = text.replace("'", "\\'");
            // Exact text match on button/a/input
            try {
                String xpath = "//button[normalize-space(.)='" + escaped
                        + "'] | //a[normalize-space(.)='" + escaped
                        + "'] | //input[@value='" + escaped + "']";
                WebElement el = driver.findElement(By.xpath(xpath));
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {}
            // Contains text match
            try {
                String xpath = "//button[contains(.,'" + escaped
                        + "')] | //a[contains(.,'" + escaped
                        + "')] | //*[@role='button'][contains(.,'" + escaped + "')]";
                WebElement el = driver.findElement(By.xpath(xpath));
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {}
            // Any element with that text
            try {
                String xpath = "//*[normalize-space(text())='" + escaped + "']";
                WebElement el = driver.findElement(By.xpath(xpath));
                if (el.isDisplayed()) return el;
            } catch (Exception ignored) {}
        }

        // 3) Fallback: raw selector as link text
        try {
            WebElement el = driver.findElement(By.linkText(selector));
            if (el.isDisplayed()) return el;
        } catch (Exception ignored) {}
        try {
            WebElement el = driver.findElement(By.partialLinkText(selector));
            if (el.isDisplayed()) return el;
        } catch (Exception ignored) {}

        // 4) Last resort: raw selector as XPath text search
        try {
            String escaped = selector.replace("'", "\\'");
            String xpath = "//*[contains(text(),'" + escaped + "')]";
            WebElement el = driver.findElement(By.xpath(xpath));
            if (el.isDisplayed()) return el;
        } catch (Exception ignored) {}

        log.warn("Élément introuvable pour le sélecteur: {}", selector);
        return null;
    }

    private List<String> extractTextFromSelector(String selector) {
        List<String> texts = new ArrayList<>();
        // Extract from :has-text('...') or :has-text("...")
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(":has-text\\(['\"](.+?)['\"]\\)").matcher(selector);
        while (m.find()) {
            texts.add(m.group(1));
        }
        // Extract from :text('...') (another Playwright pattern)
        m = java.util.regex.Pattern.compile(":text\\(['\"](.+?)['\"]\\)").matcher(selector);
        while (m.find()) {
            texts.add(m.group(1));
        }
        // Extract from [aria-label='...'] or [aria-label*='...']
        m = java.util.regex.Pattern.compile("\\[aria-label\\*?=['\"](.+?)['\"]\\]").matcher(selector);
        while (m.find()) {
            texts.add(m.group(1));
        }
        // If no patterns matched and selector looks like plain text (no CSS chars), use it directly
        if (texts.isEmpty() && !selector.matches(".*[.#\\[\\]>+~:@].*")) {
            texts.add(selector);
        }
        return texts;
    }

    // Parsing de la décision IA

    private AgentDecision parseDecision(String geminiResponse) {
        AgentDecision decision = new AgentDecision();
        decision.observation = "Observation non disponible";
        decision.actionType = "SCROLL"; // default to SCROLL instead of DONE — keep exploring
        decision.selector = null;
        decision.value = null;
        decision.reason = "Action par défaut";

        if (geminiResponse == null || geminiResponse.isBlank()) return decision;

        // Extract JSON from response (Gemini wraps in ```json ... ``` or adds text around)
        String json = geminiResponse.trim();
        // Remove markdown fences
        json = json.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            json = json.substring(braceStart, braceEnd + 1);
        }

        log.debug("[PARSE] Cleaned JSON: {}", json.length() > 500 ? json.substring(0, 500) : json);

        try {
            decision.observation = extractJsonField(json, "observation");
            decision.actionType = extractJsonField(json, "action_type");
            decision.selector = extractJsonField(json, "selector");
            decision.value = extractJsonField(json, "value");
            decision.reason = extractJsonField(json, "reason");
            decision.question = extractJsonField(json, "question");
            decision.hint = extractJsonField(json, "hint");

            log.info("[PARSE] action_type={}, selector={}, reason={}",
                    decision.actionType, decision.selector, decision.reason);

            if (decision.actionType == null || decision.actionType.isBlank()) {
                // Try to infer action from the text
                String lower = json.toLowerCase();
                if (lower.contains("\"done\"") || lower.contains("terminé")) {
                    decision.actionType = "DONE";
                } else if (lower.contains("needs_input") || lower.contains("intervention") || lower.contains("identifiant")) {
                    decision.actionType = "NEEDS_INPUT";
                } else if (lower.contains("\"fill\"") || lower.contains("remplir") || lower.contains("saisir")) {
                    decision.actionType = "FILL";
                } else if (lower.contains("\"click\"") || lower.contains("cliquer") || lower.contains("soumettre")) {
                    decision.actionType = "CLICK";
                } else {
                    decision.actionType = "SCROLL"; // keep exploring
                }
                log.info("[PARSE] Inferred action_type={}", decision.actionType);
            }

            if (decision.observation == null) {
                decision.observation = geminiResponse.substring(0, Math.min(200, geminiResponse.length()));
            }
        } catch (Exception e) {
            log.warn("[PARSE] Failed to parse Gemini JSON: {}", e.getMessage());
            decision.observation = geminiResponse.substring(0, Math.min(300, geminiResponse.length()));
            decision.actionType = "SCROLL";
        }

        return decision;
    }

    private String extractJsonField(String json, String field) {
        // Match "field": "value" or "field": null
        String pattern1 = "\"" + field + "\"\\s*:\\s*\"";
        int idx = -1;
        // Find the field
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern1).matcher(json);
        if (m.find()) {
            int start = m.end();
            // Find closing quote (handle escaped quotes)
            int end = start;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && (end == 0 || json.charAt(end - 1) != '\\')) break;
                end++;
            }
            return end > start ? json.substring(start, end) : null;
        }

        // Check for null value
        String pattern2 = "\"" + field + "\"\\s*:\\s*null";
        if (java.util.regex.Pattern.compile(pattern2).matcher(json).find()) {
            return null;
        }

        return null;
    }

    // Méthodes utilitaires

    private boolean isStoppedInDb(Long evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .map(e -> e.getStatus() != Status.RUNNING)
                .orElse(true);
    }

    /**
     * Normalise une URL pour le suivi de couverture : retire le fragment (#…) et le slash final,
     * pour que deux variantes de la même page ne comptent pas comme deux pages distinctes.
     */
    private String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private void waitForPageLoad(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
            );
        } catch (Exception ignored) {}
    }

    /** Compression du screenshot (JPEG 60%, max 800px largeur) pour réduire la taille envoyée à l'API. */
    private byte[] compressScreenshot(byte[] pngBytes) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (original == null) return pngBytes;

            // Scale down to max 800px wide
            int targetWidth = Math.min(original.getWidth(), 800);
            int targetHeight = (int) ((double) original.getHeight() * targetWidth / original.getWidth());
            java.awt.Image scaled = original.getScaledInstance(targetWidth, targetHeight,
                    java.awt.Image.SCALE_SMOOTH);
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight,
                    BufferedImage.TYPE_INT_RGB);
            resized.getGraphics().drawImage(scaled, 0, 0, null);

            // Write as JPEG at 60% quality
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO
                    .getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.6f);
            writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(out));
            writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
            writer.dispose();

            byte[] compressed = out.toByteArray();
            log.info("Screenshot compressed: {}KB → {}KB",
                    pngBytes.length / 1024, compressed.length / 1024);
            return compressed;
        } catch (Exception e) {
            log.warn("Screenshot compression failed, using original: {}", e.getMessage());
            return pngBytes;
        }
    }

    private UxNavigationStep saveStep(Long evaluationId, int stepNumber,
                                       String observation, String action,
                                       String url, String title,
                                       byte[] screenshot, String rawResponse,
                                       String actionType, String selector, String fillValue) {
        UxNavigationStep step = UxNavigationStep.builder()
                .evaluationId(evaluationId)
                .stepNumber(stepNumber)
                .stepName("Étape " + stepNumber)
                .observation(observation)
                .actionPerformed(action)
                .pageUrl(url)
                .pageTitle(title)
                .screenshotBase64(Base64.getEncoder().encodeToString(screenshot))
                .geminiRawResponse(rawResponse)
                .actionType(actionType)
                .selector(selector)
                .fillValue(fillValue)
                .createdAt(LocalDateTime.now())
                .build();
        return stepRepository.save(step);
    }

    private static class AgentDecision {
        String observation;
        String actionType;
        String selector;
        String value;
        String reason;
        String question;
        String hint;
    }
}
