# Environmental Monitoring Dashboard — COMP2850 COIL Project

A full-stack environmental monitoring system built with **Kotlin (Ktor)** on the backend and **HTML/CSS/JS** on the frontend. The system ingests sensor readings, evaluates alert thresholds, and provides a dashboard and reporting portal for community residents and researchers.

---

## Project Overview

This project was developed as part of the COMP2850 COIL (Collaborative Online International Learning) program. It monitors environmental sensor data (e.g. flood risk, air quality, water levels) across multiple sites, surfacing real-time readings, historical trends, and severity-graded alerts.

---

## Team Roles

| Person | Area                                    |
| ------ | --------------------------------------- |
| **P1** | Dashboard UI & Layout                   |
| **P2** | Charts & Historical Trends              |
| **P3** | Data Management & Validation            |
| **P4** | Alert System                            |
| **P5** | Analysis & Reporting Portal (Extension) |

---

## Tech Stack

| Layer         | Technology                              |
| ------------- | --------------------------------------- |
| Backend       | Kotlin + Ktor + Gradle                  |
| Frontend      | HTML / CSS / JavaScript                 |
| Linting       | Detekt + KLint                          |
| In-code docs  | KDoc                                    |
| CI/CD         | GitHub Actions                          |
| Testing       | JUnit (backend unit + integration tests)|
| Accessibility | axe DevTools / WAVE                     |

---

## Repository Structure

```text
/
├── README.md
├── SPRINT_PLAN.md
├── .github/
│   └── workflows/
│       └── ci.yml                  # Runs tests + Detekt on every push
├── docs/
│   ├── wireframes/
│   │   ├── dashboard-v1.png        # P1
│   │   ├── trends-v1.png           # P2
│   │   ├── alerts-v1.png           # P4
│   │   └── portal-v1.png           # P5
│   └── diagrams/
│       ├── schema-v1.png           # P3 — ERD
│       └── classes-v1.png          # P3 — Class diagram
├── backend/                        # Kotlin/Ktor application
└── frontend/                       # Static HTML/CSS/JS
```

---

## Getting Started

### Prerequisites

- JDK 17+
- Gradle (wrapper included)
- A modern browser (for frontend)

### Run the backend

```bash
./gradlew run
```

### Run tests

```bash
./gradlew test
```

### Run linting

```bash
./gradlew detekt
```

### Open the frontend

Open `frontend/index.html` in a browser, or serve it with any static file server.

---

## API Endpoints

| Method | Path                           | Description                                              |
| ------ | ------------------------------ | -------------------------------------------------------- |
| `GET`  | `/readings?site=X&from=Y&to=Z` | Fetch sensor readings for a site within a date range     |
| `GET`  | `/alerts?site=X&severity=Y`    | Fetch alerts filtered by site and/or severity            |

Full API documentation is maintained in the GitHub Wiki under `Design/API-Reference`.

---

## Alert Severity Levels

| Severity   | Meaning                                  |
| ---------- | ---------------------------------------- |
| `NORMAL`   | Reading within safe bounds               |
| `WARNING`  | Reading approaching a critical threshold |
| `CRITICAL` | Reading has exceeded a critical threshold|

Threshold rules for the chosen environmental focus are documented in the Wiki under `Requirements/Alert-Rules`.

---

## Key Features

- **Live dashboard** — current sensor readings with colour-coded severity status
- **Historical trends** — interactive time-series charts with site/location filtering
- **Active alerts panel** — plain-language alert explanations per severity level
- **Reporting portal** — date range and site filters, cross-site summaries, CSV export
- **Data validation** — server-side checks for null values, out-of-range inputs, duplicates, and malformed data
- **Accessibility** — WCAG 2.1 Level AA target; tested every sprint with axe/WAVE; keyboard navigable

---

## Development Workflow

- **No direct commits to `main`** — all changes via feature branches and pull requests
- Every PR requires at least one peer review and must pass CI before merge
- Run `./gradlew detekt` before opening a PR — all linting issues must be resolved
- KDoc comments required on all public functions and classes
- AI usage must be noted inline: `// Used [model] to assist with X — lines Y-Z`

---

## Testing

| Type                | Scope                                                           |
| ------------------- | --------------------------------------------------------------- |
| Unit tests          | Every function with logic; all boundary conditions              |
| Integration tests   | Every API endpoint tested end-to-end                            |
| UX / manual tests   | Documented in Wiki with date, tester, tasks, findings           |
| Accessibility tests | axe/WAVE run every sprint; zero Level A WCAG errors required    |
| Security tests      | Malformed, oversized, and out-of-range inputs tested explicitly |

---

## Definition of Done

A task is only complete when:

1. Code is on a feature branch — never committed directly to `main`
2. KDoc comments on all public functions
3. Detekt + KLint pass with zero significant issues
4. Tests written and passing
5. PR opened, reviewed, and approved by at least one teammate
6. CI passes on the PR
7. Merged to `main`

---

## Documentation

All design and requirements documentation lives in the **GitHub Wiki**:

- `Requirements/COIL-Summary` — agreed scope and environmental focus
- `Requirements/Data-Model` — sensor data fields and schema
- `Requirements/Personas` — community resident and researcher personas
- `Requirements/User-Stories` — full backlog with MoSCoW priority and story points
- `Design/API-Reference` — all endpoints with example requests and error codes
- `Design/Architecture` — class diagram
- `Design/Database` — ERD / schema diagram
- `Design/Wireframes` — all wireframe versions (never deleted — versioned as v1, v2, v3)
- `Testing/Accessibility-Log` — axe/WAVE results per sprint
- `Testing/UX-Tests` — user test records

---

## Key Dates

| Milestone           | Date               |
| ------------------- | ------------------ |
| Submission deadline | 17:00 Friday 8 May |
| Demo                | Monday 11 May      |
| Retrospective 1     | End of Week 3      |
| Retrospective 2     | End of Week 5      |

---

## Key Risks & Mitigations

| Risk                                      | Mitigation                                                        |
| ----------------------------------------- | ----------------------------------------------------------------- |
| P3 API not ready — blocks P1, P2, P5      | P3 provides agreed mock JSON responses by end of Week 1           |
| P5 duplicating P3's data logic            | P5 must reuse P3's API only — enforced in PR review               |
| Tests written only at the end             | Tests are listed as tasks per week — treated as non-optional      |
| WCAG failures found late                  | P1 runs axe/WAVE every sprint and logs results                    |
| Documentation left to the last week       | Wiki pages assigned by name and sprint — stubs created in Week 1  |
| Main branch broken                        | Branch protection + required CI pass before any merge to `main`   |
