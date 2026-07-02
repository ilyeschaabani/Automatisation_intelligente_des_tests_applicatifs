package com.pfe.platform.msexecution.dto;

import lombok.Builder;
import lombok.Data;

/**
 * WebSocket message sent to the frontend during a UX evaluation.
 * Topic: /topic/evaluation/{id}
 *
 * Types:
 *   SCREENSHOT   – base64 JPEG of the current browser screen
 *   THINKING     – Gemini is processing (show spinner)
 *   OBSERVATION  – what the AI sees on the page
 *   ACTION       – action the AI is about to perform
 *   ACTION_RESULT– result of the action (ok or failed)
 *   NEEDS_INPUT  – AI is paused, needs human info
 *   RESUMED      – human answered, AI is continuing
 *   STEP_DONE    – one step finished
 *   COMPLETED    – evaluation done, final report ready
 *   FAILED       – fatal error
 *   INFO         – generic informational message
 */
@Data
@Builder
public class StreamEvent {

    /** One of the type constants listed above */
    private String type;

    /** Human-readable message (displayed in the chat) */
    private String message;

    /** base64-encoded JPEG — only for SCREENSHOT events */
    private String screenshotBase64;

    /** Current step number */
    private Integer step;

    /** CLICK | FILL | SCROLL | NEEDS_INPUT | DONE | SKIP */
    private String actionType;

    /** CSS selector or visible text of the element */
    private String selector;

    /** Value to fill (FILL actions) */
    private String fillValue;

    /** Question asked to the human tester (NEEDS_INPUT) */
    private String question;

    /** Hint / example answer for the human (NEEDS_INPUT) */
    private String hint;

    /** Labels of the fields the tester must fill (NEEDS_INPUT form, e.g. ["Identifiant","Mot de passe"]) */
    private java.util.List<String> fields;

    /** Whether an action succeeded (ACTION_RESULT) */
    private Boolean success;

    /** Current URL when the event was emitted */
    private String currentUrl;

    /** Page title when the event was emitted */
    private String pageTitle;

    /** Vision backend being used: "Ollama (moondream)", "Gemini (2.0-flash)", etc. */
    private String backend;

    // ── Factory helpers ──────────────────────────────────────────────────────

    public static StreamEvent screenshot(byte[] jpeg) {
        return StreamEvent.builder()
                .type("SCREENSHOT")
                .screenshotBase64(java.util.Base64.getEncoder().encodeToString(jpeg))
                .build();
    }

    public static StreamEvent thinking(int step, String url, String title) {
        return StreamEvent.builder()
                .type("THINKING")
                .message("🔍 L'IA analyse la page — étape " + step + "…")
                .step(step)
                .currentUrl(url)
                .pageTitle(title)
                .build();
    }

    public static StreamEvent observation(int step, String observation, String url, String title) {
        return StreamEvent.builder()
                .type("OBSERVATION")
                .message(observation)
                .step(step)
                .currentUrl(url)
                .pageTitle(title)
                .build();
    }

    public static StreamEvent action(int step, String actionType, String selector, String value, String reason) {
        String label = switch (actionType == null ? "" : actionType.toUpperCase()) {
            case "CLICK"    -> "🖱 Clic sur : " + selector;
            case "FILL"     -> "✏️ Saisie dans : " + selector + " → \"" + value + "\"";
            case "SCROLL"   -> "↕ Défilement de la page";
            case "NAVIGATE" -> "🧭 Navigation vers : " + selector;
            case "BACK"     -> "⬅ Retour à la page précédente";
            case "DONE"     -> "✅ Exploration terminée";
            default         -> "⚡ Action : " + actionType;
        };
        return StreamEvent.builder()
                .type("ACTION")
                .message(label + (reason != null ? "\n💭 " + reason : ""))
                .step(step)
                .actionType(actionType)
                .selector(selector)
                .fillValue(value)
                .build();
    }

