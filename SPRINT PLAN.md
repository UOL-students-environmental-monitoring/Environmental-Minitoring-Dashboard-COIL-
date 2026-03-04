# 🗂️ Sprint Plan — Environmental Monitoring Dashboard (COMP2850)

> **Language:** Kotlin (backend) + HTML/CSS/JS (frontend)
> **Code style:** [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) — enforced via **Detekt** + **KLint**
> **In-code docs:** KDoc on all public functions and classes
> **Testing philosophy:** TDD where possible. The person who writes code should **not** be the person who writes its tests.
> **Git strategy:** No direct commits to `main`. Feature branches → PR → peer reviewed → merged only when CI passes.
> **GitHub Project Board:** Every task below = a card. Assign it, move it, close it.
> **Deadline:** 17:00 Friday 8th May | Demo: Monday 11th May

-----

## 👥 Roles

|Person |Area                                   |
|-------|---------------------------------------|
|**P1** |Dashboard UI & Layout                  |
|**P2** |Charts & Historical Trends             |
|**P3** |Data Management & Validation           |
|**P4** |Alert System                           |
|**P5** |Analysis & Reporting Portal (Extension)|
|**ALL**|Shared responsibilities (see bottom)   |

-----

## 📅 Week 1 — COIL Workshop + Setup & Planning

> Goal: Agree scope, environment/topic, roles, tools. Set up the repo. First documentation artifacts created. No production code expected yet.

-----

### P1 — Dashboard UI & Layout

**Code / Setup**

- [ ] Set up the frontend project scaffold — folder structure, base `index.html`, linked CSS file, placeholder JS. Push to repo on a `setup/frontend` branch
- [ ] Configure axe DevTools or WAVE browser extension for accessibility checking — document setup in Wiki

**Documentation**

- [ ] Attend COIL workshop. Write up agreed environmental focus (e.g. flood risk, air quality), key stakeholders, and scope decisions → add to Wiki under `Requirements/COIL-Summary`
- [ ] Create **Persona 1**: community resident. Include name, background, goals, frustrations, accessibility needs, tech literacy. Add to Wiki under `Requirements/Personas`
- [ ] Produce **wireframe v1** for the main dashboard page (current readings panel, status indicator). Annotate with: colour choices, contrast rationale, keyboard nav notes, font sizing. Save as `docs/wireframes/dashboard-v1.png` and link from Wiki under `Design/Wireframes`

**Tests**

- [ ] Validate placeholder `index.html` against WCAG using axe/WAVE. Log results (pass/fail per criterion) in Wiki under `Testing/Accessibility-Log`. Fix any Level A failures immediately

-----

### P2 — Charts & Historical Trends

**Code / Setup**

- [ ] Research charting libraries (Chart.js, D3, Recharts). Build a spike: render one dummy time-series chart with hardcoded data in the browser. Push spike to `spike/charting` branch

**Documentation**

- [ ] Attend COIL workshop. Document which environmental metrics will be charted and over what time ranges → add to Wiki under `Requirements/COIL-Summary`
- [ ] Write a charting library justification (what you evaluated, why you chose it, trade-offs) → add to Wiki under `Design/Tech-Decisions`
- [ ] Produce **wireframe v1** for the historical trends view and site/location list. Annotate with layout rationale. Save as `docs/wireframes/trends-v1.png` and link from Wiki under `Design/Wireframes`
- [ ] Draft **Job Stories** for chart/trends features (minimum 2). Format: “When I [situation], I want to [motivation], so I can [outcome].” Add to Wiki under `Requirements/Job-Stories`

**Tests**

- [ ] Document spike results — does the library render correctly? Any performance concerns with large datasets? Log in Wiki under `Testing/Spike-Results`

-----

### P3 — Data Management & Validation

**Code / Setup**

- [ ] Set up the Kotlin backend project scaffold — Gradle build file, Ktor (or chosen framework), folder structure. Push to repo on `setup/backend` branch
- [ ] Add Detekt and KLint to the Gradle build. Confirm they run with `./gradlew detekt`. Push config files
- [ ] Set up GitHub Actions CI workflow (`.github/workflows/ci.yml`) — runs `./gradlew test` + Detekt on every push to every branch

