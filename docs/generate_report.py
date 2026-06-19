from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.units import cm, mm
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, KeepTogether, HRFlowable
)
from reportlab.lib import colors
import os

OUTPUT = os.path.join(os.path.dirname(__file__), "Security_Center_Technical_Report.pdf")

PRIMARY = HexColor("#375570")
DARK = HexColor("#1e293b")
ACCENT = HexColor("#3b82f6")
CRITICAL_C = HexColor("#dc2626")
HIGH_C = HexColor("#ea580c")
MEDIUM_C = HexColor("#ca8a04")
LOW_C = HexColor("#16a34a")
INFO_C = HexColor("#3b82f6")
LIGHT_BG = HexColor("#f8fafc")
BORDER_C = HexColor("#e2e8f0")
WHITE = white

styles = getSampleStyleSheet()

styles.add(ParagraphStyle(name='CoverTitle', fontName='Helvetica-Bold', fontSize=28,
                          textColor=PRIMARY, alignment=TA_CENTER, spaceAfter=10))
styles.add(ParagraphStyle(name='CoverSub', fontName='Helvetica', fontSize=14,
                          textColor=HexColor("#64748b"), alignment=TA_CENTER, spaceAfter=30))
styles.add(ParagraphStyle(name='H1', fontName='Helvetica-Bold', fontSize=18,
                          textColor=PRIMARY, spaceBefore=20, spaceAfter=10))
styles.add(ParagraphStyle(name='H2', fontName='Helvetica-Bold', fontSize=14,
                          textColor=PRIMARY, spaceBefore=15, spaceAfter=8))
styles.add(ParagraphStyle(name='H3', fontName='Helvetica-Bold', fontSize=11,
                          textColor=DARK, spaceBefore=10, spaceAfter=6))
styles.add(ParagraphStyle(name='Body', fontName='Helvetica', fontSize=9.5,
                          textColor=DARK, alignment=TA_JUSTIFY, spaceAfter=6,
                          leading=13))
styles.add(ParagraphStyle(name='CodeBlock', fontName='Courier', fontSize=7.5,
                          textColor=DARK, backColor=LIGHT_BG, spaceAfter=8,
                          leading=10, leftIndent=10, rightIndent=10))
styles.add(ParagraphStyle(name='BulletItem', fontName='Helvetica', fontSize=9.5,
                          textColor=DARK, leftIndent=20, bulletIndent=10,
                          spaceAfter=3, leading=12))
styles.add(ParagraphStyle(name='BulletItemBold', fontName='Helvetica-Bold', fontSize=9.5,
                          textColor=DARK, leftIndent=20, bulletIndent=10,
                          spaceAfter=3, leading=12))
styles.add(ParagraphStyle(name='TableHeader', fontName='Helvetica-Bold', fontSize=8,
                          textColor=WHITE, alignment=TA_CENTER))
styles.add(ParagraphStyle(name='TableCell', fontName='Helvetica', fontSize=8,
                          textColor=DARK))
styles.add(ParagraphStyle(name='TableCellCenter', fontName='Helvetica', fontSize=8,
                          textColor=DARK, alignment=TA_CENTER))
styles.add(ParagraphStyle(name='SeqLine', fontName='Courier', fontSize=7,
                          textColor=DARK, leading=9, leftIndent=5))
styles.add(ParagraphStyle(name='Footer', fontName='Helvetica', fontSize=7,
                          textColor=HexColor("#94a3b8"), alignment=TA_CENTER))

def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 7)
    canvas.setFillColor(HexColor("#94a3b8"))
    canvas.drawString(40, 25, "Rapport Technique - Module Security Center")
    canvas.drawRightString(A4[0] - 40, 25, f"Page {doc.page}")
    canvas.setStrokeColor(BORDER_C)
    canvas.line(40, 35, A4[0] - 40, 35)
    canvas.restoreState()

def make_table(headers, rows, col_widths=None):
    data = [[Paragraph(h, styles['TableHeader']) for h in headers]]
    for row in rows:
        data.append([Paragraph(str(c), styles['TableCell']) for c in row])

    w = col_widths or [None] * len(headers)
    t = Table(data, colWidths=w, repeatRows=1)
    style = [
        ('BACKGROUND', (0, 0), (-1, 0), PRIMARY),
        ('TEXTCOLOR', (0, 0), (-1, 0), WHITE),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 8),
        ('ALIGN', (0, 0), (-1, 0), 'CENTER'),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('GRID', (0, 0), (-1, -1), 0.5, BORDER_C),
        ('FONTSIZE', (0, 1), (-1, -1), 8),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]
    for i in range(1, len(data)):
        if i % 2 == 0:
            style.append(('BACKGROUND', (0, i), (-1, i), LIGHT_BG))
    t.setStyle(TableStyle(style))
    return t