    public static StreamEvent actionResult(int step, boolean success, String message) {
        return StreamEvent.builder()
                .type("ACTION_RESULT")
                .message(success ? "✔ " + message : "⚠ " + message)
                .step(step)
                .success(success)
                .build();
    }

    public static StreamEvent needsInput(int step, String question, String hint) {
        return needsInput(step, question, hint, null);
    }

    public static StreamEvent needsInput(int step, String question, String hint, java.util.List<String> fields) {
        return StreamEvent.builder()
                .type("NEEDS_INPUT")
                .message("⏸ **Intervention humaine requise**\n" + question)
                .step(step)
                .question(question)
                .hint(hint)
                .fields(fields)
                .build();
    }

    public static StreamEvent resumed(String humanAnswer) {
        return StreamEvent.builder()
                .type("RESUMED")
                .message("▶ Reprise — informations reçues, je continue le test…")
                .build();
    }

    public static StreamEvent stepDone(int step, int total) {
        return StreamEvent.builder()
                .type("STEP_DONE")
                .message("Étape " + step + "/" + total + " terminée")
                .step(step)
                .build();
    }

    public static StreamEvent completed(String report) {
        return StreamEvent.builder()
                .type("COMPLETED")
                .message("✅ Évaluation terminée ! Rapport UX généré.")
                .screenshotBase64(report) // reuse field to pass the report text
                .build();
    }

    public static StreamEvent failed(String error) {
        return StreamEvent.builder()
                .type("FAILED")
                .message("❌ Erreur : " + error)
                .build();
    }

    public static StreamEvent info(String message) {
        return StreamEvent.builder()
                .type("INFO")
                .message(message)
                .build();
    }

    public static StreamEvent backendInfo(String backend) {
        return StreamEvent.builder()
                .type("BACKEND_INFO")
                .message("Moteur IA : " + backend)
                .backend(backend)
                .build();
    }

    // ── Test fonctionnel ──────────────────────────────────────────────────────

    public static StreamEvent formDetected(int step, String formLabel, int fieldCount) {
        return StreamEvent.builder()
                .type("FORM_DETECTED")
                .message("🧪 Formulaire détecté : « " + formLabel + " » (" + fieldCount + " champ(s)) — lancement des tests fonctionnels")
                .step(step)
                .build();
    }

    public static StreamEvent testCase(int step, String scenario, String description) {
        return StreamEvent.builder()
                .type("TEST_CASE")
                .message("▶ Cas de test [" + scenario + "] : " + description)
                .step(step)
                .actionType(scenario)
                .build();
    }

    public static StreamEvent testVerdict(int step, FunctionalTestResult r) {
        String icon = switch (r.getStatus()) {
            case PASS -> "✅";
            case FAIL -> "🔴";
            case WARN -> "🟠";
            case NEEDS_REVIEW -> "⏸";
        };
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" [").append(r.getScenario()).append("] ")
          .append(r.getStatus());
        if (r.getTargetField() != null) sb.append(" — champ « ").append(r.getTargetField()).append(" »");
        sb.append("\nAttendu : ").append(r.getExpected());
        sb.append("\nObservé : ").append(r.getObserved());
        if (r.getEvidence() != null && r.getEvidence().contains("Appels réseau")) {
            String net = r.getEvidence().replaceAll(".*?(Appels réseau[^|]*?).*", "$1").trim();
            if (!net.isEmpty()) sb.append("\nRéseau : ").append(net);
        }
        if (r.getStatus() == FunctionalTestResult.Status.FAIL) {
            sb.append("\nSévérité : ").append(r.getSeverity());
        }
        return StreamEvent.builder()
                .type("TEST_VERDICT")
                .message(sb.toString())
                .step(step)
                .actionType(r.getScenario())
                .success(r.getStatus() == FunctionalTestResult.Status.PASS)
                .screenshotBase64(r.getScreenshotBase64())
                .build();
    }
}