**Documentation**

- [ ] Attend COIL workshop. Identify all required sensor data fields (reading value, unit, timestamp, site ID, sensor type, status) → document in Wiki under `Requirements/Data-Model`
- [ ] Write the **storage approach justification** — evaluate at least 2 options (e.g. SQLite vs flat JSON vs H2). Document chosen approach, why, and how it supports historical trends and alert review → add to Wiki under `Design/Storage-Justification`
- [ ] Produce a **DB/schema diagram** (ERD). Save as `docs/diagrams/schema-v1.png` and link from Wiki under `Design/Database`
- [ ] Create Wiki page `Testing/Test-Setup` — document how to run tests locally (`./gradlew test`)

**Tests**

- [ ] Write unit tests for data model classes (`SensorReading`, `Site`, `SensorMetadata`) — field types, nullability, default values, equality checks

-----

### P4 — Alert System

**Code / Setup**

- [ ] Define the alert data model — `AlertRule`, `AlertEvent`, `AlertSeverity` (enum: NORMAL / WARNING / CRITICAL). Push skeleton classes to `feature/alert-model` branch

**Documentation**

- [ ] Attend COIL workshop. Define at least 3 threshold rules for your chosen environmental focus (e.g. water level > 2m = WARNING, > 3m = CRITICAL). Document in Wiki under `Requirements/Alert-Rules`
- [ ] Document alert severity levels, what triggers each, and the plain-language explanation for each. Add to Wiki under `Design/Alert-Design`
- [ ] Produce **wireframe v1** for the active alerts panel. Annotate with: how severity is visually distinguished, plain-language explanation placement. Save as `docs/wireframes/alerts-v1.png` and link from Wiki under `Design/Wireframes`
- [ ] Draft **Job Stories** for alert features (minimum 2). Add to Wiki under `Requirements/Job-Stories`

**Tests**

- [ ] Write unit tests for threshold rule logic (pure functions, no DB): `evaluateReading(value: Float, rules: List<AlertRule>): AlertSeverity`
  - Below threshold → NORMAL
  - At threshold → WARNING
  - Above critical threshold → CRITICAL
  - Negative value handled
  - Null/missing value handled

-----

### P5 — Analysis & Reporting Portal

**Code / Setup**

- [ ] Set up the portal page scaffold — separate HTML page or route. Push to `feature/portal-scaffold`

**Documentation**

- [ ] Attend COIL workshop. Identify researcher/decision-maker stakeholder needs → add to Wiki under `Requirements/COIL-Summary`
- [ ] Create **Persona 2**: researcher/decision-maker. Include goals, data needs, export requirements, context of use. Add to Wiki under `Requirements/Personas`
- [ ] Produce **wireframe v1** for the reporting portal — date range filter, site filter, trend summary view, CSV export button. Annotate with interaction rationale. Save as `docs/wireframes/portal-v1.png` and link from Wiki under `Design/Wireframes`
- [ ] Write **acceptance criteria** for each portal feature (filters, export, summaries) — these will map directly to tests. Add to Wiki under `Requirements/Acceptance-Criteria`
- [ ] Draft **Job Stories** for the portal (minimum 2). Add to Wiki under `Requirements/Job-Stories`

**Tests**

- [ ] No code tests this week — review and give feedback on P3’s data model to confirm it will support cross-site queries needed by the portal

-----

## 📅 Week 2 — COIL Wrap-Up + Core Implementation Begins

> Goal: Complete COIL deliverables. Full user story backlog created and prioritised. Core features start being built.

-----

### P1 — Dashboard UI & Layout

**Code**

- [ ] Implement the dashboard shell: nav bar, header, status indicator component (NORMAL / WARNING / CRITICAL) with correct ARIA roles and labels
- [ ] Implement responsive layout — confirmed working at 375px (mobile), 768px (tablet), 1280px (desktop)
- [ ] Implement the active alerts panel UI component (static/hardcoded for now — P4 will wire it up in Week 3)

