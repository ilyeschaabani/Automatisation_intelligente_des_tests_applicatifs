# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Intelligent Test Automation Platform (PFE project). A microservices system that automates unit, integration, and E2E testing with AI-driven test generation using a local LLM (deepseek-coder:6.7b via Ollama).

## Architecture

```
Frontend (Next.js 16, port 3000)
    ↓ REST API calls (proxied via Next.js API routes)
    ↓
┌─────────────────────────────────────────────┐
│ Backend Microservices (Spring Boot, Java 17) │
├─────────────────┬───────────────────────────┤
│ Auth (8081)     │ JWT, GitHub OAuth,        │
│                 │ GitHub API proxy           │
├─────────────────┼───────────────────────────┤
│ ms_gestion      │ Projects, environments,   │
│ (8082)          │ suites, test cases, LLM   │
│                 │ generation, campaigns,     │
│                 │ Swagger/OpenAPI (springdoc)│
├─────────────────┼───────────────────────────┤
│ ms-execution    │ Campaign execution, Maven  │
│ (8083)          │ test runner, PDF reports,  │
│                 │ KPIs, Surefire parsing,    │
│                 │ AI failure analysis        │
├─────────────────┼───────────────────────────┤
│ Eureka (8761)   │ Service discovery          │
└─────────────────┴───────────────────────────┘
    ↓
PostgreSQL 15 (port 5432, DB: Test_platform_db)
Ollama LLM (port 11434, model: deepseek-coder:6.7b)
```

All microservices share a single PostgreSQL database. ms_gestion uses `ddl-auto=update` (creates columns). ms-execution uses `ddl-auto=validate` (requires manual ALTER TABLE for new columns).

## Build & Run Commands

### Infrastructure
```bash
cd infrastructure && docker-compose up -d   # PostgreSQL + PgAdmin
```

### Backend (each in its own terminal)
```bash
cd Backend/EurekaServeur && mvn spring-boot:run              # Start first
cd Backend/AuthenticationMicroservice && mvn spring-boot:run  # Start second
cd Backend/ms_gestion && mvn spring-boot:run
cd Backend/ms-execution && mvn spring-boot:run
```

### Frontend
```bash
cd Frontend && npm install && npm run dev    # Turbopack, port 3000
```

### Tests
```bash
cd Frontend && npm run test                  # Vitest
cd Backend/ms_gestion && mvn test            # JUnit/TestNG
cd Backend/ms-execution && mvn test
```

## Key Workflow: How Tests Are Created and Executed

### Configuration hierarchy (all config lives on Environment, not on Project or Suite)
```
Project  → name + description only
Environment → baseUrlWeb, baseUrlApi, gitRepoUrl, gitBranch, databaseType
TestSuite → name + type (UNIT|INTEGRATION|WEB) + modulePath (optional)
TestCase → title + generated code (AI) or scriptPath (manual)
Campaign → project + environment + selected test cases → Run
```

### Test creation with M1 wizard (UNIT/INTEGRATION)
1. Frontend wizard browses source files via GitHub API (`/api/github/repos/{owner}/{repo}/java-files`)
2. User selects a class → frontend extracts skeleton client-side (~15 lines, no clone)
3. **ScenarioBuilder** (new M1 flow) guides the user:
   - Step 1: Select method from skeleton (dropdown of parsed public methods)
   - Step 2: Select scenario (HAPPY_PATH / EXCEPTION / NULL_INPUT / WRONG_INPUT / BOUNDARY)
   - Step 3: LLM generates test data JSON based on Swagger REQUEST schema → user validates/edits
   - Step 4: Assertions auto-built from Swagger RESPONSE schema + validated test data
4. Structured payload (`methodName`, `scenarioType`, `expectedBehavior`) + skeleton + testData sent to `/api/llm/generate-test`
5. LLM generates precise, targeted test → user edits → validates → saves