def build():
    doc = SimpleDocTemplate(OUTPUT, pagesize=A4,
                            topMargin=2*cm, bottomMargin=2*cm,
                            leftMargin=2*cm, rightMargin=2*cm)
    story = []

    # ── COVER ──
    story.append(Spacer(1, 4*cm))
    story.append(Paragraph("RAPPORT TECHNIQUE", styles['CoverTitle']))
    story.append(Paragraph("Module Security Center", styles['CoverTitle']))
    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("Architecture, Mecanismes et Outils d'Analyse de Securite", styles['CoverSub']))
    story.append(Spacer(1, 1*cm))

    cover_data = [
        ["Projet", "Automatisation Intelligente des Tests Applicatifs"],
        ["Module", "Security Center (SAST / DAST / SCA)"],
        ["Stack", "Spring Boot 4 + Next.js 16 + PostgreSQL 15"],
        ["Outils", "Semgrep, OWASP ZAP, OWASP Dependency-Check"],
        ["Date", "Juin 2026"],
    ]
    ct = Table([[Paragraph(r[0], ParagraphStyle('', parent=styles['Body'], fontName='Helvetica-Bold')),
                 Paragraph(r[1], styles['Body'])] for r in cover_data],
               colWidths=[4*cm, 10*cm])
    ct.setStyle(TableStyle([
        ('GRID', (0,0), (-1,-1), 0.5, BORDER_C),
        ('BACKGROUND', (0,0), (0,-1), LIGHT_BG),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('LEFTPADDING', (0,0), (-1,-1), 8),
        ('TOPPADDING', (0,0), (-1,-1), 6),
        ('BOTTOMPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(ct)
    story.append(PageBreak())

    # ── TABLE OF CONTENTS ──
    story.append(Paragraph("Table des Matieres", styles['H1']))
    toc_items = [
        "1. Introduction",
        "2. Architecture Technique",
        "3. Outils d'Analyse de Securite",
        "4. Modele de Donnees",
        "5. API REST",
        "6. Frontend - Security Center Dashboard",
        "7. Rapport PDF de Securite",
        "8. Prerequis d'Installation",
        "9. Diagramme de Sequence - Scan SAST",
        "10. Diagramme de Sequence - Scan DAST",
        "11. Classification des Vulnerabilites",
        "12. Conclusion",
    ]
    for item in toc_items:
        story.append(Paragraph(item, styles['Body']))
    story.append(PageBreak())

    # ── 1. INTRODUCTION ──
    story.append(Paragraph("1. Introduction", styles['H1']))
    story.append(Paragraph(
        "Le module Security Center integre trois types d'analyse de securite (SAST, DAST, SCA) "
        "directement dans la plateforme d'automatisation des tests. Il permet de scanner le code source, "
        "les applications web en production, et les dependances d'un projet Java/Spring Boot.",
        styles['Body']))
    story.append(Paragraph(
        "Ce module transforme la plateforme d'un simple outil d'automatisation de tests en une solution "
        "complete de DevSecOps, offrant une couverture de securite a 360 degres. Les resultats sont "
        "centralises dans PostgreSQL, visualises dans un dashboard interactif Next.js, et exportables "
        "en rapport PDF professionnel.",
        styles['Body']))
    story.append(Spacer(1, 0.3*cm))

    # Key features
    features = [
        "<b>SAST (Static Application Security Testing)</b> - Analyse du code source via Semgrep",
        "<b>DAST (Dynamic Application Security Testing)</b> - Scan d'applications en production via OWASP ZAP",
        "<b>SCA (Software Composition Analysis)</b> - Audit des dependances via OWASP Dependency-Check",
        "<b>Dashboard temps reel</b> - KPIs, OWASP Top 10, score de securite, tendances",
        "<b>Rapports PDF</b> - Generation automatique via iTextPDF 7",
        "<b>Gestion des vulnerabilites</b> - Suivi, assignation, classification CWE/OWASP",
    ]
    for f in features:
        story.append(Paragraph(f, styles['BulletItem'], bulletText='•'))

    # ── 2. ARCHITECTURE ──
    story.append(PageBreak())
    story.append(Paragraph("2. Architecture Technique", styles['H1']))
    story.append(Paragraph("2.1 Architecture Globale", styles['H2']))

    arch_lines = [
        "Frontend (Next.js, port 3000)",
        "    | POST /api/security/scan/{sast|dast|sca}",
        "    v",
        "ms-execution (Spring Boot, port 8083)",
        "    +-- SecurityScanController (REST API)",
        "    +-- SecurityScanService (Orchestrateur)",
        "    |   +-- SemgrepScanService (SAST)",
        "    |   +-- ZapScanService (DAST)",
        "    |   +-- DependencyCheckService (SCA)",
        "    +-- SecurityReportService (PDF iTextPDF)",
        "    +-- JPA Repositories --> PostgreSQL",
        "",
        "ms_gestion (Spring Boot, port 8082)",
        "    +-- SecurityController (Dashboard/KPIs)",
        "    +-- SecurityService (Lecture/Agregation)",
        "    +-- SecurityDataSeeder (Donnees demo)",
        "",
        "PostgreSQL (port 5432)",
        "    +-- security_scans",
        "    +-- security_vulnerabilities",
        "    +-- compliance_results",
    ]
    for line in arch_lines:
        story.append(Paragraph(line, styles['CodeBlock']))

    story.append(Paragraph("2.2 Flux d'execution d'un scan", styles['H2']))
    steps = [
        "1. L'utilisateur selectionne un projet et un environnement dans le dashboard",
        "2. Le frontend envoie POST /api/security/scan/{type}?projectId=X&amp;environmentId=Y",
        "3. ms-execution cree un enregistrement SecurityScan (status=RUNNING)",
        "4. Le scan s'execute en asynchrone (@Async Spring)",
        "5. Pour SAST/SCA : clone Git du repo (JGit + PAT auth) puis execution de l'outil et parsing JSON",
        "6. Pour DAST : Docker lance OWASP ZAP contre l'URL cible de l'environnement",
        "7. Les resultats sont persistes dans la table security_vulnerabilities",
        "8. Le scan passe en COMPLETED avec duree et metriques",
        "9. Le rapport PDF est disponible via GET /api/security/report/{scanId}",
    ]
    for s in steps:
        story.append(Paragraph(s, styles['Body']))

    # ── 3. OUTILS ──
    story.append(PageBreak())
    story.append(Paragraph("3. Outils d'Analyse de Securite", styles['H1']))

    # SAST
    story.append(Paragraph("3.1 SAST - Semgrep", styles['H2']))
    story.append(Paragraph(
        "Semgrep est un outil d'analyse statique open-source qui detecte les vulnerabilites "
        "de securite dans le code source. Il supporte plus de 30 langages et dispose de 2500+ "
        "regles communautaires couvrant les principales failles de securite.",
        styles['Body']))
    story.append(Paragraph("Commande d'execution :", styles['H3']))
    story.append(Paragraph("semgrep scan --config auto --json --output report.json &lt;repo&gt;", styles['CodeBlock']))

    sast_detections = [
        ["CWE-89", "SQL Injection", "A03 - Injection"],
        ["CWE-79", "Cross-Site Scripting (XSS)", "A03 - Injection"],
        ["CWE-78", "OS Command Injection", "A03 - Injection"],
        ["CWE-798", "Hardcoded Credentials", "A07 - Auth Failures"],
        ["CWE-327", "Broken Cryptography", "A02 - Crypto Failures"],
        ["CWE-22", "Path Traversal", "A01 - Broken Access Control"],
        ["CWE-502", "Unsafe Deserialization", "A08 - Integrity Failures"],
        ["CWE-918", "SSRF", "A10 - SSRF"],
    ]
    story.append(Paragraph("Detections principales :", styles['H3']))
    story.append(make_table(["CWE", "Vulnerabilite", "OWASP Top 10"], sast_detections,
                            [2.5*cm, 7*cm, 5*cm]))
    story.append(Spacer(1, 0.3*cm))

    sast_output = [
        "<b>Sortie parsee</b> : fichier, ligne, snippet de code, CWE ID, severite, recommandation",
        "<b>Mapping OWASP</b> : chaque CWE est mappe vers la categorie OWASP Top 10 (A01-A10)",
        "<b>Classe Java</b> : SemgrepScanService.java - parse le JSON et cree les SecurityVulnerability",
    ]
    for s in sast_output:
        story.append(Paragraph(s, styles['BulletItem'], bulletText='•'))

    # DAST
    story.append(Paragraph("3.2 DAST - OWASP ZAP", styles['H2']))
    story.append(Paragraph(
        "OWASP ZAP (Zed Attack Proxy) est l'outil de reference pour l'analyse dynamique "
        "des applications web. Il est execute via Docker en mode headless (baseline scan) "
        "pour detecter les vulnerabilites sur une application en cours d'execution.",
        styles['Body']))
    story.append(Paragraph("Commande d'execution :", styles['H3']))
    story.append(Paragraph(
        "docker run --rm --network host zaproxy/zap-stable zap-baseline.py -t &lt;url&gt; -J report.json -I",
        styles['CodeBlock']))

    dast_detections = [
        "XSS reflechi et stocke",
        "CSRF manquant",
        "Headers de securite absents (CSP, HSTS, X-Frame-Options)",
        "Cookies non securises (HttpOnly, Secure, SameSite)",
        "CORS misconfiguration",
        "Information disclosure",
    ]
    story.append(Paragraph("Detections :", styles['H3']))
    for d in dast_detections:
        story.append(Paragraph(d, styles['BulletItem'], bulletText='•'))

    dast_notes = [
        "<b>Prerequis</b> : Docker installe, application cible accessible via baseUrlApi ou baseUrlWeb",
        "<b>Classe Java</b> : ZapScanService.java - lance le conteneur Docker et parse le JSON",
        "<b>Timeout</b> : configurable via security.zap.timeout-minutes (defaut: 15 min)",
    ]
    for n in dast_notes:
        story.append(Paragraph(n, styles['BulletItem'], bulletText='•'))

    # SCA
    story.append(Paragraph("3.3 SCA - OWASP Dependency-Check", styles['H2']))
    story.append(Paragraph(
        "OWASP Dependency-Check analyse les dependances Maven (pom.xml) pour identifier "
        "les composants avec des CVE (Common Vulnerabilities and Exposures) connues dans "
        "la base NVD (National Vulnerability Database).",
        styles['Body']))
    story.append(Paragraph("Commande d'execution :", styles['H3']))
    story.append(Paragraph("mvn dependency-check:check -DfailBuildOnCVSS=11 -Dformat=JSON", styles['CodeBlock']))

    sca_notes = [
        "<b>Base de donnees</b> : NVD - mise a jour automatique a chaque execution",
        "<b>Detections</b> : Log4Shell (CVE-2021-44228), Spring4Shell (CVE-2022-22965), Jackson RCE, etc.",
        "<b>Injection automatique</b> : le plugin Maven dependency-check-maven 9.0.9 est injecte dans le pom.xml si absent",
        "<b>Classe Java</b> : DependencyCheckService.java - injecte le plugin, execute Maven, parse le JSON",
    ]
    for n in sca_notes:
        story.append(Paragraph(n, styles['BulletItem'], bulletText='•'))

    # ── 4. MODELE DE DONNEES ──
    story.append(PageBreak())
    story.append(Paragraph("4. Modele de Donnees", styles['H1']))

    story.append(Paragraph("4.1 Entite SecurityScan", styles['H2']))
    scan_fields = [
        ["id", "BIGSERIAL", "Cle primaire auto-generee"],
        ["scanRef", "VARCHAR", "Identifiant unique (SCAN-XXXXXXXX)"],
        ["scanType", "ENUM", "SAST, DAST, SCA"],
        ["engine", "VARCHAR", "Semgrep, OWASP ZAP, Dependency-Check"],
        ["status", "ENUM", "RUNNING, COMPLETED, FAILED, SCHEDULED"],
        ["startedAt", "TIMESTAMP", "Debut du scan"],
        ["completedAt", "TIMESTAMP", "Fin du scan"],
        ["duration", "VARCHAR", "Duree formatee (ex: 2m 34s)"],
        ["linesAnalyzed", "INTEGER", "Lignes de code analysees (SAST)"],
        ["filesAnalyzed", "INTEGER", "Fichiers analyses (SAST)"],
        ["coverage", "DOUBLE", "Couverture estimee"],
        ["branch", "VARCHAR", "Branche Git scannee"],
        ["commitHash", "VARCHAR", "Hash du commit"],
        ["targetUrl", "VARCHAR", "URL cible (DAST)"],
        ["project_id", "BIGINT FK", "Reference vers le projet"],
    ]
    story.append(make_table(["Champ", "Type", "Description"], scan_fields,
                            [3.5*cm, 3*cm, 8.5*cm]))

    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("4.2 Entite SecurityVulnerability", styles['H2']))
    vuln_fields = [
        ["id", "BIGSERIAL", "Cle primaire"],
        ["title", "VARCHAR", "Titre de la vulnerabilite"],
        ["severity", "ENUM", "CRITICAL, HIGH, MEDIUM, LOW, INFO"],
        ["vulnType", "ENUM", "SAST, DAST, SCA"],
        ["source", "VARCHAR", "Outil source"],
        ["status", "ENUM", "OPEN, IN_PROGRESS, RESOLVED, CLOSED, FALSE_POSITIVE"],
        ["cweId", "VARCHAR", "Identifiant CWE (ex: CWE-89)"],
        ["owaspCategory", "VARCHAR", "Categorie OWASP Top 10 (A01-A10)"],
        ["file", "VARCHAR", "Fichier source (SAST)"],
        ["line", "INTEGER", "Numero de ligne (SAST)"],
        ["snippet", "TEXT", "Extrait de code vulnerable"],
        ["endpoint", "VARCHAR", "Endpoint HTTP (DAST)"],
        ["httpMethod", "VARCHAR", "Methode HTTP (DAST)"],
        ["parameter", "VARCHAR", "Parametre vulnerable (DAST)"],
        ["description", "TEXT", "Description detaillee"],
        ["risk", "TEXT", "Evaluation du risque"],
        ["recommendation", "TEXT", "Recommandation de correction"],
        ["fixExample", "TEXT", "Exemple de code corrige"],
        ["evidence", "TEXT", "Preuve d'exploitation (DAST)"],
        ["scan_id", "BIGINT FK", "Reference vers le scan"],
        ["project_id", "BIGINT FK", "Reference vers le projet"],
    ]
    story.append(make_table(["Champ", "Type", "Description"], vuln_fields,
                            [3.5*cm, 3*cm, 8.5*cm]))

    # ── 5. API REST ──
    story.append(PageBreak())
    story.append(Paragraph("5. API REST", styles['H1']))

    story.append(Paragraph("5.1 Endpoints ms-execution (port 8083) - Execution des scans", styles['H2']))
    exec_api = [
        ["POST", "/api/security/scan/sast", "Lance un scan SAST (Semgrep)"],
        ["POST", "/api/security/scan/dast", "Lance un scan DAST (OWASP ZAP)"],
        ["POST", "/api/security/scan/sca", "Lance un scan SCA (Dependency-Check)"],
        ["GET", "/api/security/scans?projectId=", "Liste les scans d'un projet"],
        ["GET", "/api/security/scans/{id}", "Detail d'un scan"],
        ["GET", "/api/security/scans/{scanId}/vulns", "Vulnerabilites d'un scan"],
        ["GET", "/api/security/report/{scanId}", "Telecharger le rapport PDF"],
        ["GET", "/api/security/report/project/{id}", "Rapport PDF consolide"],
    ]
    story.append(make_table(["Methode", "Endpoint", "Description"], exec_api,
                            [2*cm, 6.5*cm, 6.5*cm]))

    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("5.2 Endpoints ms_gestion (port 8082) - Dashboard et KPIs", styles['H2']))
    gest_api = [
        ["GET", "/api/security/dashboard", "KPIs agreges (score, compteurs, OWASP)"],
        ["GET", "/api/security/vulnerabilities", "Liste toutes les vulnerabilites"],
        ["PATCH", "/api/security/vulnerabilities/{id}/status", "Modifier le statut"],
        ["PATCH", "/api/security/vulnerabilities/{id}/assign", "Assigner a un developpeur"],
        ["GET", "/api/security/compliance", "Resultats de conformite"],
    ]
    story.append(make_table(["Methode", "Endpoint", "Description"], gest_api,
                            [2*cm, 7*cm, 6*cm]))

    # ── 6. FRONTEND ──
    story.append(PageBreak())
    story.append(Paragraph("6. Frontend - Security Center Dashboard", styles['H1']))

    story.append(Paragraph("6.1 Architecture des composants", styles['H2']))
    components = [
        ["OverviewCards", "6 KPI cards (Total, Critical, High, Medium, Low, Resolved)"],
        ["SecurityScoreGauge", "Score circulaire SVG avec sous-scores"],
        ["SastCenter", "Dashboard SAST avec metriques, historique, findings expandables"],
        ["DastCenter", "Dashboard DAST avec findings par endpoint, evidence, actions"],
        ["OwaspTable", "Tableau OWASP Top 10 avec score de risque par categorie"],
        ["VulnerabilityTable", "Table de donnees avec recherche, filtres, pagination"],
        ["ComplianceCards", "4 frameworks (OWASP Top 10, ASVS, ISO 27001, NIST CSF)"],
        ["SecurityTrends", "Graphiques Recharts (area, donut, bar chart)"],
        ["PipelineView", "Visualisation CI/CD avec 8 stages et gate de securite"],
        ["ReportSection", "Telechargement de rapports (PDF/CSV/JSON)"],
    ]
    story.append(make_table(["Composant", "Description"], components,
                            [4*cm, 11*cm]))

    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("6.2 Integration Backend - Graceful Fallback", styles['H2']))
    story.append(Paragraph(
        "Le frontend utilise un mecanisme de fallback transparent : il tente d'abord de charger "
        "les donnees depuis le backend (ms_gestion). Si le backend est indisponible, il utilise "
        "les donnees statiques de demonstration. Un badge indique la source des donnees.",
        styles['Body']))
    fallback_steps = [
        "1. useEffect() appelle loadFromBackend() au montage du composant",
        "2. fetchDashboard() et fetchVulnerabilities() sont appeles en parallele (Promise.all)",
        "3. Si les reponses sont valides : mise a jour du state avec les donnees live",
        "4. Si erreur ou backend down : le state conserve les donnees statiques initiales",
        "5. Badge affiche : 'Live - ms_gestion' (vert) ou 'Static Data' (jaune)",
    ]
    for s in fallback_steps:
        story.append(Paragraph(s, styles['Body']))

    # ── 7. RAPPORT PDF ──
    story.append(Paragraph("7. Rapport PDF de Securite", styles['H1']))
    story.append(Paragraph(
        "Le rapport PDF est genere par SecurityReportService (ms-execution) utilisant iTextPDF 7. "
        "Il est disponible par scan individuel ou consolide par projet.",
        styles['Body']))
    pdf_sections = [
        ["Page de couverture", "Identifiant du scan, moteur, statut, niveau de risque global"],
        ["Resume executif", "Tableau KPI (Total/Critical/High/Medium/Low/Info) + barre de severite"],
        ["Configuration", "Moteur, duree, lignes/fichiers analyses, branche, commit"],
        ["Tableau des vulns", "Vue tabulaire de toutes les findings avec severite et CWE"],
        ["Findings detailles", "Cards pour chaque vuln Critical/High avec snippet et recommandation"],
        ["Recommandations", "Priorisation par severite + distribution OWASP Top 10"],
    ]
    story.append(make_table(["Section", "Contenu"], pdf_sections,
                            [4*cm, 11*cm]))

    # ── 8. PREREQUIS ──
    story.append(PageBreak())
    story.append(Paragraph("8. Prerequis d'Installation", styles['H1']))
    prereqs = [
        ["Semgrep", "pip install semgrep", ">= 1.0"],
        ["Docker", "docker.com/get-docker", ">= 20.x"],
        ["OWASP ZAP", "docker pull zaproxy/zap-stable", "Latest"],
        ["Maven", "Systeme ou wrapper (mvnw)", ">= 3.8"],
        ["PostgreSQL", "Docker ou natif", "15"],
        ["Java", "JDK 17+", "17"],
    ]
    story.append(make_table(["Composant", "Installation", "Version"], prereqs,
                            [4*cm, 7*cm, 4*cm]))

    # ── 9. SEQUENCE SAST ──
    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("9. Diagramme de Sequence - Scan SAST", styles['H1']))
    sast_seq = [
        "User --> Frontend         : Cliquer 'Lancer SAST'",
        "Frontend --> ms-execution : POST /api/security/scan/sast?projectId=1&environmentId=1",
        "ms-execution --> DB       : INSERT security_scans (status=RUNNING)",
        "ms-execution --> Frontend : 202 Accepted {scanRef: 'SCAN-A1B2C3D4'}",
        "ms-execution --> GitHub   : git clone (JGit + PAT authentication)",
        "GitHub --> ms-execution   : Repository clone",
        "ms-execution --> Semgrep  : semgrep scan --config auto --json",
        "Semgrep --> ms-execution  : JSON results (findings[])",
        "ms-execution --> Parse    : findings[] --> SecurityVulnerability[]",
        "ms-execution --> DB       : INSERT security_vulnerabilities (N lignes)",
        "ms-execution --> DB       : UPDATE security_scans (status=COMPLETED)",
        "",
        "--- Telechargement du rapport ---",
        "User --> Frontend         : Cliquer 'Telecharger Rapport'",
        "Frontend --> ms-execution : GET /api/security/report/{scanId}",
        "ms-execution --> DB       : SELECT scan + vulnerabilities",
        "ms-execution --> iTextPDF : Generation PDF",
        "ms-execution --> Frontend : PDF binary",
        "Frontend --> User         : Telechargement du fichier",
    ]
    for line in sast_seq:
        story.append(Paragraph(line, styles['SeqLine']))

    # ── 10. SEQUENCE DAST ──
    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("10. Diagramme de Sequence - Scan DAST", styles['H1']))
    dast_seq = [
        "User --> Frontend         : Cliquer 'Lancer DAST'",
        "Frontend --> ms-execution : POST /api/security/scan/dast?projectId=1&environmentId=1",
        "ms-execution --> DB       : SELECT environment (baseUrlApi)",
        "ms-execution --> DB       : INSERT security_scans (status=RUNNING, targetUrl=...)",
        "ms-execution --> Frontend : 202 Accepted",
        "ms-execution --> Docker   : docker run zaproxy/zap-stable zap-baseline.py -t <url>",
        "Docker/ZAP --> Target App : HTTP Spider + Passive Scan + Active Scan",
        "Target App --> Docker/ZAP : Responses",
        "Docker/ZAP --> ms-exec    : JSON report (site.alerts[])",
        "ms-execution --> Parse    : alerts[] --> SecurityVulnerability[]",
        "ms-execution --> DB       : INSERT security_vulnerabilities",
        "ms-execution --> DB       : UPDATE security_scans (status=COMPLETED)",
    ]
    for line in dast_seq:
        story.append(Paragraph(line, styles['SeqLine']))

    # ── 11. CLASSIFICATION ──
    story.append(PageBreak())
    story.append(Paragraph("11. Classification des Vulnerabilites", styles['H1']))

    story.append(Paragraph("11.1 Mapping CWE vers OWASP Top 10 (2021)", styles['H2']))
    cwe_map = [
        ["CWE-89", "SQL Injection", "A03 - Injection"],
        ["CWE-79", "Cross-Site Scripting (XSS)", "A03 - Injection"],
        ["CWE-78", "OS Command Injection", "A03 - Injection"],
        ["CWE-798", "Hardcoded Credentials", "A07 - Authentication Failures"],
        ["CWE-327", "Broken Cryptography", "A02 - Cryptographic Failures"],
        ["CWE-22", "Path Traversal", "A01 - Broken Access Control"],
        ["CWE-502", "Unsafe Deserialization", "A08 - Software Integrity Failures"],
        ["CWE-918", "SSRF", "A10 - SSRF"],
        ["CWE-352", "CSRF", "A05 - Security Misconfiguration"],
        ["CWE-611", "XXE", "A05 - Security Misconfiguration"],
    ]
    story.append(make_table(["CWE", "Vulnerabilite", "OWASP Top 10"], cwe_map,
                            [2.5*cm, 6*cm, 6.5*cm]))

    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph("11.2 Mapping des severites", styles['H2']))

    sev_semgrep = [
        ["ERROR", "HIGH"],
        ["WARNING", "MEDIUM"],
        ["INFO", "LOW"],
    ]
    story.append(Paragraph("Semgrep vers modele interne :", styles['H3']))
    story.append(make_table(["Semgrep Severity", "Severite Interne"], sev_semgrep,
                            [5*cm, 5*cm]))

    story.append(Spacer(1, 0.3*cm))
    sev_zap = [
        ["3 (High)", "HIGH"],
        ["2 (Medium)", "MEDIUM"],
        ["1 (Low)", "LOW"],
        ["0 (Informational)", "INFO"],
    ]
    story.append(Paragraph("ZAP Risk Code vers modele interne :", styles['H3']))
    story.append(make_table(["ZAP Risk Code", "Severite Interne"], sev_zap,
                            [5*cm, 5*cm]))

    story.append(Spacer(1, 0.3*cm))
    sev_cvss = [
        ["9.0 - 10.0", "CRITICAL"],
        ["7.0 - 8.9", "HIGH"],
        ["4.0 - 6.9", "MEDIUM"],
        ["0.1 - 3.9", "LOW"],
    ]
    story.append(Paragraph("CVSS Score vers modele interne (SCA) :", styles['H3']))
    story.append(make_table(["Score CVSS", "Severite Interne"], sev_cvss,
                            [5*cm, 5*cm]))

    # ── 12. CONCLUSION ──
    story.append(PageBreak())
    story.append(Paragraph("12. Conclusion", styles['H1']))
    story.append(Paragraph(
        "Le module Security Center transforme la plateforme d'un simple outil d'automatisation "
        "de tests en une solution complete de DevSecOps. L'integration de Semgrep (SAST), "
        "OWASP ZAP (DAST), et OWASP Dependency-Check (SCA) permet une couverture de securite "
        "a 360 degres : analyse du code source, des applications en production, et des dependances tierces.",
        styles['Body']))
    story.append(Paragraph(
        "Les resultats sont centralises dans PostgreSQL via des entites JPA partagees entre "
        "ms-execution (ecriture des resultats de scan) et ms_gestion (lecture et agregation pour "
        "le dashboard). Le frontend Next.js offre un dashboard interactif avec 9 onglets couvrant "
        "tous les aspects de la securite : vue d'ensemble, SAST, DAST, OWASP Top 10, gestion des "
        "vulnerabilites, conformite, tendances, pipeline CI/CD, et rapports.",
        styles['Body']))
    story.append(Paragraph(
        "L'architecture microservices permet une execution asynchrone des scans sans bloquer "
        "l'interface utilisateur, et le mecanisme de graceful fallback assure que le dashboard "
        "reste fonctionnel meme lorsque le backend est indisponible.",
        styles['Body']))
    story.append(Spacer(1, 1*cm))

    # Architecture summary table
    summary = [
        ["Couche", "Technologie", "Role"],
        ["Frontend", "Next.js 16 + Recharts + shadcn/ui", "Dashboard interactif, declenchement des scans"],
        ["API Gateway", "Next.js API Routes", "Proxy vers ms-execution et ms_gestion"],
        ["Execution", "Spring Boot (ms-execution)", "Clone Git, lancement des outils, parsing, PDF"],
        ["Gestion", "Spring Boot (ms_gestion)", "Dashboard KPIs, CRUD vulnerabilites, conformite"],
        ["Stockage", "PostgreSQL 15", "Tables security_scans, security_vulnerabilities"],
        ["SAST", "Semgrep CLI", "Analyse statique du code source"],
        ["DAST", "OWASP ZAP (Docker)", "Analyse dynamique des applications web"],
        ["SCA", "OWASP Dependency-Check", "Audit des dependances Maven"],
        ["Rapport", "iTextPDF 7", "Generation de rapports PDF professionnels"],
    ]
    data = [[Paragraph(c, styles['TableHeader'] if i == 0 else styles['TableCell'])
             for c in row] for i, row in enumerate(summary)]
    st = Table(data, colWidths=[3*cm, 5.5*cm, 6.5*cm])
    st_style = [
        ('BACKGROUND', (0, 0), (-1, 0), PRIMARY),
        ('TEXTCOLOR', (0, 0), (-1, 0), WHITE),
        ('GRID', (0, 0), (-1, -1), 0.5, BORDER_C),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]
    for i in range(2, len(data), 2):
        st_style.append(('BACKGROUND', (0, i), (-1, i), LIGHT_BG))
    st.setStyle(TableStyle(st_style))
    story.append(st)

    # Build
    doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
    print(f"PDF generated: {OUTPUT}")

if __name__ == "__main__":
    build()
