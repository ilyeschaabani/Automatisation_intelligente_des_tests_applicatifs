package com.pfe.platform.ms_gestion.config;

import com.pfe.platform.ms_gestion.entity.*;
import com.pfe.platform.ms_gestion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
// @Component  // DÉSACTIVÉ : ne plus injecter de données de sécurité fictives.
//             Seuls les vrais scans (Semgrep / OWASP ZAP / Dependency-Check) peuplent la base.
@RequiredArgsConstructor
public class SecurityDataSeeder implements CommandLineRunner {

    private final SecurityScanRepository scanRepo;
    private final SecurityVulnerabilityRepository vulnRepo;
    private final ComplianceResultRepository complianceRepo;
    private final ProjectRepository projectRepo;

    @Override
    @Transactional
    public void run(String... args) {
        if (scanRepo.count() > 0) {
            log.info("[SecuritySeeder] Data already exists, skipping seed.");
            return;
        }

        log.info("[SecuritySeeder] Seeding security data...");

        Project project = projectRepo.findAll().stream().findFirst().orElse(null);

        // ── SAST SCANS ─────────────────────────────────────
        SecurityScan sast1 = new SecurityScan();
        sast1.setScanRef("SAST-047");
        sast1.setScanType(SecurityScan.ScanType.SAST);
        sast1.setEngine("SonarQube");
        sast1.setStatus(SecurityScan.ScanStatus.COMPLETED);
        sast1.setStartedAt(LocalDateTime.of(2026, 6, 17, 9, 15));
        sast1.setCompletedAt(LocalDateTime.of(2026, 6, 17, 9, 21, 42));
        sast1.setDuration("6m 42s");
        sast1.setLinesAnalyzed(148720);
        sast1.setFilesAnalyzed(412);
        sast1.setCoverage(87.0);
        sast1.setBranch("main");
        sast1.setCommitHash("a3f8c1d");
        sast1.setProject(project);
        scanRepo.save(sast1);

        SecurityScan sast2 = new SecurityScan();
        sast2.setScanRef("SAST-046");
        sast2.setScanType(SecurityScan.ScanType.SAST);
        sast2.setEngine("Semgrep");
        sast2.setStatus(SecurityScan.ScanStatus.COMPLETED);
        sast2.setStartedAt(LocalDateTime.of(2026, 6, 16, 9, 0));
        sast2.setCompletedAt(LocalDateTime.of(2026, 6, 16, 9, 4, 18));
        sast2.setDuration("4m 18s");
        sast2.setLinesAnalyzed(148200);
        sast2.setFilesAnalyzed(410);
        sast2.setCoverage(85.0);
        sast2.setBranch("main");
        sast2.setCommitHash("e7b2a09");
        sast2.setProject(project);
        scanRepo.save(sast2);

        SecurityScan sast3 = new SecurityScan();
        sast3.setScanRef("SAST-045");
        sast3.setScanType(SecurityScan.ScanType.SAST);
        sast3.setEngine("CodeQL");
        sast3.setStatus(SecurityScan.ScanStatus.COMPLETED);
        sast3.setStartedAt(LocalDateTime.of(2026, 6, 15, 9, 0));
        sast3.setCompletedAt(LocalDateTime.of(2026, 6, 15, 9, 12, 5));
        sast3.setDuration("12m 05s");
        sast3.setLinesAnalyzed(147800);
        sast3.setFilesAnalyzed(408);
        sast3.setCoverage(91.0);
        sast3.setBranch("feature/payment-v2");
        sast3.setCommitHash("c4d19f3");
        sast3.setProject(project);
        scanRepo.save(sast3);

        // ── DAST SCAN ──────────────────────────────────────
        SecurityScan dast1 = new SecurityScan();
        dast1.setScanRef("DAST-023");
        dast1.setScanType(SecurityScan.ScanType.DAST);
        dast1.setEngine("OWASP ZAP");
        dast1.setStatus(SecurityScan.ScanStatus.COMPLETED);
        dast1.setStartedAt(LocalDateTime.of(2026, 6, 17, 8, 0));
        dast1.setCompletedAt(LocalDateTime.of(2026, 6, 17, 8, 18, 34));
        dast1.setDuration("18m 34s");
        dast1.setTargetUrl("https://staging.testauto.local");
        dast1.setProject(project);
        scanRepo.save(dast1);

        // ── SCA SCAN ───────────────────────────────────────
        SecurityScan sca1 = new SecurityScan();
        sca1.setScanRef("SCA-012");
        sca1.setScanType(SecurityScan.ScanType.SCA);
        sca1.setEngine("OWASP Dependency-Check");
        sca1.setStatus(SecurityScan.ScanStatus.COMPLETED);
        sca1.setStartedAt(LocalDateTime.of(2026, 6, 17, 7, 30));
        sca1.setCompletedAt(LocalDateTime.of(2026, 6, 17, 7, 31, 8));
        sca1.setDuration("1m 08s");
        sca1.setBranch("main");
        sca1.setCommitHash("a3f8c1d");
        sca1.setProject(project);
        scanRepo.save(sca1);

        // ── SAST VULNERABILITIES ───────────────────────────
        seedVuln(sast1, project, "SQL Injection via String Concatenation",
                SecurityVulnerability.Severity.CRITICAL, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-89", "A03:2021 Injection",
                "src/main/java/com/app/repository/UserRepository.java", 128,
                "String query = \"SELECT * FROM users WHERE username = '\" + username + \"'\";",
                null, null, null,
                "Input validation not properly sanitized. User-controlled input directly concatenated into SQL query.",
                "An attacker can bypass authentication, extract or modify the entire database.",
                "Use parameterized queries or JPA named parameters instead of string concatenation.",
                "@Query(\"SELECT u FROM User u WHERE u.username = :username\")\nUser findByUsername(@Param(\"username\") String username);",
                null, "Ahmed K.");

        seedVuln(sast1, project, "Hardcoded Database Credentials",
                SecurityVulnerability.Severity.CRITICAL, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-798", "A07:2021 Identification and Authentication Failures",
                "src/main/resources/application.properties", 12,
                "spring.datasource.password=admin123!",
                null, null, null,
                "Database password is hardcoded in the configuration file, visible in version control.",
                "Credentials exposure through source code repository.",
                "Move credentials to environment variables or a secrets manager (Vault, AWS Secrets Manager).",
                "spring.datasource.password=${DB_PASSWORD}",
                null, "Ilyes C.");

        seedVuln(sast1, project, "Cross-Site Scripting (Reflected XSS)",
                SecurityVulnerability.Severity.HIGH, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.IN_PROGRESS,
                "CWE-79", "A03:2021 Injection",
                "src/main/java/com/app/controller/SearchController.java", 45,
                "model.addAttribute(\"query\", request.getParameter(\"q\"));",
                null, null, null,
                "User-supplied input reflected in HTML response without encoding.",
                "An attacker can execute arbitrary JavaScript in the context of a victim's browser session.",
                "Sanitize user input before rendering. Use th:text instead of th:utext in Thymeleaf templates.",
                "model.addAttribute(\"query\", HtmlUtils.htmlEscape(request.getParameter(\"q\")));",
                null, "Sara M.");

        seedVuln(sast1, project, "Insecure Deserialization",
                SecurityVulnerability.Severity.HIGH, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-502", "A08:2021 Software and Data Integrity Failures",
                "src/main/java/com/app/util/ObjectSerializer.java", 34,
                "ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));",
                null, null, null,
                "Untrusted data deserialized without type whitelisting.",
                "Remote code execution through crafted serialized objects.",
                "Use a safe deserialization library or implement type whitelisting.",
                "ObjectMapper mapper = new ObjectMapper();\nmapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance);",
                null, "Ahmed K.");

        seedVuln(sast1, project, "Weak Password Hashing (MD5)",
                SecurityVulnerability.Severity.MEDIUM, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-328", "A02:2021 Cryptographic Failures",
                "src/main/java/com/app/service/AuthService.java", 67,
                "MessageDigest md = MessageDigest.getInstance(\"MD5\");",
                null, null, null,
                "MD5 is cryptographically broken. Collisions can be generated in seconds.",
                "Password database compromise leads to immediate credential recovery.",
                "Use BCrypt or Argon2 for password hashing.",
                "String hashed = new BCryptPasswordEncoder(12).encode(rawPassword);",
                null, "Sara M.");

        seedVuln(sast2, project, "Missing CSRF Protection",
                SecurityVulnerability.Severity.MEDIUM, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.RESOLVED,
                "CWE-352", "A01:2021 Broken Access Control",
                "src/main/java/com/app/config/SecurityConfig.java", 28,
                "http.csrf().disable();",
                null, null, null,
                "CSRF protection globally disabled in Spring Security configuration.",
                "Attackers can forge state-changing requests on behalf of authenticated users.",
                "Enable CSRF protection for state-changing endpoints.",
                "http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));",
                null, "Ilyes C.");

        seedVuln(sast1, project, "Verbose Error Messages Exposing Stack Trace",
                SecurityVulnerability.Severity.LOW, SecurityVulnerability.VulnType.SAST, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-209", "A04:2021 Insecure Design",
                "src/main/java/com/app/controller/GlobalExceptionHandler.java", 19,
                "return ResponseEntity.status(500).body(e.getStackTrace());",
                null, null, null,
                "Full Java stack traces returned to clients in error responses.",
                "Information disclosure reveals internal class names, method names, and library versions.",
                "Return generic error messages to clients. Log the full stack trace server-side only.",
                "return ResponseEntity.status(500).body(Map.of(\"error\", \"Internal server error\"));",
                null, "Sara M.");

        // ── DAST VULNERABILITIES ───────────────────────────
        seedVuln(dast1, project, "SQL Injection — User Login Endpoint",
                SecurityVulnerability.Severity.CRITICAL, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.OPEN,
                null, "A03:2021 Injection",
                null, null, null,
                "/api/auth/login", "POST", "username",
                "The username parameter is vulnerable to SQL injection. Injecting a single quote causes an unhandled SQL syntax error.",
                "An attacker can bypass authentication, extract or modify the entire database, and potentially execute OS commands.",
                "Use parameterized queries. Validate and sanitize all user inputs. Implement a WAF rule for SQL injection patterns.",
                null,
                "Input: admin' OR '1'='1 — Response: HTTP 200 with admin session token", "Ahmed K.");

        seedVuln(dast1, project, "Reflected XSS in Search",
                SecurityVulnerability.Severity.HIGH, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.OPEN,
                null, "A03:2021 Injection",
                null, null, null,
                "/search", "GET", "q",
                "User-supplied input in the q parameter is reflected in the HTML response without encoding.",
                "An attacker can execute arbitrary JavaScript in the context of a victim's browser session.",
                "HTML-encode all user input before rendering. Implement Content-Security-Policy header.",
                null,
                "Input: <script>alert(1)</script> — Script executed in response page", "Sara M.");

        seedVuln(dast1, project, "Missing Security Headers",
                SecurityVulnerability.Severity.HIGH, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.IN_PROGRESS,
                null, "A05:2021 Security Misconfiguration",
                null, null, null,
                "/*", "GET", "response headers",
                "Several critical security headers are missing: X-Content-Type-Options, X-Frame-Options, Content-Security-Policy, Strict-Transport-Security.",
                "The application is vulnerable to clickjacking, MIME sniffing attacks, and protocol downgrade attacks.",
                "Add all recommended security headers via a global filter or reverse proxy configuration.",
                null,
                "Missing: X-Frame-Options, CSP, HSTS, X-Content-Type-Options", "Ilyes C.");

        seedVuln(dast1, project, "CSRF on State-Changing Endpoints",
                SecurityVulnerability.Severity.MEDIUM, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.OPEN,
                null, "A01:2021 Broken Access Control",
                null, null, null,
                "/api/users/profile", "PUT", "body",
                "The profile update endpoint does not validate CSRF tokens and accepts requests from any origin.",
                "An attacker can trick an authenticated user into unknowingly changing their profile data.",
                "Implement CSRF tokens for all state-changing operations. Validate Origin and Referer headers.",
                null,
                "Cross-origin PUT request accepted without CSRF token", "Ilyes C.");

        seedVuln(dast1, project, "Session Fixation",
                SecurityVulnerability.Severity.MEDIUM, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.OPEN,
                null, "A07:2021 Identification and Authentication Failures",
                null, null, null,
                "/api/auth/login", "POST", "session",
                "The session ID is not regenerated after successful authentication.",
                "An attacker can fixate a known session ID and hijack the user session after login.",
                "Regenerate the session ID after every authentication event.",
                null,
                "Session cookie JSESSIONID remains identical before and after login", "Ahmed K.");

        seedVuln(dast1, project, "Sensitive Data in URL Parameters",
                SecurityVulnerability.Severity.LOW, SecurityVulnerability.VulnType.DAST, SecurityVulnerability.VulnStatus.RESOLVED,
                null, "A04:2021 Insecure Design",
                null, null, null,
                "/api/payments/process", "GET", "cardNumber",
                "Credit card number is passed as a URL query parameter, logged in server logs and browser history.",
                "Sensitive payment data exposure through multiple logging channels.",
                "Send sensitive data in the request body via POST. Never pass PII or PCI data in URL parameters.",
                null,
                "GET /api/payments/process?cardNumber=4111111111111111", "Ahmed K.");

        // ── SCA VULNERABILITIES ────────────────────────────
        seedVuln(sca1, project, "Outdated Jackson Databind (CVE-2024-XXXX)",
                SecurityVulnerability.Severity.HIGH, SecurityVulnerability.VulnType.SCA, SecurityVulnerability.VulnStatus.OPEN,
                "CWE-502", "A06:2021 Vulnerable and Outdated Components",
                "pom.xml", null, null,
                null, null, null,
                "Jackson Databind 2.14.2 has a known deserialization vulnerability.",
                "Remote code execution through crafted JSON payloads.",
                "Upgrade Jackson Databind to version 2.17.0 or later.",
                null, null, "Ilyes C.");

        seedVuln(sca1, project, "Outdated Spring Framework (CVE-2024-YYYY)",
                SecurityVulnerability.Severity.MEDIUM, SecurityVulnerability.VulnType.SCA, SecurityVulnerability.VulnStatus.IN_PROGRESS,
                "CWE-20", "A06:2021 Vulnerable and Outdated Components",
                "pom.xml", null, null,
                null, null, null,
                "Spring Framework 6.1.2 has a known input validation bypass.",
                "Authentication bypass under specific configurations.",
                "Upgrade Spring Framework to version 6.1.6 or later.",
                null, null, "Ahmed K.");

        seedVuln(sca1, project, "Log4j Transitive Dependency",
                SecurityVulnerability.Severity.CRITICAL, SecurityVulnerability.VulnType.SCA, SecurityVulnerability.VulnStatus.CLOSED,
                "CWE-917", "A06:2021 Vulnerable and Outdated Components",
                "pom.xml", null, null,
                null, null, null,
                "Log4j 2.14.1 pulled transitively. Contains Log4Shell (CVE-2021-44228).",
                "Critical RCE vulnerability exploitable via JNDI lookups in log messages.",
                "Exclude log4j-core transitive dependency. Add explicit log4j-core 2.21.0+.",
                null, null, "Ilyes C.");

        // ── COMPLIANCE ─────────────────────────────────────
        seedCompliance(project, "OWASP Top 10", "2021", 72.0, 7, 3, 10);
        seedCompliance(project, "OWASP ASVS", "4.0.3", 68.0, 89, 42, 131);
        seedCompliance(project, "ISO 27001", "2022", 81.0, 93, 21, 114);
        seedCompliance(project, "NIST CSF", "2.0", 76.0, 79, 25, 104);

        log.info("[SecuritySeeder] Seeded {} scans, {} vulnerabilities, {} compliance results",
                scanRepo.count(), vulnRepo.count(), complianceRepo.count());
    }

