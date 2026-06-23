package com.pfe.platform.msexecution.dto;

/**
 * Verdict d'un cas de test fonctionnel : précondition → action → attendu → observé → statut.
 * Chaque verdict porte une preuve (screenshot) et un niveau de confiance qui décide
 * si on escalade au testeur humain.
 */
public class FunctionalTestResult {

    public enum Status { PASS, FAIL, WARN, NEEDS_REVIEW }
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    private String formLabel;
    private String scenario;        // EMPTY_SUBMIT | INVALID_FORMAT | BOUNDARY | REQUIRED_MISSING | HAPPY_PATH | CUSTOM
    private String targetField;     // champ ciblé (null pour les scénarios globaux)
    private String inputData;       // données injectées (résumé lisible)
    private String expected;        // comportement attendu
    private String observed;        // comportement observé
    private Status status;
    private Severity severity;
    private double confidence;      // 0.0–1.0 ; < seuil ⇒ escalade humaine
    private String screenshotBase64;
    private String evidence;        // extrait DOM/validity/réseau qui justifie le verdict

    public FunctionalTestResult() {}

    public static FunctionalTestResult of(String formLabel, String scenario, String targetField) {
        FunctionalTestResult r = new FunctionalTestResult();
        r.formLabel = formLabel;
        r.scenario = scenario;
        r.targetField = targetField;
        r.confidence = 1.0;
        r.severity = Severity.MEDIUM;
        return r;
    }

    public boolean isFailure() { return status == Status.FAIL; }
    public boolean needsHuman() { return status == Status.NEEDS_REVIEW || confidence < 0.6; }

    public String getFormLabel() { return formLabel; }
    public void setFormLabel(String formLabel) { this.formLabel = formLabel; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }
    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getObserved() { return observed; }
    public void setObserved(String observed) { this.observed = observed; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getScreenshotBase64() { return screenshotBase64; }
    public void setScreenshotBase64(String screenshotBase64) { this.screenshotBase64 = screenshotBase64; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
}
