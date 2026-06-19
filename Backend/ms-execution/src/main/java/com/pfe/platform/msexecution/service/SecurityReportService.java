package com.pfe.platform.msexecution.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.pfe.platform.msexecution.entity.SecurityScan;
import com.pfe.platform.msexecution.entity.SecurityVulnerability;
import com.pfe.platform.msexecution.repository.SecurityVulnerabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SecurityReportService {

    private static final Logger log = LoggerFactory.getLogger(SecurityReportService.class);
    private final SecurityVulnerabilityRepository vulnRepo;

    private static final DeviceRgb PRIMARY = new DeviceRgb(55, 85, 112);
    private static final DeviceRgb CRITICAL_COLOR = new DeviceRgb(220, 38, 38);
    private static final DeviceRgb HIGH_COLOR = new DeviceRgb(234, 88, 12);
    private static final DeviceRgb MEDIUM_COLOR = new DeviceRgb(202, 138, 4);
    private static final DeviceRgb LOW_COLOR = new DeviceRgb(22, 163, 74);
    private static final DeviceRgb INFO_COLOR = new DeviceRgb(59, 130, 246);
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(226, 232, 240);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public SecurityReportService(SecurityVulnerabilityRepository vulnRepo) {
        this.vulnRepo = vulnRepo;
    }

    public byte[] generateSecurityReport(SecurityScan scan, List<SecurityVulnerability> vulns) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            addCoverPage(doc, scan, vulns, bold, regular);
            doc.add(new AreaBreak());

            addExecutiveSummary(doc, scan, vulns, bold, regular);
            addScanDetails(doc, scan, bold, regular);
            addVulnerabilityTable(doc, vulns, bold, regular);
            addDetailedFindings(doc, vulns, bold, regular);
            addRecommendations(doc, vulns, bold, regular);

            int pageCount = pdfDoc.getNumberOfPages();
            doc.close();
            log.info("[SecurityReport] Generated PDF for scan {}: {} pages",
                    scan.getScanRef(), pageCount);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[SecurityReport] Failed to generate PDF", e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public byte[] generateProjectSecurityReport(Long projectId, List<SecurityScan> scans) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            addProjectCover(doc, projectId, scans, bold, regular);
            doc.add(new AreaBreak());

            for (SecurityScan scan : scans) {
                List<SecurityVulnerability> vulns = vulnRepo.findByScanId(scan.getId());
                addExecutiveSummary(doc, scan, vulns, bold, regular);
                addScanDetails(doc, scan, bold, regular);
                addVulnerabilityTable(doc, vulns, bold, regular);
                addDetailedFindings(doc, vulns, bold, regular);
                if (scans.indexOf(scan) < scans.size() - 1) {
                    doc.add(new AreaBreak());
                }
            }

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[SecurityReport] Failed to generate project PDF", e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    // ── Cover Page ──────────────────────────

    private void addCoverPage(Document doc, SecurityScan scan,
                              List<SecurityVulnerability> vulns,
                              PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("\n\n\n\n"));

        doc.add(new Paragraph("SECURITY SCAN REPORT")
                .setFont(bold).setFontSize(28).setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph(scan.getEngine() + " — " + scan.getScanType().name() + " Analysis")
                .setFont(regular).setFontSize(14)
                .setFontColor(new DeviceRgb(100, 116, 139))
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("\n"));

        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth()
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setWidth(UnitValue.createPercentValue(60));

        addCoverRow(infoTable, "Scan ID", scan.getScanRef(), bold, regular);
        addCoverRow(infoTable, "Status", scan.getStatus().name(), bold, regular);
        addCoverRow(infoTable, "Date", scan.getStartedAt() != null
                ? scan.getStartedAt().format(DATE_FMT) : "N/A", bold, regular);
        addCoverRow(infoTable, "Duration", scan.getDuration() != null
                ? scan.getDuration() : "N/A", bold, regular);
        addCoverRow(infoTable, "Findings", String.valueOf(vulns.size()), bold, regular);

        if (scan.getBranch() != null) {
            addCoverRow(infoTable, "Branch", scan.getBranch(), bold, regular);
        }
        if (scan.getCommitHash() != null) {
            addCoverRow(infoTable, "Commit", scan.getCommitHash(), bold, regular);
        }
        if (scan.getTargetUrl() != null) {
            addCoverRow(infoTable, "Target URL", scan.getTargetUrl(), bold, regular);
        }

        doc.add(infoTable);

        doc.add(new Paragraph("\n\n"));
        long critical = vulns.stream()
                .filter(v -> v.getSeverity() == SecurityVulnerability.Severity.CRITICAL).count();
        long high = vulns.stream()
                .filter(v -> v.getSeverity() == SecurityVulnerability.Severity.HIGH).count();

        String riskLevel = critical > 0 ? "CRITICAL" : high > 0 ? "HIGH" : "MODERATE";
        DeviceRgb riskColor = critical > 0 ? CRITICAL_COLOR : high > 0 ? HIGH_COLOR : MEDIUM_COLOR;

        doc.add(new Paragraph("Overall Risk: " + riskLevel)
                .setFont(bold).setFontSize(18).setFontColor(riskColor)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("\n\n\nGenerated: " + LocalDateTime.now().format(DATE_FMT))
                .setFont(regular).setFontSize(9)
                .setFontColor(new DeviceRgb(148, 163, 184))
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addProjectCover(Document doc, Long projectId,
                                 List<SecurityScan> scans,
                                 PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("\n\n\n\n"));
        doc.add(new Paragraph("PROJECT SECURITY AUDIT")
                .setFont(bold).setFontSize(28).setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Comprehensive Security Analysis Report")
                .setFont(regular).setFontSize(14)
                .setFontColor(new DeviceRgb(100, 116, 139))
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("\n"));
        doc.add(new Paragraph("Project #" + projectId + " — " + scans.size() + " scans")
                .setFont(regular).setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FMT))
                .setFont(regular).setFontSize(9)
                .setFontColor(new DeviceRgb(148, 163, 184))
                .setTextAlignment(TextAlignment.CENTER));
    }

    // ── Executive Summary ──────────────────────────

    private void addExecutiveSummary(Document doc, SecurityScan scan,
                                     List<SecurityVulnerability> vulns,
                                     PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("Executive Summary — " + scan.getScanRef())
                .setFont(bold).setFontSize(16).setFontColor(PRIMARY)
                .setMarginBottom(10));

        Map<SecurityVulnerability.Severity, Long> bySeverity = vulns.stream()
                .collect(Collectors.groupingBy(SecurityVulnerability::getSeverity, Collectors.counting()));

        Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1, 1, 1}))
                .useAllAvailableWidth().setMarginBottom(15);

        addKpiCell(kpiTable, "Total", String.valueOf(vulns.size()), PRIMARY, bold, regular);
        addKpiCell(kpiTable, "Critical", String.valueOf(bySeverity.getOrDefault(
                SecurityVulnerability.Severity.CRITICAL, 0L)), CRITICAL_COLOR, bold, regular);
        addKpiCell(kpiTable, "High", String.valueOf(bySeverity.getOrDefault(
                SecurityVulnerability.Severity.HIGH, 0L)), HIGH_COLOR, bold, regular);
        addKpiCell(kpiTable, "Medium", String.valueOf(bySeverity.getOrDefault(
                SecurityVulnerability.Severity.MEDIUM, 0L)), MEDIUM_COLOR, bold, regular);
        addKpiCell(kpiTable, "Low", String.valueOf(bySeverity.getOrDefault(
                SecurityVulnerability.Severity.LOW, 0L)), LOW_COLOR, bold, regular);
        addKpiCell(kpiTable, "Info", String.valueOf(bySeverity.getOrDefault(
                SecurityVulnerability.Severity.INFO, 0L)), INFO_COLOR, bold, regular);

        doc.add(kpiTable);

        addSeverityBar(doc, vulns, bold);
    }

    private void addKpiCell(Table table, String label, String value,
                            DeviceRgb color, PdfFont bold, PdfFont regular) {
        Cell cell = new Cell().setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setBackgroundColor(LIGHT_BG).setPadding(8)
                .setTextAlignment(TextAlignment.CENTER);
        cell.add(new Paragraph(value).setFont(bold).setFontSize(20).setFontColor(color));
        cell.add(new Paragraph(label).setFont(regular).setFontSize(8)
                .setFontColor(new DeviceRgb(100, 116, 139)));
        table.addCell(cell);
    }

    private void addSeverityBar(Document doc, List<SecurityVulnerability> vulns, PdfFont bold) {
        if (vulns.isEmpty()) return;
        int total = vulns.size();
        long crit = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.CRITICAL).count();
        long high = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.HIGH).count();
        long med = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.MEDIUM).count();
        long low = vulns.stream().filter(v -> v.getSeverity() == SecurityVulnerability.Severity.LOW).count();
        long info = total - crit - high - med - low;

        float[] widths = {
                Math.max((float) crit / total * 100, crit > 0 ? 3 : 0),
                Math.max((float) high / total * 100, high > 0 ? 3 : 0),
                Math.max((float) med / total * 100, med > 0 ? 3 : 0),
                Math.max((float) low / total * 100, low > 0 ? 3 : 0),
                Math.max((float) info / total * 100, info > 0 ? 3 : 0)
        };
        float sum = 0;
        int segments = 0;
        for (float w : widths) { sum += w; if (w > 0) segments++; }
        if (segments == 0) return;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) widths[i] = widths[i] / sum * 100;
        }

        float[] nonZero = new float[segments];
        DeviceRgb[] colors = {CRITICAL_COLOR, HIGH_COLOR, MEDIUM_COLOR, LOW_COLOR, INFO_COLOR};
        DeviceRgb[] usedColors = new DeviceRgb[segments];
        int idx = 0;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) { nonZero[idx] = widths[i]; usedColors[idx] = colors[i]; idx++; }
        }

        Table bar = new Table(UnitValue.createPercentArray(nonZero)).useAllAvailableWidth()
                .setMarginBottom(15);
        for (int i = 0; i < segments; i++) {
            bar.addCell(new Cell().setBackgroundColor(usedColors[i]).setHeight(12)
                    .setBorder(Border.NO_BORDER));
        }
        doc.add(bar);
    }

    // ── Scan Details ──────────────────────────

    private void addScanDetails(Document doc, SecurityScan scan, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("Scan Configuration")
                .setFont(bold).setFontSize(14).setFontColor(PRIMARY).setMarginTop(15));

        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginBottom(15);

        addDetailRow(t, "Engine", scan.getEngine(), bold, regular);
        addDetailRow(t, "Type", scan.getScanType().name(), bold, regular);
        addDetailRow(t, "Status", scan.getStatus().name(), bold, regular);
        addDetailRow(t, "Started", scan.getStartedAt() != null
                ? scan.getStartedAt().format(DATE_FMT) : "N/A", bold, regular);
        addDetailRow(t, "Completed", scan.getCompletedAt() != null
                ? scan.getCompletedAt().format(DATE_FMT) : "N/A", bold, regular);
        addDetailRow(t, "Duration", scan.getDuration() != null
                ? scan.getDuration() : "N/A", bold, regular);

        if (scan.getLinesAnalyzed() != null) {
            addDetailRow(t, "Lines Analyzed", String.format("%,d", scan.getLinesAnalyzed()), bold, regular);
        }
        if (scan.getFilesAnalyzed() != null) {
            addDetailRow(t, "Files Analyzed", String.valueOf(scan.getFilesAnalyzed()), bold, regular);
        }
        if (scan.getBranch() != null) {
            addDetailRow(t, "Branch", scan.getBranch(), bold, regular);
        }
        if (scan.getCommitHash() != null) {
            addDetailRow(t, "Commit", scan.getCommitHash(), bold, regular);
        }
        if (scan.getTargetUrl() != null) {
            addDetailRow(t, "Target URL", scan.getTargetUrl(), bold, regular);
        }

        doc.add(t);
    }

    // ── Vulnerability Table ──────────────────────────

    private void addVulnerabilityTable(Document doc, List<SecurityVulnerability> vulns,
                                       PdfFont bold, PdfFont regular) {
        if (vulns.isEmpty()) return;

        doc.add(new Paragraph("Vulnerability Summary")
                .setFont(bold).setFontSize(14).setFontColor(PRIMARY).setMarginTop(15));

        Table t = new Table(UnitValue.createPercentArray(new float[]{0.5f, 3, 1, 1, 1.5f}))
                .useAllAvailableWidth().setMarginBottom(15).setFontSize(8);

        String[] headers = {"#", "Title", "Severity", "CWE", "Location"};
        for (String h : headers) {
            t.addHeaderCell(new Cell().setBackgroundColor(PRIMARY)
                    .add(new Paragraph(h).setFont(bold).setFontColor(ColorConstants.WHITE).setFontSize(8)));
        }

        int idx = 1;
        for (SecurityVulnerability v : vulns) {
            DeviceRgb bg = idx % 2 == 0 ? LIGHT_BG : new DeviceRgb(255, 255, 255);

            t.addCell(new Cell().setBackgroundColor(bg)
                    .add(new Paragraph(String.valueOf(idx)).setFont(regular)));
            t.addCell(new Cell().setBackgroundColor(bg)
                    .add(new Paragraph(truncate(v.getTitle(), 60)).setFont(regular)));
            t.addCell(new Cell().setBackgroundColor(bg)
                    .add(new Paragraph(v.getSeverity().name()).setFont(bold)
                            .setFontColor(severityColor(v.getSeverity()))));
            t.addCell(new Cell().setBackgroundColor(bg)
                    .add(new Paragraph(v.getCweId() != null ? v.getCweId() : "").setFont(regular)));

            String location = v.getFile() != null
                    ? v.getFile() + (v.getLine() != null ? ":" + v.getLine() : "")
                    : v.getEndpoint() != null ? v.getEndpoint() : "";
            t.addCell(new Cell().setBackgroundColor(bg)
                    .add(new Paragraph(truncate(location, 40)).setFont(regular)));

            idx++;
        }
        doc.add(t);
    }

    // ── Detailed Findings ──────────────────────────

    private void addDetailedFindings(Document doc, List<SecurityVulnerability> vulns,
                                     PdfFont bold, PdfFont regular) {
        List<SecurityVulnerability> critical = vulns.stream()
                .filter(v -> v.getSeverity() == SecurityVulnerability.Severity.CRITICAL
                        || v.getSeverity() == SecurityVulnerability.Severity.HIGH)
                .toList();

        if (critical.isEmpty()) return;

        doc.add(new Paragraph("Detailed Findings — Critical & High")
                .setFont(bold).setFontSize(14).setFontColor(PRIMARY).setMarginTop(15));

        for (SecurityVulnerability v : critical) {
            DeviceRgb borderColor = v.getSeverity() == SecurityVulnerability.Severity.CRITICAL
                    ? CRITICAL_COLOR : HIGH_COLOR;

            Table card = new Table(1).useAllAvailableWidth()
                    .setMarginBottom(10)
                    .setBorderLeft(new SolidBorder(borderColor, 3));

            Cell content = new Cell().setPadding(10).setBorder(new SolidBorder(BORDER_COLOR, 0.5f));

            content.add(new Paragraph(v.getTitle())
                    .setFont(bold).setFontSize(11).setFontColor(PRIMARY));

            content.add(new Paragraph("[" + v.getSeverity().name() + "] "
                    + (v.getCweId() != null ? v.getCweId() : "") + " "
                    + (v.getOwaspCategory() != null ? "OWASP " + v.getOwaspCategory() : ""))
                    .setFont(regular).setFontSize(8)
                    .setFontColor(severityColor(v.getSeverity())));

            if (v.getFile() != null) {
                content.add(new Paragraph("File: " + v.getFile()
                        + (v.getLine() != null ? " (line " + v.getLine() + ")" : ""))
                        .setFont(regular).setFontSize(8)
                        .setFontColor(new DeviceRgb(100, 116, 139)));
            }
            if (v.getEndpoint() != null) {
                content.add(new Paragraph("Endpoint: "
                        + (v.getHttpMethod() != null ? v.getHttpMethod() + " " : "")
                        + v.getEndpoint())
                        .setFont(regular).setFontSize(8)
                        .setFontColor(new DeviceRgb(100, 116, 139)));
            }

            if (v.getDescription() != null && !v.getDescription().isBlank()) {
                content.add(new Paragraph("Description:").setFont(bold).setFontSize(9).setMarginTop(5));
                content.add(new Paragraph(truncate(v.getDescription(), 500))
                        .setFont(regular).setFontSize(8));
            }

            if (v.getSnippet() != null && !v.getSnippet().isBlank()) {
                content.add(new Paragraph("Code:").setFont(bold).setFontSize(9).setMarginTop(5));
                content.add(new Paragraph(v.getSnippet())
                        .setFont(regular).setFontSize(7)
                        .setBackgroundColor(new DeviceRgb(241, 245, 249))
                        .setPadding(5));
            }

            if (v.getRecommendation() != null && !v.getRecommendation().isBlank()) {
                content.add(new Paragraph("Recommendation:").setFont(bold).setFontSize(9)
                        .setMarginTop(5).setFontColor(LOW_COLOR));
                content.add(new Paragraph(truncate(v.getRecommendation(), 400))
                        .setFont(regular).setFontSize(8));
            }

            card.addCell(content);
            doc.add(card);
        }
    }

    // ── Recommendations ──────────────────────────

    private void addRecommendations(Document doc, List<SecurityVulnerability> vulns,
                                     PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("Remediation Recommendations")
                .setFont(bold).setFontSize(14).setFontColor(PRIMARY).setMarginTop(15));

        Map<SecurityVulnerability.Severity, Long> counts = vulns.stream()
                .collect(Collectors.groupingBy(SecurityVulnerability::getSeverity, Collectors.counting()));

        long critical = counts.getOrDefault(SecurityVulnerability.Severity.CRITICAL, 0L);
        long high = counts.getOrDefault(SecurityVulnerability.Severity.HIGH, 0L);

        if (critical > 0) {
            doc.add(new Paragraph("IMMEDIATE ACTION REQUIRED")
                    .setFont(bold).setFontSize(11).setFontColor(CRITICAL_COLOR));
            doc.add(new Paragraph(critical + " critical vulnerabilities must be resolved before deployment. "
                    + "These represent direct exploitable attack vectors.")
                    .setFont(regular).setFontSize(9).setMarginBottom(8));
        }
        if (high > 0) {
            doc.add(new Paragraph("HIGH PRIORITY")
                    .setFont(bold).setFontSize(11).setFontColor(HIGH_COLOR));
            doc.add(new Paragraph(high + " high-severity findings should be addressed within the current sprint.")
                    .setFont(regular).setFontSize(9).setMarginBottom(8));
        }

        Map<String, Long> byOwasp = vulns.stream()
                .filter(v -> v.getOwaspCategory() != null && !v.getOwaspCategory().equals("A00"))
                .collect(Collectors.groupingBy(SecurityVulnerability::getOwaspCategory, Collectors.counting()));

        if (!byOwasp.isEmpty()) {
            doc.add(new Paragraph("OWASP Top 10 Distribution")
                    .setFont(bold).setFontSize(11).setFontColor(PRIMARY).setMarginTop(10));

            Table t = new Table(UnitValue.createPercentArray(new float[]{1, 3, 1}))
                    .useAllAvailableWidth().setMarginBottom(15);
            t.addHeaderCell(headerCell("Category", bold));
            t.addHeaderCell(headerCell("Description", bold));
            t.addHeaderCell(headerCell("Count", bold));

            Map<String, String> owaspNames = Map.ofEntries(
                    Map.entry("A01", "Broken Access Control"),
                    Map.entry("A02", "Cryptographic Failures"),
                    Map.entry("A03", "Injection"),
                    Map.entry("A04", "Insecure Design"),
                    Map.entry("A05", "Security Misconfiguration"),
                    Map.entry("A06", "Vulnerable Components"),
                    Map.entry("A07", "Authentication Failures"),
                    Map.entry("A08", "Software Integrity Failures"),
                    Map.entry("A09", "Logging Failures"),
                    Map.entry("A10", "SSRF")
            );

            byOwasp.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> {
                        t.addCell(cell(e.getKey(), regular));
                        t.addCell(cell(owaspNames.getOrDefault(e.getKey(), ""), regular));
                        t.addCell(cell(String.valueOf(e.getValue()), regular));
                    });
            doc.add(t);
        }
    }

    // ── Helpers ──────────────────────────

    private void addCoverRow(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        t.addCell(new Cell().setBorder(Border.NO_BORDER).setPaddingBottom(5)
                .add(new Paragraph(label).setFont(bold).setFontSize(10).setFontColor(PRIMARY)));
        t.addCell(new Cell().setBorder(Border.NO_BORDER).setPaddingBottom(5)
                .add(new Paragraph(value).setFont(regular).setFontSize(10)));
    }

    private void addDetailRow(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        t.addCell(new Cell().setBackgroundColor(LIGHT_BG).setPadding(6)
                .add(new Paragraph(label).setFont(bold).setFontSize(9)));
        t.addCell(new Cell().setPadding(6)
                .add(new Paragraph(value).setFont(regular).setFontSize(9)));
    }

    private Cell headerCell(String text, PdfFont bold) {
        return new Cell().setBackgroundColor(PRIMARY)
                .add(new Paragraph(text).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE));
    }

    private Cell cell(String text, PdfFont regular) {
        return new Cell().add(new Paragraph(text).setFont(regular).setFontSize(9));
    }

    private DeviceRgb severityColor(SecurityVulnerability.Severity s) {
        return switch (s) {
            case CRITICAL -> CRITICAL_COLOR;
            case HIGH -> HIGH_COLOR;
            case MEDIUM -> MEDIUM_COLOR;
            case LOW -> LOW_COLOR;
            case INFO -> INFO_COLOR;
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