### Test execution flow (ms-execution)
1. `ExecutionService.runCampaign(campaignId, selectedTestCaseIds)` clones `env.gitRepoUrl` @ `env.gitBranch`
2. GitHub PAT authentication: token embedded in URL (`https://oauth2:TOKEN@github.com/...`)
3. For UNIT/INTEGRATION: writes generated code into `src/test/java/suites/unit/` (or `suites/integration/`)
4. Injects dependencies into pom.xml (Mockito 5.7, TestNG 7.9, H2, Testcontainers, etc.)
5. Runs `mvnw test -Dtest=suites.unit.ClassName -Dsurefire.provider=testng`
6. Parses `target/surefire-reports/TEST-*.xml` for per-method results
7. Calls LLM for AI analysis of failures (with error type classification)
8. Stores results in `execution_results` table
9. Auto-generates PDF report via `ReportStorageService`

### Key execution features
- **active=false**: test cases are skipped
- **flaky=true**: failed tests are retried once
- **maxDurationSeconds**: per-test Maven timeout (fallback: 30 min global)
- **springProfile**: injected as `-Dspring.profiles.active={value}`
- **testData**: written to temp file, passed as `-Dtest.data.file=path`
- **databaseType**: resolved from test case first, then environment
- **selective run**: `runMode=SELECTED` + `testCaseIds=[...]` runs only specified tests
- **campaign test management**: add/remove test cases from existing campaigns (blocked while RUNNING)

## LLM Prompt Rules (LlmService.java)

Tests generated for UNIT must use `package suites.unit;`. Tests for INTEGRATION must use `package suites.integration;`. This is because the generated file is written into `src/test/java/suites/{type}/` of the cloned source project. Imports must be full-qualified from the source project's packages.

UNIT tests use TestNG + Mockito (no Spring context). INTEGRATION tests use TestNG + `@SpringBootTest` + `AbstractTestNGSpringContextTests` + H2/Testcontainers.

### Structured prompt (M1 mechanism)
When `methodName` + `scenarioType` are provided, the prompt includes a `scenarioBlock`:
- Specifies exact method to test, scenario label, expected behavior
- Forces test method naming: `test_{methodName}_{scenarioType}()`
- Generates exactly ONE @Test method per scenario

### UNIT prompt rules
- `@InjectMocks` without `new()` (Mockito instantiates)
- Mandatory `@BeforeMethod` with `MockitoAnnotations.openMocks(this)`
- If `SecurityUtils.getCurrentUserId()` is used → mandatory `MockedStatic<SecurityUtils>` with `@AfterMethod` teardown
- Must mock ALL repositories used by the method (findById, save, existsBy...)
- Must use testData values in request setters

### Token cleanup
LLM output is post-processed to remove:
- Markdown code blocks (` ```java `, ` ``` `)
- Deepseek special tokens (`<｜begin▁of▁sentence｜>`, `<｜end▁of▁sentence｜>`)

The LLM runs on a constrained GPU (4GB VRAM). Skeleton extraction is capped at 1500 chars. testData and skeleton are optional prompt sections.

## AI Failure Analysis (LlmAnalysisService.java)

When a test fails, `LlmAnalysisService` analyzes the failure:
1. `extractRelevantErrors()`: extracts only `[ERROR]`, `cannot find symbol`, `AssertionError`, `NullPointerException` lines instead of first N chars
2. `detectErrorType()`: classifies as COMPILATION_ERROR, ASSERTION_FAILURE, NULL_POINTER, TIMEOUT, RUNTIME_EXCEPTION
3. Prompt includes error type label and forces structured response: `**Cause** / **Correction** / **Conseil**`

## PDF Report (ReportService.java)

- **Cover page**: project name, version, environment, status badge
- **Executive summary**: KPI table (total/success/failures/errors/duration) + horizontal progress bar gauge (green/orange/red)
- **Context**: project info, suites, test cases, environment details (repo, branch, DB type)
- **Detailed results**: per-test card with error type classification + first meaningful error line
- **AI Analysis**: extracted error lines (not arbitrary tail) + structured AI analysis
- **Metrics**: top 5 slowest tests, average duration by type (bar chart)
- **Flaky tests**: tests marked as unstable
- **Recommendations**: full AI analysis per failed test (not truncated)
- **Appendix**: generated AI scripts

### Report data fetching
Reports use `/api/internal/` endpoints in ms_gestion (no SecurityUtils checks) for service-to-service calls. This avoids auth failures when the report is auto-generated at end of campaign (no user context).