    private void seedVuln(SecurityScan scan, Project project, String title,
                          SecurityVulnerability.Severity severity, SecurityVulnerability.VulnType type,
                          SecurityVulnerability.VulnStatus status,
                          String cweId, String owaspCategory,
                          String file, Integer line, String snippet,
                          String endpoint, String httpMethod, String parameter,
                          String description, String risk, String recommendation,
                          String fixExample, String evidence, String assignedTo) {
        SecurityVulnerability v = new SecurityVulnerability();
        v.setTitle(title);
        v.setSeverity(severity);
        v.setVulnType(type);
        v.setStatus(status);
        v.setSource(scan.getEngine());
        v.setCweId(cweId);
        v.setOwaspCategory(owaspCategory);
        v.setFile(file);
        v.setLine(line);
        v.setSnippet(snippet);
        v.setEndpoint(endpoint);
        v.setHttpMethod(httpMethod);
        v.setParameter(parameter);
        v.setDescription(description);
        v.setRisk(risk);
        v.setRecommendation(recommendation);
        v.setFixExample(fixExample);
        v.setEvidence(evidence);
        v.setAssignedTo(assignedTo);
        v.setCreatedAt(scan.getStartedAt());
        v.setUpdatedAt(LocalDateTime.now());
        v.setScan(scan);
        v.setProject(project);
        vulnRepo.save(v);
    }

    private void seedCompliance(Project project, String framework, String version,
                                Double score, int passed, int failed, int total) {
        ComplianceResult c = new ComplianceResult();
        c.setFramework(framework);
        c.setVersion(version);
        c.setScore(score);
        c.setPassedControls(passed);
        c.setFailedControls(failed);
        c.setTotalControls(total);
        c.setEvaluatedAt(LocalDateTime.now());
        c.setProject(project);
        complianceRepo.save(c);
    }
}
