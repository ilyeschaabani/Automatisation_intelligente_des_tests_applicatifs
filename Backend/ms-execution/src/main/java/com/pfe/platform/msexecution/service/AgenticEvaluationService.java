package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.dto.FormSchema;
import com.pfe.platform.msexecution.dto.FunctionalTestResult;
import com.pfe.platform.msexecution.entity.UxEvaluation;
import com.pfe.platform.msexecution.entity.UxEvaluation.Status;
import com.pfe.platform.msexecution.entity.UxNavigationStep;
import com.pfe.platform.msexecution.repository.UxEvaluationRepository;
import com.pfe.platform.msexecution.repository.UxNavigationStepRepository;
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
    private final AppiumDriverService appiumDriverService;
    private final WebFunctionalTester webFunctionalTester;
    private final com.pfe.platform.msexecution.repository.FunctionalTestResultRepository functionalTestResultRepository;

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

    /** Set of evaluation IDs currently paused by the tester. */
    private final Set<Long> pauseRequested = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void pauseEvaluation(Long evaluationId) {
        pauseRequested.add(evaluationId);
        streamService.sendInfo(evaluationId, "⏸ Test mis en pause par le testeur");
        log.info("[UX-AGENT {}] Pause requested", evaluationId);
    }

    public void resumeEvaluation(Long evaluationId) {
        pauseRequested.remove(evaluationId);
        streamService.sendInfo(evaluationId, "▶ Reprise du test");
        log.info("[UX-AGENT {}] Resume requested", evaluationId);
    }

    /** Bloque tant que le testeur a mis le test en pause (sans consommer de CPU). Renvoie false si arrêt demandé. */
    private boolean awaitIfPaused(Long evaluationId) {
        boolean wasPaused = false;
        while (pauseRequested.contains(evaluationId)) {
            wasPaused = true;
            if (stopRequested.contains(evaluationId) || isStoppedInDb(evaluationId)) {
                return false;
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        if (wasPaused) {
            streamService.sendInfo(evaluationId, "▶ Reprise — le test continue");
        }
        return true;
    }

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
        return createEvaluation(url, description, projectId, "WEB", null);
    }

    public UxEvaluation createEvaluation(String url, String description, Long projectId, String platform) {
        return createEvaluation(url, description, projectId, platform, null);
    }

    public UxEvaluation createEvaluation(String url, String description, Long projectId, String platform, String apkPath) {
        return createEvaluation(url, description, projectId, platform, apkPath, "AUTO");
    }

    public UxEvaluation createEvaluation(String url, String description, Long projectId, String platform,
                                         String apkPath, String reviewMode) {
        UxEvaluation.Platform p;
        try {
            p = UxEvaluation.Platform.valueOf(platform.toUpperCase());
        } catch (Exception e) {
            p = UxEvaluation.Platform.WEB;
        }
        String mode = "SUPERVISED".equalsIgnoreCase(reviewMode) ? "SUPERVISED" : "AUTO";
        UxEvaluation evaluation = UxEvaluation.builder()
                .url(url)
                .description(description)
                .projectId(projectId)
                .platform(p)
                .apkPath(apkPath)
                .reviewMode(mode)
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

        // Clean up old functional test results from a previous run
        try {
            functionalTestResultRepository.deleteByEvaluationId(evaluationId);
        } catch (Exception ignored) {}

        evaluation.setStatus(Status.RUNNING);
        evaluation.setExecutedAt(LocalDateTime.now());
        evaluation.setErrorMessage(null);
        evaluation.setAiAnalysis(null);
        evaluation.setLogs(null);
        evaluationRepository.save(evaluation);

        if (evaluation.getPlatform() == UxEvaluation.Platform.MOBILE_APP) {
            executeApkEvaluation(evaluation);
            return;
        }

        Instant startedAt = Instant.now();
        WebDriver driver = null;
        List<UxNavigationStep> steps = new ArrayList<>();

        String backend = visionProvider.activeBackend();
        streamService.sendInfo(evaluationId, "🚀 Démarrage de l'évaluation UX (moteur IA : " + backend + ")");
        log.info("[UX-AGENT {}] Using vision backend: {}", evaluationId, backend);
        try {
            // Selenium Manager (built into Selenium 4.11+) auto-resolves chromedriver
            // No WebDriverManager.setup() needed — avoids network timeouts
            ChromeOptions opts = new ChromeOptions();

            boolean mobileMode = evaluation.getPlatform() == UxEvaluation.Platform.WEB_MOBILE
                    || evaluation.getPlatform() == UxEvaluation.Platform.MOBILE
                    || (evaluation.getDescription() != null
                        && evaluation.getDescription().toLowerCase().contains("[mobile]"));

            if (mobileMode) {
                opts.addArguments(
                    "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-gpu", "--window-size=390,844", "--lang=fr"
                );
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
                log.info("[UX-AGENT {}] Mobile emulation enabled (iPhone 14 — 390x844)", evaluationId);
                streamService.sendInfo(evaluationId, "📱 Mode mobile activé (iPhone 14 — 390×844)");
            } else {
                opts.addArguments(
                    "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-gpu", "--window-size=1280,800", "--lang=fr"
                );
            }

            driver = new ChromeDriver(opts);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));

            // Chargement de la page initiale
            log.info("[UX-AGENT {}] Navigating to {}", evaluationId, evaluation.getUrl());
            streamService.sendInfo(evaluationId, "🌐 Ouverture de " + evaluation.getUrl() + "…");
            driver.get(evaluation.getUrl());
            waitForPageLoad(driver);

            // ── Phase 0 : Découverte automatique du menu (texte → URL) ──
            LinkedHashMap<String, String> menuSections = discoverMenuLinks(driver, evaluation.getUrl());
            Set<String> visitedSections = new LinkedHashSet<>();
            if (!menuSections.isEmpty()) {
                log.info("[UX-AGENT {}] Menu discovered: {} sections", evaluationId, menuSections.size());
                streamService.sendInfo(evaluationId,
                        "🗺 " + menuSections.size() + " section(s) détectée(s) dans le menu : "
                        + String.join(", ", menuSections.keySet().stream().limit(8)
                            .map(s -> s.length() > 30 ? s.substring(0, 30) + "…" : s)
                            .toList()));
            }

            List<String> history = new ArrayList<>();
            int consecutiveEmptyResponses = 0;
            int consecutiveFailures = 0;
            String lastUrl = "";
            int sameUrlCount = 0;

            // Suivi de couverture pour l'exploration adaptative (tour complet)
            Set<String> visitedUrls = new LinkedHashSet<>();
            Set<String> interactedElements = new HashSet<>();
            int stepsWithoutDiscovery = 0;

            // Test fonctionnel : formulaires déjà testés + résultats collectés
            Set<String> testedFormSignatures = new HashSet<>();
            List<FunctionalTestResult> functionalResults = new ArrayList<>();

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

                // Pause éventuelle demandée par le testeur (HITL)
                if (!awaitIfPaused(evaluationId)) {
                    streamService.sendInfo(evaluationId, "⛔ Arrêté pendant la pause");
                    break;
                }
                log.info("[UX-AGENT {}] Step {} (cap {}, sans découverte {}/{})",
                        evaluationId, i, maxSteps, stepsWithoutDiscovery, saturationThreshold);

                // Screenshot : version HD pour le live view, compressée pour l'IA
                byte[] rawScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                byte[] hdScreenshot = compressScreenshot(rawScreenshot, 1280, 0.85f);
                byte[] aiScreenshot = compressScreenshot(rawScreenshot, 800, 0.6f);
                String currentUrl = driver.getCurrentUrl();
                String pageTitle = driver.getTitle();

                // Détection de boucle sur la même page
                String normalizedCurrent = normalizeUrl(currentUrl);
                if (normalizedCurrent.equals(lastUrl)) {
                    sameUrlCount++;
                    if (sameUrlCount >= 4) {
                        log.warn("[UX-AGENT {}] Stuck on same URL for {} steps, forcing navigation to next unvisited section",
                                evaluationId, sameUrlCount);
                        String nextUrl = menuSections.entrySet().stream()
                                .filter(e -> !visitedSections.contains(e.getKey()))
                                .map(Map.Entry::getValue)
                                .findFirst().orElse(null);
                        if (nextUrl != null) {
                            driver.get(nextUrl);
                            history.add("SYSTEM: Forced navigation to " + nextUrl + " (stuck " + sameUrlCount + " steps)");
                            sameUrlCount = 0;
                            continue;
                        }
                    }
                } else {
                    lastUrl = normalizedCurrent;
                    sameUrlCount = 0;
                }

                // Suivi de couverture : nouvelle page découverte ?
                boolean newPage = visitedUrls.add(normalizedCurrent);
                for (Map.Entry<String, String> entry : menuSections.entrySet()) {
                    String normalizedMenuUrl = normalizeUrl(entry.getValue());
                    if (normalizedCurrent.equals(normalizedMenuUrl)
                            || normalizedCurrent.startsWith(normalizedMenuUrl)) {
                        visitedSections.add(entry.getKey());
                    }
                }

                // ── Test fonctionnel : détecter et tester les formulaires de cette page ──
                boolean supervised = "SUPERVISED".equalsIgnoreCase(evaluation.getReviewMode());
                try {
                    List<FormSchema> forms = webFunctionalTester.detectForms(driver);
                    for (FormSchema form : forms) {
                        String sig = form.signature();
                        if (testedFormSignatures.add(sig)) {
                            log.info("[UX-AGENT {}] New form to test functionally: {}", evaluationId, form.label());
                            List<FunctionalTestResult> res =
                                    webFunctionalTester.testForm(driver, form, evaluationId, i, supervised);
                            functionalResults.addAll(res);
                            // Le formulaire a pu naviguer : revenir à la page d'exploration
                            try { driver.get(currentUrl); waitForPageLoad(driver); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception fe) {
                    log.warn("[UX-AGENT {}] Functional testing error: {}", evaluationId, fe.getMessage());
                }

                // Envoi du screenshot HD au frontend
                streamService.sendScreenshot(evaluationId, hdScreenshot);
                streamService.sendThinking(evaluationId, i, currentUrl, pageTitle);

                // Petit délai de stabilisation (Ollama Cloud n'a pas le rate limit free-tier d'OpenRouter)
                if (i > 1) {
                    Thread.sleep(1_000);
                }

                // Analyse IA du screenshot
                String prompt = buildActionPrompt(currentUrl, pageTitle, history, i,
                        evaluation.getDescription(), visitedUrls, menuSections, visitedSections, mobileMode);
                String geminiResponse = visionProvider.analyzeScreenshot(aiScreenshot, prompt);

                // Info backend utilisé
                streamService.send(evaluationId,
                        com.pfe.platform.msexecution.dto.StreamEvent.backendInfo(visionProvider.getLastUsedBackend()));

                // Vérification d'arrêt après l'appel IA
                if (stopRequested.remove(evaluationId) || isStoppedInDb(evaluationId)) {
                    log.info("[UX-AGENT {}] Stop detected after Gemini call", evaluationId);
                    steps.add(saveStep(evaluationId, i, "Exploration arrêtée par le testeur",
                            "⛔ Stop", currentUrl, pageTitle, rawScreenshot, null, "SKIP", null, null));
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
                                currentUrl, pageTitle, rawScreenshot, null, "SKIP", null, null));
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
                            currentUrl, pageTitle, rawScreenshot, null, "SKIP", null, null));
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
                    consecutiveFailures++;
                    log.warn("[UX-AGENT {}] Action failed: {} on '{}' (consecutive: {})",
                            evaluationId, decision.actionType, decision.selector, consecutiveFailures);
                    history.add("  ⚠ Action échouée: " + decision.actionType + " sur " + decision.selector);

                    // Auto-recovery : 3 échecs consécutifs → retour automatique
                    if (consecutiveFailures >= 3) {
                        log.info("[UX-AGENT {}] Auto-recovery: 3 consecutive failures, going back", evaluationId);
                        streamService.sendInfo(evaluationId,
                                "🔄 3 échecs consécutifs — retour automatique à la page précédente");
                        try { driver.navigate().back(); Thread.sleep(1500); waitForPageLoad(driver); }
                        catch (Exception ignored) {}
                        consecutiveFailures = 0;
                        history.add("  🔄 Auto-recovery: retour à la page précédente");
                    }
                } else {
                    consecutiveFailures = 0;
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

            // Persistance des résultats fonctionnels (rapport répétable + comparaison de runs)
            persistFunctionalResults(evaluationId, functionalResults);

            // Génération du rapport final
            log.info("[UX-AGENT {}] Generating final report ({} steps, {} functional tests)",
                    evaluationId, steps.size(), functionalResults.size());
            streamService.sendInfo(evaluationId, "📝 Génération du rapport en cours…");
            String uxReport = buildFinalReport(evaluation.getUrl(), steps, evaluation.getDescription(), mobileMode);
            String functionalSummary = buildFunctionalSummary(functionalResults);
            String finalReport = functionalSummary.isEmpty()
                    ? uxReport
                    : functionalSummary + "\n\n---\n\n" + uxReport;

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
            streamService.sendFailed(evaluationId, sanitizeError(e));
        } finally {
            humanInputService.cancel(evaluationId); // release any pending HITL future
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    /** Message d'erreur lisible pour l'utilisateur : retire stacktraces et dumps techniques. */
    private String sanitizeError(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return "Une erreur technique est survenue pendant l'évaluation.";
        int cut = msg.length();
        for (String marker : new String[]{"{\"value\"", "stacktrace", "\n\tat ", "\n    at ", "UnknownError", "\tat "}) {
            int i = msg.indexOf(marker);
            if (i >= 0 && i < cut) cut = i;
        }
        String clean = msg.substring(0, cut).trim();
        if (clean.isEmpty()) clean = msg.substring(0, Math.min(msg.length(), 160)).trim();
        return clean.length() > 200 ? clean.substring(0, 200) + "…" : clean;
    }

    // Construction des prompts

    private String buildActionPrompt(String url, String pageTitle,
                                     List<String> history, int step,
                                     String userDescription, Set<String> visitedUrls,
                                     LinkedHashMap<String, String> menuSections, Set<String> visitedSections,
                                     boolean mobileMode) {
        String historyText = history.isEmpty() ? "Aucune (première visite)"
                : String.join("\n", history.subList(Math.max(0, history.size() - 15), history.size()));

        String userContext = (userDescription != null && !userDescription.isBlank())
                ? "\nLe testeur a précisé : " + userDescription
                : "";

        String visitedText = visitedUrls.isEmpty() ? "Aucune encore"
                : String.join("\n", visitedUrls);

        // Checklist des sections du menu avec URLs réelles
        StringBuilder sectionChecklist = new StringBuilder();
        if (!menuSections.isEmpty()) {
            for (Map.Entry<String, String> entry : menuSections.entrySet()) {
                boolean visited = visitedSections.contains(entry.getKey());
                sectionChecklist.append(visited ? "  ✅ " : "  ❌ ")
                        .append(entry.getKey())
                        .append(" → ").append(entry.getValue())
                        .append("\n");
            }
        } else {
            sectionChecklist.append("  (Non détectées — explore le menu toi-même)\n");
        }

        String mobileBlock = mobileMode ? """

            🔍 MODE MOBILE — Critères spécifiques à évaluer :
            - Taille des boutons/liens tactiles (doivent être >= 44px pour être confortables au doigt)
            - Texte lisible sans zoomer (taille >= 14px)
            - Pas de scroll horizontal involontaire
            - Menu hamburger fonctionnel et accessible
            - Formulaires adaptés au mobile (champs assez grands, clavier adapté)
            - Images et mise en page responsive
            """ : "";

        return """
            Tu es un testeur UX expert chargé de faire un TOUR COMPLET de cette application web.
            Tu explores méthodiquement TOUTES les sections, tu testes les formulaires, et tu notes
            chaque détail UX (positif ou négatif). Tu ne sais rien au départ — tu découvres tout.%s%s

            === CONTEXTE ===
            URL actuelle : %s
            Titre de page : %s
            Étape : %d

            === SECTIONS DU SITE (checklist) ===
            %s
            === PAGES VISITÉES ===
            %s

            === HISTORIQUE RÉCENT (15 dernières actions) ===
            %s

            === RÉPONSE ATTENDUE ===
            Réponds en JSON STRICT uniquement (pas de texte avant/après, pas de ```json) :
            {
              "observation": "Ce que tu vois sur cette page en 1-2 phrases",
              "action_type": "CLICK | FILL | SCROLL | NAVIGATE | BACK | NEEDS_INPUT | DONE",
              "selector": "Texte visible exact du bouton/lien, OU sélecteur CSS simple, OU URL pour NAVIGATE. null si DONE/BACK/NEEDS_INPUT",
              "value": "valeur à saisir si FILL, sinon null",
              "question": "question pour le testeur humain (UNIQUEMENT si NEEDS_INPUT)",
              "hint": "exemple de réponse (UNIQUEMENT si NEEDS_INPUT)",
              "reason": "Pourquoi tu fais cette action"
            }

            === RÈGLES DE PRIORITÉ (ordre strict) ===
            1. Si un popup/modal bloque la navigation (cookies, promo…) → CLICK pour le fermer
            2. Si des sections ❌ ne sont pas encore visitées → NAVIGATE avec l'URL affichée à côté (après →)
            3. Si la page contient un formulaire non testé → FILL + soumission (d'abord vide pour voir les erreurs, puis avec des données)
            4. Si la page a du contenu non vu plus bas → SCROLL (1 seule fois par page, pas plus)
            5. Si tu es coincé (page PDF, iframe, page morte, pas de liens) → BACK pour revenir en arrière
            6. Si TOUTES les sections ✅ sont visitées et les formulaires testés → DONE

            === RÈGLES SELECTOR ===
            - Boutons/liens : texte visible exact ("Accepter", "Connexion", "Envoyer")
            - Formulaires : sélecteur CSS simple (input[name='email'], #password)
            - Navigation directe : URL complète (https://...)
            - JAMAIS de :has-text() ou sélecteurs Playwright
            - Si un clic échoue 2 fois, essaie NAVIGATE avec l'URL du lien ou BACK
            """.formatted(userContext, mobileBlock, url, pageTitle, step,
                sectionChecklist, visitedText, historyText);
    }

    private String buildFinalReport(String url, List<UxNavigationStep> steps, String description, boolean mobileMode) {
        String parcours = steps.stream()
                .map(s -> "Étape " + s.getStepNumber() + " (" + s.getPageUrl() + "): "
                        + s.getObservation() + " → " + s.getActionPerformed())
                .collect(Collectors.joining("\n"));

        String mobileSection = mobileMode ? """

            ## 5b. ÉVALUATION MOBILE
            - Ergonomie tactile : les boutons font-ils >= 44px ? Faciles à toucher au doigt ?
            - Lisibilité : textes lisibles sans zoomer ? Taille de police suffisante ?
            - Responsive : la mise en page s'adapte-t-elle correctement à l'écran mobile ?
            - Scroll horizontal : y a-t-il du contenu qui dépasse de l'écran ?
            - Menu mobile : le menu hamburger fonctionne-t-il correctement ?
            - Formulaires : les champs sont-ils adaptés au mobile ?
            """ : "";

        String prompt = """
            Tu es un expert UX senior avec 10 ans d'expérience.
            Tu viens d'explorer %s en %d étapes comme un vrai utilisateur%s.
            %s

            Parcours effectué :
            %s

            Rédige un rapport d'expérience utilisateur complet en français.

            COMMENCE OBLIGATOIREMENT par la note globale. Structure EXACTE :

            ## SCORE GLOBAL : X/10
            Justification en 1-2 phrases.

            ## 1. PREMIÈRE IMPRESSION
            Qu'est-ce que cette application ? À quoi sert-elle ?

            ## 2. POINTS FORTS
            Ce qui est bien conçu, intuitif, agréable (3-5 points max).

            ## 3. POINTS DE FRICTION
            Ce qui bloque, confuse, ou frustre un utilisateur (3-5 points max).

            ## 4. CLARTÉ DES MESSAGES
            Les messages d'erreur, labels et textes sont-ils compréhensibles ?

            ## 5. NAVIGATION & FLUIDITÉ
            Est-ce facile de trouver ce qu'on cherche ?
            %s
            ## 6. RECOMMANDATIONS
            Suggestions concrètes d'amélioration (5-8 points prioritaires).

            RÈGLES STRICTES :
            - Le rapport DOIT commencer par "## SCORE GLOBAL : X/10" — c'est la première ligne.
            - Écris comme un vrai testeur humain, pas comme une machine.
            - Base-toi uniquement sur ce que tu as observé pendant la navigation.
            - Sois précis : cite les pages, les boutons, les messages exacts.
            - Sois concis : chaque section fait 3-8 lignes max. Pas de pavés.
            """.formatted(url, steps.size(),
                mobileMode ? " sur mobile (iPhone 14)" : "",
                description != null ? "Contexte du testeur : " + description : "",
                parcours, mobileSection);

        return visionProvider.analyzeText(prompt);
    }

    private void persistFunctionalResults(Long evaluationId, List<FunctionalTestResult> results) {
        if (results == null || results.isEmpty()) return;
        try {
            for (FunctionalTestResult r : results) {
                com.pfe.platform.msexecution.entity.FunctionalTestResultEntity e =
                        com.pfe.platform.msexecution.entity.FunctionalTestResultEntity.builder()
                                .evaluationId(evaluationId)
                                .formLabel(r.getFormLabel())
                                .scenario(r.getScenario())
                                .targetField(r.getTargetField())
                                .inputData(r.getInputData())
                                .expected(r.getExpected())
                                .observed(r.getObserved())
                                .status(r.getStatus() != null ? r.getStatus().name() : null)
                                .severity(r.getSeverity() != null ? r.getSeverity().name() : null)
                                .confidence(r.getConfidence())
                                .evidence(r.getEvidence())
                                .humanValidated(Boolean.FALSE)
                                .createdAt(LocalDateTime.now())
                                .build();
                functionalTestResultRepository.save(e);
            }
            log.info("[UX-AGENT {}] Persisted {} functional test result(s)", evaluationId, results.size());
        } catch (Exception ex) {
            log.warn("[UX-AGENT {}] Failed to persist functional results: {}", evaluationId, ex.getMessage());
        }
    }

    /**
     * Synthèse déterministe des tests fonctionnels — les verdicts viennent du moteur
     * (oracle DOM/HTML5), pas du LLM, pour garantir la fiabilité.
     */
    private String buildFunctionalSummary(List<FunctionalTestResult> results) {
        if (results == null || results.isEmpty()) return "";

        long total = results.size();
        long pass = results.stream().filter(r -> r.getStatus() == FunctionalTestResult.Status.PASS).count();
        long fail = results.stream().filter(r -> r.getStatus() == FunctionalTestResult.Status.FAIL).count();
        long warn = results.stream().filter(r -> r.getStatus() == FunctionalTestResult.Status.WARN).count();
        long review = results.stream().filter(r -> r.getStatus() == FunctionalTestResult.Status.NEEDS_REVIEW).count();

        StringBuilder sb = new StringBuilder();
        sb.append("# RAPPORT DE TESTS FONCTIONNELS\n\n");
        sb.append("## Synthèse\n");
        sb.append("- Cas exécutés : ").append(total).append("\n");
        sb.append("- ✅ Réussis : ").append(pass).append("\n");
        sb.append("- 🔴 Échecs (bugs) : ").append(fail).append("\n");
        sb.append("- 🟠 Avertissements : ").append(warn).append("\n");
        sb.append("- ⏸ À vérifier (humain) : ").append(review).append("\n\n");

        // Grouper par formulaire
        Map<String, List<FunctionalTestResult>> byForm = new LinkedHashMap<>();
        for (FunctionalTestResult r : results) {
            byForm.computeIfAbsent(r.getFormLabel(), k -> new ArrayList<>()).add(r);
        }

        if (fail > 0) {
            sb.append("## 🔴 Bugs détectés (priorité)\n");
            for (FunctionalTestResult r : results) {
                if (r.getStatus() == FunctionalTestResult.Status.FAIL) {
                    sb.append("- **[").append(r.getSeverity()).append("] ").append(r.getFormLabel())
                      .append(" / ").append(r.getScenario());
                    if (r.getTargetField() != null) sb.append(" (").append(r.getTargetField()).append(")");
                    sb.append("**\n");
                    sb.append("  - Attendu : ").append(r.getExpected()).append("\n");
                    sb.append("  - Observé : ").append(r.getObserved()).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("## Détail par formulaire\n");
        for (Map.Entry<String, List<FunctionalTestResult>> e : byForm.entrySet()) {
            sb.append("### ").append(e.getKey()).append("\n");
            for (FunctionalTestResult r : e.getValue()) {
                String icon = switch (r.getStatus()) {
                    case PASS -> "✅";
                    case FAIL -> "🔴";
                    case WARN -> "🟠";
                    case NEEDS_REVIEW -> "⏸";
                };
                sb.append(icon).append(" `").append(r.getScenario()).append("`");
                if (r.getTargetField() != null) sb.append(" — ").append(r.getTargetField());
                sb.append(" : ").append(r.getObserved()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // Exécution des actions Selenium

    private boolean executeAction(WebDriver driver, AgentDecision decision) {
        try {
            // Auto-detect: if selector is a full URL, treat as NAVIGATE
            if (decision.selector != null
                    && (decision.selector.startsWith("http://") || decision.selector.startsWith("https://"))) {
                log.info("[UX-AGENT] Selector is a URL — navigating directly to {}", decision.selector);
                driver.get(decision.selector);
                waitForPageLoad(driver);
                return true;
            }

            switch (decision.actionType.toUpperCase()) {
                case "NAVIGATE" -> {
                    String url = decision.value != null ? decision.value : decision.selector;
                    if (url == null || url.isBlank()) return false;
                    driver.get(url);
                    waitForPageLoad(driver);
                    return true;
                }
                case "BACK" -> {
                    driver.navigate().back();
                    waitForPageLoad(driver);
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
                } else if (lower.contains("\"navigate\"") || lower.contains("naviguer") || lower.contains("navigue")) {
                    decision.actionType = "NAVIGATE";
                } else if (lower.contains("\"back\"") || lower.contains("retour") || lower.contains("précédent")) {
                    decision.actionType = "BACK";
                } else if (lower.contains("\"fill\"") || lower.contains("remplir") || lower.contains("saisir")) {
                    decision.actionType = "FILL";
                } else if (lower.contains("\"click\"") || lower.contains("cliquer") || lower.contains("soumettre")) {
                    decision.actionType = "CLICK";
                } else {
                    decision.actionType = "SCROLL";
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

    /**
     * Phase 0 : extrait les liens de navigation (menu, nav, header) de la page pour
     * construire une checklist de sections à visiter.
     */
    /**
     * Phase 0 : extrait les liens de navigation avec leurs URLs réelles.
     * Retourne une Map ordonnée : texte du lien → URL absolue.
     * Filtre les liens qui pointent vers la page courante (href == baseUrl).
     */
    private LinkedHashMap<String, String> discoverMenuLinks(WebDriver driver, String baseUrl) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) ((JavascriptExecutor) driver)
                    .executeScript(
                        "var results = [];" +
                        "var selectors = 'nav a, header a, [role=navigation] a, .navbar a, .menu a, .sidebar a, .nav a';" +
                        "document.querySelectorAll(selectors).forEach(function(a) {" +
                        "  var text = (a.textContent || '').trim();" +
                        "  var href = a.href || '';" +
                        "  if (text.length > 1 && text.length < 50 && href.startsWith('http') && !href.includes('#')) {" +
                        "    results.push({text: text, href: href});" +
                        "  }" +
                        "});" +
                        "return results;");

            if (links == null || links.isEmpty()) return new LinkedHashMap<>();

            LinkedHashMap<String, String> sections = new LinkedHashMap<>();
            String baseDomain = baseUrl.replaceAll("https?://([^/]+).*", "$1");
            String normalizedBase = normalizeUrl(baseUrl);

            for (Map<String, Object> link : links) {
                String text = String.valueOf(link.get("text")).trim();
                String href = String.valueOf(link.get("href")).trim();
                if (text.isEmpty() || href.isEmpty()) continue;
                if (!href.contains(baseDomain)) continue;
                // Skip links that point to the current page (homepage links, anchors, etc.)
                if (normalizeUrl(href).equals(normalizedBase)) continue;
                String key = text.toLowerCase();
                if (!sections.containsKey(key) && sections.size() < 20) {
                    sections.put(key, href);
                }
            }
            return sections;
        } catch (Exception e) {
            log.debug("[UX-AGENT] Menu discovery failed: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** Compression du screenshot avec paramètres configurables. */
    private byte[] compressScreenshot(byte[] pngBytes, int maxWidth, float quality) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (original == null) return pngBytes;

            int targetWidth = Math.min(original.getWidth(), maxWidth);
            int targetHeight = (int) ((double) original.getHeight() * targetWidth / original.getWidth());
            java.awt.Image scaled = original.getScaledInstance(targetWidth, targetHeight,
                    java.awt.Image.SCALE_SMOOTH);
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight,
                    BufferedImage.TYPE_INT_RGB);
            resized.getGraphics().drawImage(scaled, 0, 0, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO
                    .getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(out));
            writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
            writer.dispose();

            byte[] compressed = out.toByteArray();
            log.info("Screenshot compressed: {}KB → {}KB ({}px, {}%)",
                    pngBytes.length / 1024, compressed.length / 1024, maxWidth, Math.round(quality * 100));
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

    // ═══════════════════════════════════════════════════════════════════
    // APK (Android native app) evaluation
    // ═══════════════════════════════════════════════════════════════════

    private void executeApkEvaluation(UxEvaluation evaluation) {
        Long evaluationId = evaluation.getId();
        Instant startedAt = Instant.now();

        try {
            evaluation.setStatus(Status.RUNNING);
            evaluation.setExecutedAt(LocalDateTime.now());
            evaluationRepository.save(evaluation);

            streamService.sendInfo(evaluationId, "Demarrage de l'emulateur Android Docker...");
            appiumDriverService.ensureEmulatorRunning();
            streamService.sendInfo(evaluationId, "Emulateur pret — copie de l'APK...");

            String hostApkPath = evaluation.getApkPath();
            appiumDriverService.copyApkToContainer(hostApkPath);
            String containerApkPath = appiumDriverService.getContainerApkPath(hostApkPath);

            streamService.sendInfo(evaluationId, "Installation de l'APK sur l'emulateur...");
            appiumDriverService.createSession(containerApkPath);
            streamService.sendInfo(evaluationId, "Application Android lancee — debut de l'exploration");

            Thread.sleep(3000);

            List<UxNavigationStep> steps = new ArrayList<>();
            List<String> history = new ArrayList<>();
            Set<String> visitedScreens = new LinkedHashSet<>();
            int consecutiveFailures = 0;
            int stepsWithoutDiscovery = 0;

            int i = 0;
            while (true) {
                i++;
                if (i > maxSteps) {
                    streamService.sendInfo(evaluationId, "Plafond de securite atteint (" + maxSteps + " etapes)");
                    break;
                }

                if (stopRequested.remove(evaluationId) || isStoppedInDb(evaluationId)) {
                    streamService.sendInfo(evaluationId, "Arret demande");
                    break;
                }

                if (!awaitIfPaused(evaluationId)) {
                    streamService.sendInfo(evaluationId, "Arrete pendant la pause");
                    break;
                }

                byte[] rawScreenshot = appiumDriverService.takeScreenshot();

                try {
                    java.awt.image.BufferedImage rawImg = ImageIO.read(new ByteArrayInputStream(rawScreenshot));
                    if (rawImg != null) {
                        lastRawScreenshotWidth = rawImg.getWidth();
                        lastRawScreenshotHeight = rawImg.getHeight();
                        log.info("[UX-AGENT-APK] Raw screenshot: {}x{}", lastRawScreenshotWidth, lastRawScreenshotHeight);
                    }
                } catch (Exception ignored) {}

                byte[] hdScreenshot = compressScreenshot(rawScreenshot, 1080, 0.85f);
                byte[] aiScreenshot = compressScreenshot(rawScreenshot, 540, 0.6f);

                String currentActivity = appiumDriverService.getCurrentActivity();

                // Liste des éléments interactifs réels (coordonnées exactes depuis l'arbre d'accessibilité)
                List<AppiumDriverService.UiElement> uiElements;
                try {
                    uiElements = appiumDriverService.getInteractiveElements();
                } catch (Exception ex) {
                    uiElements = new ArrayList<>();
                }
                log.info("[UX-AGENT-APK] {} interactive element(s) detected", uiElements.size());

                boolean newScreen = visitedScreens.add(currentActivity);
                if (newScreen) { stepsWithoutDiscovery = 0; } else { stepsWithoutDiscovery++; }

                if (stepsWithoutDiscovery >= saturationThreshold && i > 8) {
                    streamService.sendInfo(evaluationId, "Saturation — toutes les pages principales ont ete explorees");
                    break;
                }

                streamService.sendScreenshot(evaluationId, hdScreenshot);
                streamService.sendThinking(evaluationId, i, currentActivity, "Android App");

                if (i > 1) Thread.sleep(1_000);

                String prompt = buildApkActionPrompt(currentActivity, history, i,
                        evaluation.getDescription(), visitedScreens, uiElements);
                String aiResponse = visionProvider.analyzeScreenshot(aiScreenshot, prompt);

                streamService.send(evaluationId,
                        com.pfe.platform.msexecution.dto.StreamEvent.backendInfo(visionProvider.getLastUsedBackend()));

                if (stopRequested.remove(evaluationId) || isStoppedInDb(evaluationId)) break;

                if (aiResponse == null || aiResponse.isBlank()) {
                    consecutiveFailures++;
                    history.add("Step " + i + " [EMPTY AI RESPONSE]");
                    if (consecutiveFailures >= 3) {
                        try { appiumDriverService.pressBack(); } catch (Exception ignored) {}
                        history.add("SYSTEM: Forced BACK after " + consecutiveFailures + " empty responses");
                        consecutiveFailures = 0;
                    }
                    continue;
                }

                AgentDecision decision = parseDecision(aiResponse);
                if (decision == null) {
                    history.add("Step " + i + " [PARSE ERROR]");
                    continue;
                }

                String observation = decision.observation != null ? decision.observation : "";
                String actionType = decision.actionType != null ? decision.actionType.toUpperCase() : "DONE";

                streamService.sendObservation(evaluationId, i, observation, currentActivity, "Android App");
                streamService.sendAction(evaluationId, i, actionType,
                        decision.selector, decision.value, decision.reason);

                if ("DONE".equals(actionType)) {
                    steps.add(saveStep(evaluationId, i, observation, "DONE",
                            currentActivity, "Android App", hdScreenshot, aiResponse, "DONE", null, null));
                    streamService.sendStepDone(evaluationId, i, i);
                    break;
                }

                boolean success = executeApkAction(decision, uiElements);

                streamService.sendActionResult(evaluationId, i, success,
                        success ? "Action executee avec succes" : "Action echouee");

                if (success) {
                    consecutiveFailures = 0;
                    history.add("Step " + i + " [" + currentActivity + "]: " + observation
                            + " -> " + actionType + (decision.selector != null ? " " + decision.selector : ""));
                } else {
                    consecutiveFailures++;
                    history.add("Step " + i + " [FAILED]: " + actionType
                            + " " + (decision.selector != null ? decision.selector : ""));
                    if (consecutiveFailures >= 3) {
                        try { appiumDriverService.pressBack(); } catch (Exception ignored) {}
                        history.add("SYSTEM: Auto-recovery BACK after 3 failures");
                        consecutiveFailures = 0;
                    }
                }

                steps.add(saveStep(evaluationId, i, observation,
                        actionType + (success ? "" : " [FAILED]"),
                        currentActivity, "Android App", hdScreenshot, aiResponse,
                        actionType, decision.selector, decision.value));
                streamService.sendStepDone(evaluationId, i, maxSteps);

                Thread.sleep(1500);
            }

            streamService.sendInfo(evaluationId, "Redaction du rapport UX mobile...");
            String finalReport = buildApkFinalReport(evaluation.getDescription(), steps, visitedScreens);

            evaluation.setAiAnalysis(finalReport);
            evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            evaluation.setStatus(Status.COMPLETED);
            evaluation.setLogs(String.join("\n", history));
            evaluationRepository.save(evaluation);

            streamService.sendCompleted(evaluationId, finalReport);
            log.info("[UX-AGENT-APK {}] Completed in {} ms with {} steps",
                    evaluationId, evaluation.getDurationMs(), steps.size());

        } catch (Exception e) {
            log.error("[UX-AGENT-APK {}] Failed: {}", evaluationId, e.getMessage(), e);
            evaluation.setStatus(Status.FAILED);
            evaluation.setErrorMessage(e.getMessage());
            evaluation.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            evaluationRepository.save(evaluation);
            streamService.sendFailed(evaluationId, sanitizeError(e));
        } finally {
            humanInputService.cancel(evaluationId);
            appiumDriverService.deleteSession();
        }
    }

    private static final int AI_SCREENSHOT_WIDTH = 540;
    private int lastRawScreenshotWidth = 0;
    private int lastRawScreenshotHeight = 0;

    private int[] scaleCoords(int aiX, int aiY) {
        int refWidth = lastRawScreenshotWidth > 0 ? lastRawScreenshotWidth : appiumDriverService.getScreenWidth();
        int refHeight = lastRawScreenshotHeight > 0 ? lastRawScreenshotHeight : appiumDriverService.getScreenHeight();
        double scale = (double) refWidth / AI_SCREENSHOT_WIDTH;
        int realX = (int) (aiX * scale);
        int realY = (int) (aiY * scale);
        log.info("[UX-AGENT-APK] Coordinate scaling: AI({},{}) -> Device({},{}) [scale={}, screenshot={}x{}]",
                aiX, aiY, realX, realY, String.format("%.2f", scale), refWidth, refHeight);
        return new int[]{realX, realY};
    }

    private AppiumDriverService.UiElement resolveElement(String selector,
                                                         List<AppiumDriverService.UiElement> uiElements) {
        if (selector == null || uiElements == null || uiElements.isEmpty()) return null;
        try {
            int idx = Integer.parseInt(selector.trim().replaceAll("[^0-9].*$", ""));
            if (idx >= 0 && idx < uiElements.size()) return uiElements.get(idx);
        } catch (Exception ignored) {}
        // Fallback: match by label text
        String s = selector.trim().toLowerCase();
        for (AppiumDriverService.UiElement el : uiElements) {
            if (el.label != null && el.label.toLowerCase().contains(s)) return el;
        }
        return null;
    }

    private boolean executeApkAction(AgentDecision decision,
                                     List<AppiumDriverService.UiElement> uiElements) {
        try {
            String action = decision.actionType.toUpperCase();
            switch (action) {
                case "TAP_ELEMENT" -> {
                    AppiumDriverService.UiElement el = resolveElement(decision.selector, uiElements);
                    if (el == null) {
                        log.warn("[UX-AGENT-APK] TAP_ELEMENT: no element for selector '{}'", decision.selector);
                        return false;
                    }
                    log.info("[UX-AGENT-APK] TAP_ELEMENT \"{}\" at exact ({},{})", el.label, el.centerX, el.centerY);
                    appiumDriverService.tap(el.centerX, el.centerY);
                    Thread.sleep(500);
                    return true;
                }
                case "TAP" -> {
                    String[] coords = decision.selector.split(",");
                    int aiX = Integer.parseInt(coords[0].trim());
                    int aiY = Integer.parseInt(coords[1].trim());
                    int[] real = scaleCoords(aiX, aiY);
                    appiumDriverService.tap(real[0], real[1]);
                    Thread.sleep(500);
                    return true;
                }
                case "SWIPE_UP", "SCROLL" -> {
                    appiumDriverService.swipeUp();
                    Thread.sleep(500);
                    return true;
                }
                case "SWIPE_DOWN" -> {
                    appiumDriverService.swipeDown();
                    Thread.sleep(500);
                    return true;
                }
                case "TYPE", "FILL" -> {
                    // First focus the field: by element index if available, else by coordinates
                    AppiumDriverService.UiElement el = resolveElement(decision.selector, uiElements);
                    if (el != null) {
                        appiumDriverService.tap(el.centerX, el.centerY);
                        Thread.sleep(300);
                    } else if (decision.selector != null && decision.selector.contains(",")) {
                        String[] coords = decision.selector.split(",");
                        int aiX = Integer.parseInt(coords[0].trim());
                        int aiY = Integer.parseInt(coords[1].trim());
                        int[] real = scaleCoords(aiX, aiY);
                        appiumDriverService.tap(real[0], real[1]);
                        Thread.sleep(300);
                    }
                    if (decision.value != null) {
                        appiumDriverService.typeText(decision.value);
                        Thread.sleep(300);
                    }
                    return true;
                }
                case "BACK" -> {
                    appiumDriverService.pressBack();
                    Thread.sleep(500);
                    return true;
                }
                default -> {
                    log.warn("[UX-AGENT-APK] Unknown action: {}", action);
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("[UX-AGENT-APK] Action failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildApkActionPrompt(String currentActivity, List<String> history, int step,
                                         String userDescription, Set<String> visitedScreens,
                                         List<AppiumDriverService.UiElement> uiElements) {
        String historyText = history.isEmpty() ? "Aucune (première ouverture)"
                : String.join("\n", history.subList(Math.max(0, history.size() - 15), history.size()));

        String userContext = (userDescription != null && !userDescription.isBlank())
                ? "\nLe testeur a précisé : " + userDescription : "";

        String screensText = visitedScreens.isEmpty() ? "Aucun encore"
                : String.join(", ", visitedScreens);

        // Liste numérotée des éléments réels détectés sur l'écran (coordonnées exactes)
        StringBuilder elementList = new StringBuilder();
        if (uiElements != null && !uiElements.isEmpty()) {
            for (int idx = 0; idx < uiElements.size(); idx++) {
                AppiumDriverService.UiElement el = uiElements.get(idx);
                elementList.append("  [").append(idx).append("] ")
                        .append(el.editable ? "(champ texte) " : "")
                        .append("\"").append(el.label).append("\"")
                        .append("\n");
            }
        } else {
            elementList.append("  (Aucun élément détecté — utilise TAP avec coordonnées estimées)\n");
        }

        return """
            Tu es un testeur UX expert d'applications mobiles Android.
            Tu explores cette application native de façon méthodique pour évaluer l'expérience utilisateur.%s

            === CONTEXTE ===
            Écran actuel (Activity) : %s
            Étape : %d
            Écrans visités : %s

            === ÉLÉMENTS INTERACTIFS DÉTECTÉS SUR CET ÉCRAN ===
            (Utilise l'index [N] pour taper précisément — c'est BIEN PLUS FIABLE que les coordonnées)
            %s
            === HISTORIQUE RÉCENT ===
            %s

            === RÉPONSE ATTENDUE ===
            Réponds en JSON STRICT uniquement (pas de texte avant/après, pas de ```json) :
            {
              "observation": "Ce que tu vois sur cet écran en 1-2 phrases",
              "action_type": "TAP_ELEMENT | TAP | SWIPE_UP | SWIPE_DOWN | TYPE | BACK | DONE",
              "selector": "index de l'élément (ex: \\"2\\") pour TAP_ELEMENT ; OU x,y pour TAP ; null sinon",
              "value": "texte à saisir si TYPE, sinon null",
              "reason": "Pourquoi tu fais cette action"
            }

            === RÈGLES DE PRIORITÉ ===
            1. PRIORISE TOUJOURS TAP_ELEMENT avec l'index [N] d'un élément de la liste ci-dessus
            2. Si un popup/permission/dialog bloque → TAP_ELEMENT sur "Autoriser"/"OK"/"Accepter"
            3. Explore tous les onglets, menus, boutons de la liste — un par un, sans répéter
            4. Pour un champ texte : TYPE avec selector = index du champ + value = données réalistes
            5. SWIPE_UP si du contenu est caché en bas (rien de nouveau dans la liste)
            6. BACK pour revenir si tu es dans un cul-de-sac
            7. DONE quand tu as exploré tous les écrans principaux
            8. N'utilise TAP (coordonnées) QUE si l'élément voulu n'est PAS dans la liste

            === COORDONNÉES (fallback uniquement) ===
            Si tu dois absolument utiliser TAP, estime (x,y) du CENTRE de l'élément sur le screenshot.
            """.formatted(userContext, currentActivity, step, screensText, elementList, historyText);
    }

    private String buildApkFinalReport(String description, List<UxNavigationStep> steps,
                                        Set<String> visitedScreens) {
        String parcours = steps.stream()
                .map(s -> "Étape " + s.getStepNumber() + " (" + s.getPageUrl() + "): "
                        + s.getObservation() + " → " + s.getActionPerformed())
                .collect(Collectors.joining("\n"));

        String userContext = (description != null && !description.isBlank())
                ? "Contexte du testeur : " + description : "";

        return visionProvider.analyzeText("""
            Tu es un expert UX mobile senior avec 10 ans d'expérience.
            Tu viens d'explorer une application Android native en %d étapes.
            %s

            Écrans visités : %s

            Parcours effectué :
            %s

            Rédige un rapport d'expérience utilisateur complet en français.

            COMMENCE OBLIGATOIREMENT par la note globale. Structure EXACTE :

            ## SCORE GLOBAL : X/10
            Justification en 1-2 phrases.

            ## 1. PREMIÈRE IMPRESSION
            L'app se lance-t-elle rapidement ? L'écran d'accueil est-il clair ?

            ## 2. ERGONOMIE MOBILE
            - Taille des zones tactiles (>= 44dp ?)
            - Espacement entre les éléments cliquables
            - Navigation intuitive (onglets, drawer, bottom nav)
            - Gestes naturels (swipe, pull-to-refresh)

            ## 3. POINTS FORTS
            Ce qui est bien conçu (3-5 points max).

            ## 4. POINTS DE FRICTION
            Ce qui bloque ou frustre (3-5 points max).

            ## 5. PERFORMANCE PERÇUE
            Temps de chargement, animations fluides, réactivité au toucher.

            ## 6. RECOMMANDATIONS
            5-8 suggestions concrètes d'amélioration.

            RÈGLES : Sois concis, chaque section 3-8 lignes max. Écris comme un vrai testeur humain.
            """.formatted(steps.size(), userContext, String.join(", ", visitedScreens), parcours));
    }
}