## Frontend Architecture

- **App Router** (Next.js 16): pages under `Frontend/app/`
- **API Routes** proxy to backend services (auth at 8081, ms_gestion at 8082, ms_execution at 8083)
- **`apiFetch()`** (in `components/profile/GitHubIntegrationCard.tsx`): authenticated fetch using `credentials: 'include'` against `NEXT_PUBLIC_API_URL`
- **State**: cookie-based JWT auth (access_token, refresh_token). No Redux/Zustand.
- **Key components**:
  - `SourceClassWizard` (3-step class browser via GitHub API)
  - `ScenarioBuilder` (M1: method selector → scenario → test data → assertions)
  - `CampaignCard`, `FormDialog`, `ConfirmDialog`
- **Types**: `Frontend/types/ms-gestion.ts` for all domain types
- **Services**: `Frontend/services/` for axiosClient-based CRUD; `Frontend/lib/api-client.ts` for fetch-based calls

### Swagger integration
- ms_gestion exposes `/v3/api-docs` via springdoc-openapi
- Frontend proxy at `/api/llm/swagger?schema=TestCaseResponse` returns field definitions (name, type, enum, required)
- Used by ScenarioBuilder to auto-fill assertion checklists and generate test data

## Database Schema Notes

When adding new columns to entities in ms-execution, you must run `ALTER TABLE` manually (ddl-auto=validate). ms_gestion auto-creates columns (ddl-auto=update).

The `environments` table has `git_repo_url`, `git_branch`, `database_type` (added during refactor — the old `variables` JSONB column is deprecated).

The `test_cases.test_data` column is JSONB in PostgreSQL — ms-execution entity must use `@Column(columnDefinition = "JSONB")` with `@JdbcTypeCode(SqlTypes.JSON)`, not `TEXT`.

New columns added: `test_cases.git_repo_url`, `test_cases.target_class_name` (auto-created by ms_gestion ddl-auto=update).

## Important Conventions

- Backend entities in ms-execution are **read-only mirrors** of ms_gestion tables (same DB). They use plain getters/setters, not Lombok.
- ms_gestion entities use Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`).
- All test frameworks in generated code use **TestNG** (not JUnit). The Surefire plugin is configured for TestNG.
- GitHub API calls go through the Auth service's `GitHubClient` (WebClient with `InsecureTrustManagerFactory` for dev SSL).
- GitHub clone auth uses PAT embedded in URL: `https://oauth2:{token}@github.com/...` (both ms-execution and ms_gestion SkeletonExtractorService).
- `github.token` property in both `ms-execution/application.properties` and `ms_gestion/application.properties`.
- Environment select dropdowns show environment config summary (repo @ branch, DB type) inline.

## What Was Done — Session History

### Phase 1: Workflow refactor (prior session)
- Project simplified to name + description only (removed gitRepoUrl/gitBranch)
- Environment now holds gitRepoUrl, gitBranch, databaseType
- Suite types: UNIT, INTEGRATION, WEB
- Campaign: removed gitBranch override — branch comes from environment only

### Phase 2: Execution engine fixes (prior session)
- Skip inactive tests, retry flaky, per-test timeout, springProfile, testData file, appVersion, databaseType fallback, Surefire XML parsing

### Phase 3: AI generation improvements (prior session)
- SkeletonExtractorService, Source class wizard, testData/skeleton in prompt, force correct package/imports

### Phase 4: Campaign run pipeline fixes (current session)
- **Run route fix**: Frontend `/api/campaigns/[id]/Run` was pointing to ms_gestion (8082) instead of ms-execution (8083), and using wrong URL path `/api/campaigns/{id}/run` instead of `/api/execution/run/{id}`
- **GitHub clone auth**: JGit needed PAT for private repos — added `github.token` config and URL-embedded auth (`https://oauth2:TOKEN@github.com/...`)
- **iTextPDF NaN fix**: bar chart width capped at [5%, 95%] to avoid division by zero
- **Temp directory cleanup**: cleared ~317 MB of orphaned clone dirs in `C:\tmp\ms-execution`