**Documentation**

- [ ] Finalise wireframes based on COIL feedback. Save updated version as `docs/wireframes/dashboard-v2.png`. Add a note to the Wiki entry explaining what changed and why — **do not delete v1**
- [ ] Write **User Stories** for dashboard features. Format: “As a [persona], I want to [action], so that [benefit].” Include story point estimates and MoSCoW priority. Add to Wiki under `Requirements/User-Stories`

**Tests**

- [ ] Write and log a **manual UX test checklist** for keyboard navigation: Tab order correct, focus ring visible, all interactive elements reachable. Run it. Log results and fixes in Wiki under `Testing/UX-Tests`
- [ ] Re-run axe/WAVE on implemented shell. Log results in `Testing/Accessibility-Log`. Zero Level A errors before merging

-----

### P2 — Charts & Historical Trends

**Code**

- [ ] Implement the time-series chart component using agreed library. Wire to mock data matching P3’s agreed data shape
- [ ] Implement the site list view (text list of sites with latest reading per site)
- [ ] Implement location filter — selecting a site updates the chart

**Documentation**

- [ ] Update the GitHub Project Board — all user stories as cards with estimates. Confirm backlog is complete and prioritised with the team
- [ ] Update `Design/Tech-Decisions` Wiki page with final library choice and any changes from Week 1 spike
- [ ] Update wireframe to `trends-v2.png` if design changed. Note what changed and why in Wiki — **do not delete v1**

**Tests**

- [ ] Unit test `mapReadingsToChartData(readings: List<SensorReading>): ChartData`:
  - Correct sort by timestamp
  - Empty list → empty chart, no crash
  - Single data point → renders without error
  - Identical timestamps handled
- [ ] Unit test location filter logic

-----

### P3 — Data Management & Validation

**Code**

- [ ] Implement the storage layer using the chosen approach. Seed with realistic simulated data (minimum 3 sites, 7 days of readings per sensor)
- [ ] Implement validation logic: missing/null values, out-of-range checks, duplicate detection (same sensor ID + same timestamp)
- [ ] Expose `GET /readings?site=X&from=Y&to=Z` endpoint

**Documentation**

- [ ] Update **DB/schema diagram** to reflect implemented schema. Save as `docs/diagrams/schema-v2.png`. Note changes in Wiki — **keep v1**
- [ ] Create **API Reference** page in Wiki under `Design/API-Reference` — document every endpoint: method, path, params, example response, all error codes
- [ ] Produce **class diagram** with implemented backend classes. Save as `docs/diagrams/classes-v1.png` and link from Wiki under `Design/Architecture`

**Tests**

- [ ] Unit tests for each validation rule:
  - Null/missing value → rejected with specific error message
  - Value exactly at range boundary → accepted
  - Value outside range → rejected
  - Duplicate (same sensor ID + timestamp) → rejected
  - Valid reading → stored and retrievable
- [ ] Unit test `GET /readings` — returns correct data for valid site, 404 for unknown site, handles missing query params gracefully

-----

### P4 — Alert System

**Code**

- [ ] Implement the alert engine: reads from P3’s data store, applies threshold rules, assigns severity, writes to alert log
- [ ] Implement alert log/history storage (timestamp, rule triggered, severity, reading value, site)
- [ ] Expose `GET /alerts?site=X&severity=Y` endpoint

**Documentation**

- [ ] Update alert design docs in Wiki with any changes from implementation — note decisions made
- [ ] Add alert data model to the **class diagram** (coordinate with P3 to keep one diagram updated)
- [ ] Write **User Stories** for alert features with acceptance criteria linked to tests. Add to Wiki under `Requirements/User-Stories`

**Tests**

- [ ] Unit tests for alert engine:
  - Reading below threshold → no alert logged
  - Reading at WARNING threshold → WARNING logged
  - Reading above CRITICAL threshold → CRITICAL logged
  - Duplicate alerts not logged for same event
  - Alert log persists correct fields
