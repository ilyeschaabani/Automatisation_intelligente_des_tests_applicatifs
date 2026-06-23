package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.msexecution.dto.FormSchema;
import com.pfe.platform.msexecution.dto.FunctionalTestResult;
import com.pfe.platform.msexecution.dto.FunctionalTestResult.Severity;
import com.pfe.platform.msexecution.dto.FunctionalTestResult.Status;
import com.pfe.platform.msexecution.dto.StreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moteur de test fonctionnel pour applications web.
 *
 * Principe : oracle déterministe (contraintes HTML5 + état DOM) plutôt que devinette IA.
 * Pour chaque formulaire détecté, on extrait son schéma réel, on dérive des cas de test
 * (soumission vide, format invalide, bornes, happy path) et on VÉRIFIE le résultat
 * attendu contre l'observé. L'IA ne sert qu'à générer des données valides réalistes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebFunctionalTester {

    private final VisionProvider visionProvider;
    private final EvaluationStreamService streamService;
    private final HumanInputService humanInputService;
    private static final ObjectMapper mapper = new ObjectMapper();

    private record NetworkCall(String url, int status, String type) {
        boolean isSuccess()     { return status >= 200 && status < 300; }
        boolean isClientError() { return status >= 400 && status < 500; }
        boolean isServerError() { return status >= 500; }
    }

    // ── Interception réseau (XHR + fetch) ────────────────────────────────────

    private static final String INJECT_NETWORK_JS = """
        window.__netCalls = [];
        (function(){
          if (window.__netPatched) return;
          window.__netPatched = true;
          var origXHR = window.XMLHttpRequest;
          window.XMLHttpRequest = function() {
            var xhr = new origXHR();
            var _url = '';
            var origOpen = xhr.open;
            xhr.open = function(m, u) { _url = u; return origOpen.apply(this, arguments); };
            xhr.addEventListener('load', function() {
              window.__netCalls.push({url: _url, status: this.status, type: 'xhr'});
            });
            xhr.addEventListener('error', function() {
              window.__netCalls.push({url: _url, status: 0, type: 'xhr-error'});
            });
            return xhr;
          };
          if (window.fetch) {
            var origFetch = window.fetch;
            window.fetch = function(input, opts) {
              var url = (typeof input === 'string') ? input : (input && input.url) ? input.url : String(input);
              return origFetch.apply(this, arguments).then(function(res) {
                window.__netCalls.push({url: url, status: res.status, type: 'fetch'});
                return res;
              }).catch(function(err) {
                window.__netCalls.push({url: url, status: 0, type: 'fetch-error'});
                throw err;
              });
            };
          }
        })();
        window.__netCalls = [];
        """;

    private static final String READ_NETWORK_JS = "return JSON.stringify(window.__netCalls || []);";

    private void injectNetworkInterceptor(WebDriver driver) {
        try {
            ((JavascriptExecutor) driver).executeScript(INJECT_NETWORK_JS);
        } catch (Exception e) {
            log.debug("[FUNC-TEST] Network interceptor inject failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<NetworkCall> readNetworkCalls(WebDriver driver) {
        List<NetworkCall> calls = new ArrayList<>();
        try {
            String json = (String) ((JavascriptExecutor) driver).executeScript(READ_NETWORK_JS);
            if (json == null || json.isBlank()) return calls;
            JsonNode arr = mapper.readTree(json);
            for (JsonNode n : arr) {
                calls.add(new NetworkCall(
                    n.path("url").asText(""),
                    n.path("status").asInt(0),
                    n.path("type").asText("?")
                ));
            }
        } catch (Exception e) {
            log.debug("[FUNC-TEST] Read network calls failed: {}", e.getMessage());
        }
        return calls;
    }

    private String networkSummary(List<NetworkCall> calls) {
        if (calls.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Appels réseau: ");
        calls.forEach(c -> sb.append("[").append(c.type()).append(" ").append(c.status()).append("] "));
        return sb.toString().trim();
    }

    // ── Détection des formulaires ─────────────────────────────────────────────

    private static final String DETECT_FORMS_JS = """
        var forms = [];
        document.querySelectorAll('form').forEach(function(form, fi){
          var fields = [];
          form.querySelectorAll('input, textarea, select').forEach(function(el){
            var type = (el.type||'').toLowerCase();
            if (['hidden','submit','button','reset','image','file'].includes(type)) return;
            var label = '';
            if (el.labels && el.labels[0]) label = el.labels[0].innerText;
            if (!label) label = el.getAttribute('aria-label') || el.placeholder || '';
            fields.push({
              name: el.name || el.id || '',
              label: (label||'').trim().substring(0,60),
              tag: el.tagName.toLowerCase(),
              type: type,
              required: el.required || el.getAttribute('aria-required')==='true',
              pattern: el.pattern || '',
              minLength: el.minLength > 0 ? el.minLength : null,
              maxLength: el.maxLength > 0 ? el.maxLength : null,
              min: el.min || '',
              max: el.max || '',
              options: el.tagName.toLowerCase()==='select' ? Array.from(el.options).map(function(o){return o.value;}) : []
            });
          });
          var submit = form.querySelector("[type=submit], button:not([type=button])");
          forms.push({
            index: fi,
            id: form.id || '',
            name: form.getAttribute('name') || '',
            action: form.action || '',
            method: (form.method || 'get').toUpperCase(),
            hasSubmit: !!submit,
            fields: fields
          });
        });
        return JSON.stringify(forms);
        """;

    public List<FormSchema> detectForms(WebDriver driver) {
        List<FormSchema> result = new ArrayList<>();
        try {
            String json = (String) ((JavascriptExecutor) driver).executeScript(DETECT_FORMS_JS);
            if (json == null || json.isBlank()) return result;
            JsonNode arr = mapper.readTree(json);
            for (JsonNode node : arr) {
                FormSchema form = mapper.treeToValue(node, FormSchema.class);
                // On ne teste que les formulaires avec au moins un champ testable
                if (form.getFields() != null && !form.getFields().isEmpty()) {
                    result.add(form);
                }
            }
        } catch (Exception e) {
            log.warn("[FUNC-TEST] Form detection failed: {}", e.getMessage());
        }
        return result;
    }

    // ── Batterie de tests sur un formulaire ───────────────────────────────────

    /**
     * Exécute la batterie complète de tests fonctionnels sur un formulaire et
     * diffuse chaque verdict en temps réel. Renvoie la liste des résultats.
     */
    public List<FunctionalTestResult> testForm(WebDriver driver, FormSchema form,
                                               Long evaluationId, int step, boolean supervised) {
        List<FunctionalTestResult> results = new ArrayList<>();
        String pageUrl = driver.getCurrentUrl();

        streamService.send(evaluationId, StreamEvent.formDetected(step, form.label(), form.getFields().size()));

        // Données valides générées par l'IA (oracle pour le happy path + base des cas négatifs)
        Map<String, String> validData = generateValidData(form);

        boolean hasRequired = form.getFields().stream().anyMatch(FormSchema.FormField::isRequired);

        // 1) Soumission vide → les champs requis doivent être rejetés
        if (hasRequired) {
            record(runEmptySubmit(driver, form, evaluationId, step, pageUrl), supervised, evaluationId, step, results);
        }

        // 2) Format invalide → un champ à la fois
        for (FormSchema.FormField field : form.getFields()) {
            if (field.hasFormatConstraint()) {
                record(runInvalidFormat(driver, form, field, validData, evaluationId, step, pageUrl),
                        supervised, evaluationId, step, results);
            }
        }

        // 3) Bornes (longueur / min-max numérique)
        for (FormSchema.FormField field : form.getFields()) {
            if (field.hasBoundary()) {
                record(runBoundary(driver, form, field, validData, evaluationId, step, pageUrl),
                        supervised, evaluationId, step, results);
            }
        }

        // 4) Happy path → toutes données valides
        record(runHappyPath(driver, form, validData, evaluationId, step, pageUrl),
                supervised, evaluationId, step, results);

        return results;
    }

    /**
     * Applique le HITL si nécessaire (mode supervisé + verdict incertain), diffuse le verdict
     * final, et enregistre le résultat. C'est le point central de validation humaine.
     */
    private void record(FunctionalTestResult r, boolean supervised, Long evaluationId, int step,
                        List<FunctionalTestResult> results) {
        if (supervised && r.needsHuman()) {
            askTesterToValidate(r, evaluationId, step);
        }
        streamService.send(evaluationId, StreamEvent.testVerdict(step, r));
        results.add(r);
    }

    /**
     * Escalade un verdict douteux au testeur : il tranche PASS / FAIL / IGNORE.
     * Le testeur devient l'oracle quand l'oracle automatique ne suffit pas.
     */
    private void askTesterToValidate(FunctionalTestResult r, Long evaluationId, int step) {
        String question = "Test « " + r.getScenario() + " »"
                + (r.getTargetField() != null ? " sur « " + r.getTargetField() + " »" : "")
                + " — verdict incertain.\nAttendu : " + r.getExpected()
                + "\nObservé : " + r.getObserved()
                + "\n\nTon verdict ? Réponds : OK (correct) / BUG (problème) / IGNORE (faux positif)";
        streamService.sendNeedsInput(evaluationId, step, question, "OK / BUG / IGNORE");
        try {
            String answer = humanInputService.waitForInput(evaluationId, java.time.Duration.ofMinutes(5));
            streamService.sendResumed(evaluationId, answer);
            String a = answer == null ? "" : answer.trim().toLowerCase();
            if (a.contains("bug") || a.contains("fail") || a.contains("ko") || a.contains("problème") || a.contains("probleme")) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.HIGH);
                r.setObserved(r.getObserved() + " [Confirmé BUG par le testeur]");
            } else if (a.contains("ignore") || a.contains("skip") || a.contains("faux")) {
                r.setStatus(Status.WARN);
                r.setObserved(r.getObserved() + " [Marqué faux positif par le testeur]");
            } else {
                r.setStatus(Status.PASS);
                r.setObserved(r.getObserved() + " [Validé correct par le testeur]");
            }
            r.setConfidence(1.0);
        } catch (Exception e) {
            log.info("[FUNC-TEST] No human validation (timeout/cancel) — verdict left as-is: {}", e.getMessage());
        }
    }

    // ── Scénarios ─────────────────────────────────────────────────────────────

    private FunctionalTestResult runEmptySubmit(WebDriver driver, FormSchema form,
                                                Long evaluationId, int step, String pageUrl) {
        FunctionalTestResult r = FunctionalTestResult.of(form.label(), "EMPTY_SUBMIT", null);
        r.setExpected("Les champs requis affichent une erreur et le formulaire n'est PAS soumis.");
        r.setInputData("(tous les champs vides)");
        streamService.send(evaluationId, StreamEvent.testCase(step, "EMPTY_SUBMIT",
                "soumettre le formulaire « " + form.label() + " » vide"));
        try {
            reNavigate(driver, pageUrl);
            clearForm(driver, form.getIndex());
            injectNetworkInterceptor(driver);
            String before = stateSignature(driver);
            submitForm(driver, form.getIndex());
            sleep(1400);

            boolean blocked = requiredFieldsBlocked(driver, form);
            List<String> domErrors = detectErrors(driver, form.getIndex());
            boolean stateChanged = !stateSignature(driver).equals(before);
            List<NetworkCall> netCalls = readNetworkCalls(driver);
            boolean serverAccepted = netCalls.stream().anyMatch(NetworkCall::isSuccess);
            boolean serverRejected = netCalls.stream().anyMatch(NetworkCall::isClientError);

            r.setScreenshotBase64(snapshot(driver));
            String netSummary = networkSummary(netCalls);
            r.setEvidence("blocked=" + blocked + " | domErrors=" + domErrors
                    + " | navigated=" + stateChanged
                    + (netSummary != null ? " | " + netSummary : ""));

            if (!stateChanged && (blocked || !domErrors.isEmpty())) {
                r.setStatus(Status.PASS);
                r.setObserved("Soumission bloquée, validation des champs requis active. " + summarize(domErrors));
            } else if (stateChanged || serverAccepted) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.HIGH);
                r.setObserved("⚠ Le formulaire a été soumis alors que des champs requis étaient vides"
                        + (serverAccepted ? " (serveur a accepté la requête !)" : " (navigation détectée)."));
            } else if (serverRejected) {
                r.setStatus(Status.WARN);
                r.setSeverity(Severity.MEDIUM);
                r.setObserved("Le serveur a rejeté la requête (4xx) mais la validation client-side était absente. "
                        + netSummary);
            } else {
                r.setStatus(Status.NEEDS_REVIEW);
                r.setConfidence(0.4);
                r.setObserved("Aucune navigation mais aucune erreur détectée automatiquement — vérification humaine conseillée.");
            }
        } catch (Exception e) {
            fail(r, e);
        }
        return r;
    }

    private FunctionalTestResult runInvalidFormat(WebDriver driver, FormSchema form, FormSchema.FormField field,
                                                  Map<String, String> validData, Long evaluationId, int step, String pageUrl) {
        FunctionalTestResult r = FunctionalTestResult.of(form.label(), "INVALID_FORMAT", field.displayName());
        String invalid = invalidValueFor(field);
        r.setExpected("Le champ « " + field.displayName() + " » rejette une valeur de format invalide.");
        r.setInputData(field.getName() + " = \"" + invalid + "\"");
        streamService.send(evaluationId, StreamEvent.testCase(step, "INVALID_FORMAT",
                "saisir un format invalide dans « " + field.displayName() + " »"));
        try {
            reNavigate(driver, pageUrl);
            fillAllValid(driver, form, validData);
            fillField(driver, form.getIndex(), field.getName(), invalid);
            injectNetworkInterceptor(driver);
            String before = stateSignature(driver);
            submitForm(driver, form.getIndex());
            sleep(1400);

            Map<String, Object> validity = fieldValidity(driver, form.getIndex(), field.getName());
            List<String> domErrors = detectErrors(driver, form.getIndex());
            boolean stateChanged = !stateSignature(driver).equals(before);
            boolean htmlRejected = validity != null && Boolean.FALSE.equals(validity.get("valid"));
            List<NetworkCall> netCalls = readNetworkCalls(driver);
            boolean serverAccepted = netCalls.stream().anyMatch(NetworkCall::isSuccess);
            boolean serverRejected = netCalls.stream().anyMatch(NetworkCall::isClientError);

            r.setScreenshotBase64(snapshot(driver));
            String netSummary = networkSummary(netCalls);
            r.setEvidence("validity=" + validity + " | domErrors=" + domErrors
                    + " | navigated=" + stateChanged
                    + (netSummary != null ? " | " + netSummary : ""));

            if (!stateChanged && (htmlRejected || !domErrors.isEmpty())) {
                r.setStatus(Status.PASS);
                r.setObserved("Format invalide correctement rejeté (HTML5/DOM). " + summarize(domErrors));
            } else if (serverRejected && !stateChanged) {
                r.setStatus(Status.WARN);
                r.setSeverity(Severity.MEDIUM);
                r.setObserved("Validation client absente, mais le serveur a rejeté (4xx). " + netSummary);
            } else if (stateChanged || serverAccepted) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.HIGH);
                r.setObserved("⚠ Une valeur de format invalide a été acceptée"
                        + (serverAccepted ? " (serveur a renvoyé 2xx !)" : " (navigation détectée).")
                        + " Validation de format manquante.");
            } else {
                r.setStatus(Status.NEEDS_REVIEW);
                r.setConfidence(0.45);
                r.setObserved("Pas de rejet détecté automatiquement — vérification humaine conseillée.");
            }
        } catch (Exception e) {
            fail(r, e);
        }
        return r;
    }

    private FunctionalTestResult runBoundary(WebDriver driver, FormSchema form, FormSchema.FormField field,
                                             Map<String, String> validData, Long evaluationId, int step, String pageUrl) {
        FunctionalTestResult r = FunctionalTestResult.of(form.label(), "BOUNDARY", field.displayName());
        String outOfBounds = boundaryViolatingValue(field);
        r.setExpected("Le champ « " + field.displayName() + " » rejette une valeur hors limites.");
        r.setInputData(field.getName() + " = \"" + outOfBounds + "\"");
        streamService.send(evaluationId, StreamEvent.testCase(step, "BOUNDARY",
                "tester les limites de « " + field.displayName() + " »"));
        try {
            reNavigate(driver, pageUrl);
            fillAllValid(driver, form, validData);
            fillField(driver, form.getIndex(), field.getName(), outOfBounds);
            injectNetworkInterceptor(driver);
            String before = stateSignature(driver);
            submitForm(driver, form.getIndex());
            sleep(1200);

            Map<String, Object> validity = fieldValidity(driver, form.getIndex(), field.getName());
            List<String> domErrors = detectErrors(driver, form.getIndex());
            boolean stateChanged = !stateSignature(driver).equals(before);
            boolean htmlRejected = validity != null && Boolean.FALSE.equals(validity.get("valid"));
            List<NetworkCall> netCalls = readNetworkCalls(driver);
            boolean serverAccepted = netCalls.stream().anyMatch(NetworkCall::isSuccess);
            boolean serverRejected = netCalls.stream().anyMatch(NetworkCall::isClientError);

            r.setScreenshotBase64(snapshot(driver));
            String netSummary = networkSummary(netCalls);
            r.setEvidence("validity=" + validity + " | domErrors=" + domErrors
                    + " | navigated=" + stateChanged
                    + (netSummary != null ? " | " + netSummary : ""));

            if (!stateChanged && (htmlRejected || !domErrors.isEmpty())) {
                r.setStatus(Status.PASS);
                r.setObserved("Valeur hors limites correctement rejetée. " + summarize(domErrors));
            } else if (serverRejected && !stateChanged) {
                r.setStatus(Status.WARN);
                r.setSeverity(Severity.LOW);
                r.setObserved("Contrainte non vérifiée côté client, mais serveur a rejeté (4xx). " + netSummary);
            } else if (stateChanged || serverAccepted) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.MEDIUM);
                r.setObserved("⚠ Une valeur hors limites a été acceptée"
                        + (serverAccepted ? " (serveur 2xx)" : " (navigation).")
                        + " Contrainte de bornes non appliquée.");
            } else {
                r.setStatus(Status.WARN);
                r.setConfidence(0.5);
                r.setObserved("Comportement ambigu sur les bornes — à vérifier.");
            }
        } catch (Exception e) {
            fail(r, e);
        }
        return r;
    }

    private FunctionalTestResult runHappyPath(WebDriver driver, FormSchema form,
                                              Map<String, String> validData, Long evaluationId, int step, String pageUrl) {
        FunctionalTestResult r = FunctionalTestResult.of(form.label(), "HAPPY_PATH", null);
        r.setExpected("Soumission réussie : navigation ou message de succès, AUCUNE erreur de validation.");
        r.setInputData(summarizeData(validData));
        streamService.send(evaluationId, StreamEvent.testCase(step, "HAPPY_PATH",
                "soumettre « " + form.label() + " » avec des données valides"));
        try {
            reNavigate(driver, pageUrl);
            fillAllValid(driver, form, validData);
            injectNetworkInterceptor(driver);
            String before = stateSignature(driver);
            submitForm(driver, form.getIndex());
            sleep(1800);

            List<String> domErrors = detectErrors(driver, form.getIndex());
            boolean stateChanged = !stateSignature(driver).equals(before);
            List<NetworkCall> netCalls = readNetworkCalls(driver);
            boolean serverAccepted = netCalls.stream().anyMatch(NetworkCall::isSuccess);
            boolean serverError    = netCalls.stream().anyMatch(NetworkCall::isServerError);
            boolean serverRejected = netCalls.stream().anyMatch(NetworkCall::isClientError);
            String netSummary = networkSummary(netCalls);

            r.setScreenshotBase64(snapshot(driver));
            r.setEvidence("domErrors=" + domErrors + " | navigated=" + stateChanged
                    + (netSummary != null ? " | " + netSummary : ""));

            if (serverError) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.CRITICAL);
                r.setObserved("⚠ Erreur serveur (5xx) sur une soumission valide — bug côté serveur. " + netSummary);
            } else if (serverRejected && domErrors.isEmpty()) {
                r.setStatus(Status.FAIL);
                r.setSeverity(Severity.HIGH);
                r.setObserved("⚠ Serveur a rejeté (4xx) des données valides — règle métier non documentée ou bug de validation. " + netSummary);
            } else if (serverRejected && !domErrors.isEmpty()) {
                r.setStatus(Status.NEEDS_REVIEW);
                r.setConfidence(0.45);
                r.setObserved("Serveur a rejeté (4xx) + erreurs DOM — règle métier (email déjà pris…) ? " + summarize(domErrors));
            } else if ((domErrors.isEmpty() && stateChanged) || serverAccepted) {
                r.setStatus(Status.PASS);
                r.setObserved("Données valides acceptées"
                        + (serverAccepted ? " (serveur 2xx)" : "")
                        + (stateChanged ? ", état de la page modifié." : " — réponse AJAX reçue sans navigation.")
                );
            } else if (!domErrors.isEmpty()) {
                r.setStatus(Status.NEEDS_REVIEW);
                r.setConfidence(0.4);
                r.setObserved("Erreurs DOM sur données valides : " + summarize(domErrors)
                        + " (règle métier ? bug ? — vérification humaine conseillée).");
            } else {
                r.setStatus(Status.WARN);
                r.setConfidence(0.5);
                r.setObserved("Aucune erreur, aucune navigation, aucun appel réseau capturé — soumission silencieuse non résolue.");
            }
        } catch (Exception e) {
            fail(r, e);
        }
        return r;
    }

    // ── Génération de données valides (IA) ────────────────────────────────────

    private Map<String, String> generateValidData(FormSchema form) {
        Map<String, String> data = new LinkedHashMap<>();
        // Défauts déterministes (robustesse : on a toujours quelque chose même si l'IA échoue)
        for (FormSchema.FormField f : form.getFields()) {
            data.put(f.getName(), defaultValidValue(f));
        }
        try {
            StringBuilder fieldDesc = new StringBuilder();
            for (FormSchema.FormField f : form.getFields()) {
                fieldDesc.append("- ").append(f.getName())
                        .append(" (type=").append(f.getType())
                        .append(f.getLabel() != null && !f.getLabel().isBlank() ? ", label=" + f.getLabel() : "")
                        .append(")\n");
            }
            String prompt = """
                Tu génères des données de test VALIDES et réalistes pour un formulaire web.
                Champs :
                %s
                Réponds en JSON STRICT uniquement (pas de texte autour, pas de ```), format {"nomChamp":"valeur"}.
                Utilise des valeurs réalistes et cohérentes (email valide, téléphone plausible, etc.).
                """.formatted(fieldDesc);
            String resp = visionProvider.analyzeText(prompt);
            if (resp != null && !resp.isBlank()) {
                String json = resp.replaceAll("(?s)```json", "").replaceAll("(?s)```", "").trim();
                int s = json.indexOf('{'), e = json.lastIndexOf('}');
                if (s >= 0 && e > s) {
                    JsonNode node = mapper.readTree(json.substring(s, e + 1));
                    for (FormSchema.FormField f : form.getFields()) {
                        JsonNode v = node.get(f.getName());
                        if (v != null && !v.asText().isBlank()) {
                            data.put(f.getName(), v.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[FUNC-TEST] Valid-data generation fell back to defaults: {}", e.getMessage());
        }
        return data;
    }

    private String defaultValidValue(FormSchema.FormField f) {
        String t = f.getType() == null ? "text" : f.getType().toLowerCase();
        return switch (t) {
            case "email" -> "test.user@example.com";
            case "tel" -> "+33612345678";
            case "url" -> "https://example.com";
            case "number" -> nonNullOr(f.getMin(), "42");
            case "date" -> "2000-01-01";
            case "password" -> "Test1234!";
            case "checkbox", "radio" -> "true";
            default -> {
                if (f.getOptions() != null && !f.getOptions().isEmpty()) yield f.getOptions().get(0);
                yield "Test " + (f.getName() == null ? "valeur" : f.getName());
            }
        };
    }

    private String invalidValueFor(FormSchema.FormField f) {
        String t = f.getType() == null ? "text" : f.getType().toLowerCase();
        return switch (t) {
            case "email" -> "pas-un-email";
            case "url" -> "pas une url";
            case "tel" -> "abcdef";
            case "number" -> "abc";
            default -> {
                if (f.getPattern() != null && !f.getPattern().isBlank()) yield "###invalid###";
                yield "x"; // valeur trop courte / générique
            }
        };
    }

    private String boundaryViolatingValue(FormSchema.FormField f) {
        if (f.getMinLength() != null && f.getMinLength() > 1) {
            return "a".repeat(f.getMinLength() - 1); // trop court
        }
        if (f.getMaxLength() != null && f.getMaxLength() > 0) {
            return "a".repeat(f.getMaxLength() + 5); // trop long
        }
        if (f.getMin() != null && !f.getMin().isBlank()) {
            try { return String.valueOf(Long.parseLong(f.getMin()) - 1); } catch (Exception ignored) {}
        }
        if (f.getMax() != null && !f.getMax().isBlank()) {
            try { return String.valueOf(Long.parseLong(f.getMax()) + 1); } catch (Exception ignored) {}
        }
        return "0";
    }

    // ── Opérations DOM (re-synchronisées à chaque appel — anti stale element) ──

    private void fillField(WebDriver driver, int formIndex, String fieldName, String value) {
        ((JavascriptExecutor) driver).executeScript("""
            var form = document.querySelectorAll('form')[arguments[0]];
            if (!form) return 'NO_FORM';
            var el = form.querySelector("[name='"+arguments[1]+"']") || document.getElementById(arguments[1]);
            if (!el) return 'NO_FIELD';
            var tag = el.tagName.toLowerCase();
            try { el.focus(); } catch(e){}
            if (tag === 'select') {
              el.value = arguments[2];
            } else {
              var proto = tag === 'textarea' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
              var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
              setter.call(el, arguments[2]);
            }
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
            el.dispatchEvent(new Event('blur', {bubbles:true}));
            return 'OK';
            """, formIndex, fieldName, value);
    }

    private void fillAllValid(WebDriver driver, FormSchema form, Map<String, String> validData) {
        for (FormSchema.FormField f : form.getFields()) {
            String v = validData.getOrDefault(f.getName(), defaultValidValue(f));
            fillField(driver, form.getIndex(), f.getName(), v);
        }
    }

    private void clearForm(WebDriver driver, int formIndex) {
        ((JavascriptExecutor) driver).executeScript("""
            var form = document.querySelectorAll('form')[arguments[0]];
            if (!form) return;
            form.querySelectorAll('input, textarea, select').forEach(function(el){
              var type=(el.type||'').toLowerCase();
              if (['hidden','submit','button','reset','image'].includes(type)) return;
              var proto = el.tagName.toLowerCase()==='textarea' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
              try {
                var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                setter.call(el, '');
              } catch(e){ el.value=''; }
              el.dispatchEvent(new Event('input', {bubbles:true}));
              el.dispatchEvent(new Event('change', {bubbles:true}));
            });
            """, formIndex);
    }

    private void submitForm(WebDriver driver, int formIndex) {
        ((JavascriptExecutor) driver).executeScript("""
            var form = document.querySelectorAll('form')[arguments[0]];
            if (!form) return;
            var btn = form.querySelector("[type=submit], button:not([type=button])");
            if (btn) { btn.click(); }
            else if (form.requestSubmit) { form.requestSubmit(); }
            else { form.submit(); }
            """, formIndex);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldValidity(WebDriver driver, int formIndex, String fieldName) {
        try {
            Object res = ((JavascriptExecutor) driver).executeScript("""
                var form = document.querySelectorAll('form')[arguments[0]];
                if (!form) return null;
                var el = form.querySelector("[name='"+arguments[1]+"']") || document.getElementById(arguments[1]);
                if (!el || !el.validity) return null;
                var v = el.validity;
                return {
                  valid: v.valid, valueMissing: v.valueMissing, typeMismatch: v.typeMismatch,
                  patternMismatch: v.patternMismatch, tooLong: v.tooLong, tooShort: v.tooShort,
                  rangeOverflow: v.rangeOverflow, rangeUnderflow: v.rangeUnderflow,
                  message: el.validationMessage || ''
                };
                """, formIndex, fieldName);
            return (Map<String, Object>) res;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean requiredFieldsBlocked(WebDriver driver, FormSchema form) {
        for (FormSchema.FormField f : form.getFields()) {
            if (!f.isRequired()) continue;
            Map<String, Object> v = fieldValidity(driver, form.getIndex(), f.getName());
            if (v != null && Boolean.TRUE.equals(v.get("valueMissing"))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> detectErrors(WebDriver driver, int formIndex) {
        try {
            Object res = ((JavascriptExecutor) driver).executeScript("""
                var form = document.querySelectorAll('form')[arguments[0]];
                var scope = form || document;
                var sel = "[aria-invalid='true'], .error, .invalid, .is-invalid, .field-error, .form-error," +
                          " [role='alert'], .text-danger, .Mui-error, .invalid-feedback, .errorMessage";
                var out = [];
                scope.querySelectorAll(sel).forEach(function(e){
                  if (e.offsetParent === null) return;
                  var t = (e.innerText || e.textContent || '').trim();
                  if (t) out.push(t.substring(0,120));
                  else if (e.getAttribute('aria-invalid')==='true') out.push('[champ marqué invalide]');
                });
                return out;
                """, formIndex);
            if (res instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) out.add(String.valueOf(o));
                return out;
            }
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private String stateSignature(WebDriver driver) {
        try {
            Object res = ((JavascriptExecutor) driver).executeScript(
                "return location.href + '||forms=' + document.querySelectorAll('form').length;");
            return String.valueOf(res);
        } catch (Exception e) {
            return driver.getCurrentUrl();
        }
    }

    private void reNavigate(WebDriver driver, String url) {
        try {
            if (!driver.getCurrentUrl().equals(url)) {
                driver.get(url);
                sleep(1200);
            }
        } catch (Exception ignored) {}
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private String snapshot(WebDriver driver) {
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            return java.util.Base64.getEncoder().encodeToString(png);
        } catch (Exception e) {
            return null;
        }
    }

    private void fail(FunctionalTestResult r, Exception e) {
        r.setStatus(Status.WARN);
        r.setConfidence(0.3);
        r.setObserved("Erreur technique pendant le test : " + e.getMessage());
        log.warn("[FUNC-TEST] Scenario error: {}", e.getMessage());
    }

    private String summarize(List<String> errors) {
        if (errors == null || errors.isEmpty()) return "";
        return "Messages : " + String.join(" | ", errors.subList(0, Math.min(3, errors.size())));
    }

    private String summarizeData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        data.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
        return sb.length() > 200 ? sb.substring(0, 200) : sb.toString();
    }

    private String nonNullOr(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