### Phase 5: Report quality improvements (current session)
- **Broken gauge replaced**: canvas-based arc → flow-based horizontal progress bar (no coordinate issues)
- **AI analysis improved**: `extractRelevantErrors()` extracts actual [ERROR] lines; `detectErrorType()` classifies error type; structured prompt forces **Cause/Correction/Conseil** format
- **Report shows real names**: new `InternalReportController` in ms_gestion with `/api/internal/` endpoints (no auth checks) — resolves "Campagne #19 / Projet #17" fallback names
- **Detailed results**: shows `[COMPILATION_ERROR] actual error message` instead of generic "Maven exit code: 1"
- **Recommendations**: full AI analysis (not truncated at 200 chars)
- **Log extract in PDF**: shows extracted error lines instead of arbitrary tail

### Phase 6: M1 — Structured test generation wizard (current session)
- **GenerateTestRequest**: added `methodName`, `scenarioType`, `expectedBehavior` fields
- **LlmService**: `scenarioBlock` in prompt with method target + scenario label + expected behavior; `resolveScenarioLabel()` maps scenario types to French descriptions
- **TestDataGeneratorService** (new): generates realistic test data JSON via LLM based on Swagger REQUEST schema fields + scenario type (HAPPY_PATH→valid values, NULL_INPUT→one required field null, WRONG_INPUT→value outside enum/constraints, BOUNDARY→limit values, EXCEPTION→trigger context)
- **LlmController**: new endpoint `POST /api/llm/generate-testdata`
- **Swagger integration**: springdoc-openapi added to ms_gestion; frontend proxy at `/api/llm/swagger?schema=Name` returns field definitions
- **ScenarioBuilder component**: 4-step wizard (method → scenario → test data with LLM generation + human validation → assertions from Swagger + testData)
- **Token cleanup**: filters deepseek special tokens (`<｜begin▁of▁sentence｜>`) from LLM output

### Phase 7: LLM prompt hardening (current session)
- `@InjectMocks` without `new()` rule
- Mandatory `MockedStatic<SecurityUtils>` pattern with `@BeforeMethod`/`@AfterMethod`
- Mock ALL repositories used by the method
- Use testData values in request setters

### Phase 8: Campaign test case management (current session)
- **3 new endpoints in ms_gestion**: `GET .../available-testcases`, `POST .../testcases` (add), `DELETE .../testcases/{id}` (remove)
- **Blocked while RUNNING**: cannot add/remove tests from a running campaign
- **ms-execution selective run**: `runCampaign(id, selectedTestCaseIds)` — filters to specified IDs if provided
- **ExecutionController**: accepts `{runMode: "ALL"}` or `{runMode: "SELECTED", testCaseIds: [...]}` in POST body
- **Frontend campaign page**: "Retirer" button per test case; "Ajouter des tests" section with available tests from project; Run Mode Dialog (All vs Selected)

## What's Left To Do

### High Priority
- **Commit all changes**: working tree is weeks ahead of committed code — LLM generates tests against committed repo, causing compilation mismatches (e.g. SkeletonExtractorService not found, 4-arg generateTestCode not found)
- **Integration test support**: INTEGRATION tests need different prompt rules (SpringBootTest, H2 config, no Mockito for repos) — the prompt exists but needs testing with actual execution
- **M3 — Auto-retry on compilation error**: if Maven returns compilation failure, auto-send code + error back to LLM for self-correction (zero manual intervention)

### Medium Priority
- **M2 — Batch multi-scenario generation**: select multiple scenarios at once → generates one test class with multiple @Test methods
- **M4 — Frontend code validation**: check for @BeforeMethod/openMocks, correct package, @Test presence before saving
- **Maven wrapper fallback**: if cloned repo has no `mvnw`, fall back to system `mvn`
- **Campaign diff**: "3 new failures vs previous campaign" comparison
- **ScenarioBuilder for edit dialog**: currently only works in create flow, not when editing existing test cases

### Lower Priority
- **Real E2E tests**: currently only AI template for WEB — could add Playwright support
- **Production readiness**: switch ms-execution to ddl-auto=validate with proper SQL migrations
- **M6 — Test templates**: predefined patterns (Service unit test, Repository integration test, Controller test)