- [ ] Unit test `GET /alerts` — correct filter by site, correct filter by severity, empty result returns `[]` not an error

-----

### P5 — Analysis & Reporting Portal

**Code**

- [ ] Implement date range filter UI and wire to P3’s `/readings` endpoint
- [ ] Implement site filter UI — reuse P3’s API, do not build a separate data layer
- [ ] Implement filtered results display (table or summary view)

**Documentation**

- [ ] Document agreed data contracts with P3 & P4 in Wiki under `Design/API-Reference` — what fields does the portal consume, what queries does it make
- [ ] Update wireframe to `portal-v2.png` if design changed. Note what changed and why — **do not delete v1**
- [ ] Add portal user stories to Wiki under `Requirements/User-Stories` with acceptance criteria

**Tests**

- [ ] Unit test `filterByDateRange(readings, start, end)`:
  - Correctly excludes out-of-range entries
  - Handles boundary dates (inclusive)
  - Handles empty result
- [ ] Unit test `filterBySite(readings, siteId)` — correct filtering, handles unknown site ID
- [ ] Manual test: apply both filters simultaneously — confirm correct combined result

-----

## 📅 Week 3 — Integration + Polish + Retrospective 1

> Goal: All core features working end-to-end. Integration tested. UX tested with a real person. Retrospective 1 held.

-----

### P1 — Dashboard UI & Layout

**Code**

- [ ] Replace all mock data — integrate live data from P3’s `/readings` endpoint. Current readings panel shows real values with correct status colours
- [ ] Wire active alerts panel to P4’s `/alerts` endpoint. Each alert displays its plain-language explanation
- [ ] Handle API error states gracefully — if API is unreachable, show a user-friendly message (no stack trace, no crash)

**Documentation**

- [ ] Conduct **UX Test 1** — test with at least 1 person outside the group. Prepare a short task list (e.g. “find the current water level”, “identify if there’s an active warning”). Record: tester profile, tasks, observations, issues found, changes made. Add to Wiki under `Testing/UX-Tests`
- [ ] Update wireframe to `dashboard-v3.png` if UX test led to design changes. Note what changed and why in Wiki

**Tests**

- [ ] Integration test: dashboard shows CRITICAL status when P4 returns a critical alert
- [ ] Integration test: dashboard shows user-friendly error message (not a crash) when API returns 500
- [ ] Re-run axe/WAVE. Log results in `Testing/Accessibility-Log`. Zero Level A errors on integrated dashboard before merge

-----

### P2 — Charts & Historical Trends

**Code**

- [ ] Integrate real historical data from P3’s API into chart component
- [ ] Polish chart UI — axis labels, tooltips, readable time formatting, legend
- [ ] Confirm location filter correctly updates chart when site changes

**Documentation**

- [ ] Update `Design/Tech-Decisions` Wiki page with any implementation learnings or changes
- [ ] Update wireframe to `trends-v3.png` if chart UI changed. Note changes in Wiki

**Tests**

- [ ] Integration test: filter by site → chart updates with correct data
- [ ] Integration test: site with no readings → chart shows empty state message, no crash
- [ ] Integration test: single data point → chart renders without error
- [ ] Manual test: chart is readable on mobile (375px). Log result in Wiki under `Testing/UX-Tests`

-----

### P3 — Data Management & Validation

**Code**

- [ ] Handle all remaining edge cases: string sent instead of float, non-existent site ID, malformed timestamps, extremely large values
- [ ] Ensure all API error responses return meaningful messages — no raw exceptions exposed
- [ ] Confirm data store supports cross-site queries needed by P5

**Documentation**

- [ ] Finalise **API Reference** in Wiki — every endpoint documented with all error codes and example payloads
- [ ] Update class diagram and schema diagram if anything changed. Version and note changes in Wiki
- [ ] Write up security considerations for the data layer in Wiki under `Design/Security-Notes`

**Tests**

- [ ] Integration test — full flow: ingest reading → validate → store → retrieve via `GET /readings`
- [ ] Integration test — invalid data rejected with correct HTTP status code and error message body
- [ ] Security test: send a 10,000-character string as a site ID — confirm rejected gracefully
- [ ] Security test: send a negative reading value — confirm validation catches it

-----

### P4 — Alert System

**Code**

- [ ] Coordinate with P1 — confirm alert panel on dashboard is fully wired and displaying correctly
- [ ] Confirm plain-language explanations render per severity on the dashboard
- [ ] Handle edge case: no active alerts → panel shows “No active alerts” (not blank or broken)

**Documentation**

- [ ] **Hold Retrospective 1 (all team).** Document: what went well, what didn’t, action items with owners and deadlines. Add minutes to Wiki under `Meetings/Retrospective-1`
- [ ] Update alert design docs with any changes made during integration

**Tests**

- [ ] End-to-end test: seed a reading that crosses a threshold → alert triggered → alert visible on dashboard with correct severity and explanation
- [ ] End-to-end test: alert history persists — restart the app, alert log is still populated
- [ ] Test: no active alerts → dashboard alert panel shows correct empty state

-----

### P5 — Analysis & Reporting Portal

**Code**

- [ ] Implement cross-site trend summaries (e.g. average reading per site over selected date range, min/max per site)
- [ ] Implement CSV export of filtered data
- [ ] Confirm portal only uses P3’s API — no duplicated data fetching logic

**Documentation**

- [ ] Conduct **UX Test 1 for Portal** — test with at least 1 person. Document findings and any changes made in Wiki under `Testing/UX-Tests`
- [ ] Update portal wireframe to `portal-v3.png` if design changed. Note changes in Wiki

**Tests**

- [ ] Unit test CSV export: correct headers present, correct values, empty result = CSV with headers only (not an error)
- [ ] Integration test: date range + site filter applied simultaneously → exported CSV contains only matching records
- [ ] Manual test: large dataset export — completes without timeout or browser freeze. Log result

-----

## 🤝 Shared Responsibilities (Everyone, Every Week)

|Task                                                                          |Who                      |When                              |
|------------------------------------------------------------------------------|-------------------------|----------------------------------|
|Update GitHub Project Board — move cards, close issues, log bugs              |All                      |Ongoing                           |
|KDoc comments on all public functions/classes you write                       |All                      |When writing code                 |
|Run Detekt + KLint before every PR. Fix all flagged issues                    |All                      |Before every PR                   |
|Weekly team meeting — minutes written, actions assigned with owners           |All (rotate minute-taker)|Weekly                            |
|Complete weekly development diary on Gradescope                               |All                      |Each week (min 5 of 7)            |
|Add AI usage comments inline: `// Used [model] to assist with X — lines Y-Z`  |All                      |When applicable                   |
|Peer-review at least one PR per week                                          |All                      |Weekly                            |
|**Retrospective 1** — minutes + actions in Wiki                               |All                      |End of Week 3                     |
|**Retrospective 2** — minutes + actions in Wiki                               |All                      |End of Week 5                     |
|**Online Check-In 1** via Feedback Fruits                                     |All                      |Within 2-week window — do not miss|
|**Online Check-In 2** via Feedback Fruits                                     |All                      |Within 2-week window — do not miss|
|Start individual reflection notes from Week 1 — use diaries as source material|All                      |Ongoing                           |

-----

## 🧪 Testing Standards (Non-Negotiable)

- **Unit tests:** Every function with logic gets a test. All boundary conditions covered
- **Integration tests:** Every API endpoint tested end-to-end
- **UX/manual tests:** Documented in Wiki with date, tester, tasks, findings, and what changed as a result
- **Accessibility tests:** axe/WAVE run every sprint. Results logged. Zero Level A WCAG errors in final system
- **Security:** All inputs validated server-side. Test malformed, oversized, and out-of-range inputs explicitly
- **CI/CD:** GitHub Actions runs `./gradlew test` + Detekt on every push to every branch — set up in Week 1

-----

## ✅ Definition of Done (Every Task)

A task is only “done” when:

1. Code is on a feature branch — never committed directly to `main`
1. KDoc comments on all public functions
1. Detekt + KLint pass with zero significant issues
1. Tests written and passing
1. PR opened, reviewed and approved by at least one teammate
1. CI passes on the PR
1. Merged to `main`

-----

## 📁 Repo Structure & Starter Files to Create

The following must exist in the repo from **Week 1**. Owner listed per item.

```
/
├── README.md                            ← P3 creates, all contribute
├── SPRINT_PLAN.md                       ← This file
├── .github/
│   └── workflows/
│       └── ci.yml                       ← P3: runs tests + Detekt on every push
├── docs/
│   ├── wireframes/
│   │   ├── dashboard-v1.png             ← P1
│   │   ├── trends-v1.png               ← P2
│   │   ├── alerts-v1.png               ← P4
│   │   └── portal-v1.png              ← P5
│   └── diagrams/
│       ├── schema-v1.png               ← P3
│       └── classes-v1.png             ← P3 (updated by all as needed)
├── backend/                             ← P3 scaffolds (Kotlin/Ktor + Gradle)
├── frontend/                            ← P1 scaffolds (HTML/CSS/JS)
└── [test directories inside backend/]  ← All
```

-----

## 📚 Wiki Pages to Create (GitHub Wiki)

All pages must be created by **end of Week 1** even if empty — fill them as you go.

|Wiki Page                             |Owner     |Path                              |
|--------------------------------------|----------|----------------------------------|
|COIL Summary & Scope                  |All       |`Requirements/COIL-Summary`       |
|Data Model                            |P3        |`Requirements/Data-Model`         |
|Personas                              |P1, P5    |`Requirements/Personas`           |
|Job Stories                           |P2, P4, P5|`Requirements/Job-Stories`        |
|User Stories & Backlog                |All       |`Requirements/User-Stories`       |
|Acceptance Criteria                   |P5 leads  |`Requirements/Acceptance-Criteria`|
|Alert Rules                           |P4        |`Requirements/Alert-Rules`        |
|Wireframes index                      |P1        |`Design/Wireframes`               |
|Storage Justification                 |P3        |`Design/Storage-Justification`    |
|Tech Decisions                        |P2, P3    |`Design/Tech-Decisions`           |
|API Reference                         |P3        |`Design/API-Reference`            |
|Database Diagram                      |P3        |`Design/Database`                 |
|Architecture / Class Diagram          |P3        |`Design/Architecture`             |
|Alert Design                          |P4        |`Design/Alert-Design`             |
|Security Notes                        |P3        |`Design/Security-Notes`           |
|Test Setup                            |P3        |`Testing/Test-Setup`              |
|Accessibility Log                     |P1        |`Testing/Accessibility-Log`       |
|UX Test Records                       |P1, P5    |`Testing/UX-Tests`                |
|Spike Results                         |P2        |`Testing/Spike-Results`           |
|Meeting Minutes (one page per meeting)|Rotating  |`Meetings/Week-X`                 |
|Retrospective 1                       |P4        |`Meetings/Retrospective-1`        |
|Retrospective 2                       |All       |`Meetings/Retrospective-2`        |


> **Critical:** When updating any design document (wireframes, diagrams), keep the old version. Version filenames (v1, v2, v3). Never delete — the rubric explicitly rewards evidence of incremental change with justification.

-----

## ⚠️ Key Risks

|Risk                                |Mitigation                                                   |
|------------------------------------|-------------------------------------------------------------|
|P3 API not ready → blocks P1, P2, P5|P3 provides agreed mock JSON responses by end of Week 1      |
|P5 duplicates P3’s data logic       |P5 must reuse P3’s API only — enforced in PR review          |
|Tests written only at the end       |Tests listed as tasks per week — treat as non-optional       |
|WCAG failures found late            |P1 runs axe/WAVE every sprint and logs results every time    |
|Documentation left to the last week |Wiki pages assigned by name and sprint — create stubs Week 1 |
|Old wireframes deleted              |Versioned filenames — never delete, only add new versions    |
|Main branch broken                  |Branch protection + required CI pass before any merge to main|
