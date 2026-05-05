# Environmental Monitoring Dashboard — COMP2850 COIL Project

A full-stack environmental monitoring system for **livestock farm management**, built with **Kotlin (Ktor)** and **HTML/CSS/JavaScript** served directly by Ktor. The current application ingests livestock sensor readings, seeds an H2 database from the bundled `livestock_tracking.csv` dataset, evaluates welfare alerts, and serves dashboard pages for farm monitoring.

---

## Project Context

Developed as part of the **COMP2850 COIL (Collaborative Online International Learning)** programme.

The current dataset and implementation focus on livestock welfare signals: herd GPS position, accelerometer movement, ambient temperature, derived status, and alert flags. The app currently supports two seeded monitoring sites: `herd_cattle_A` and `herd_goat_B`, with readings loaded from `ktor-sample/src/main/resources/livestock_tracking.csv`.

### Personas

| Persona | Role | Core Need |
|---------|------|-----------|
| **Tom Hargreaves**, 58 | Arable & Livestock Farmer, Yorkshire | At-a-glance signal (safe / borderline / dangerous) — no jargon, works on mobile in the field |
| **Priya Nair**, 29 | Agricultural Field Officer | Multi-site overview, historical trends, date/location filters, CSV export for reporting |

Full persona detail: [`docs/Personas.md`](docs/Personas.md)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin + Ktor + Gradle |
| Frontend | HTML / CSS / JavaScript (ES Modules) — served by Ktor as static assets |
| Charts | Chart.js CDN is used by the trends page assets |
| Database | H2 in-memory database for the current app; PostgreSQL connection settings are present in config |
| ORM | Exposed |
| Linting | Detekt + KtLint are documented as desired gates but are not currently wired into Gradle |
| In-code docs | KDoc/comments are present in the Kotlin and frontend files |
| CI/CD | GitHub Actions placeholder workflow |
| Testing | Kotlin test suite through Gradle |
| Accessibility | Dashboard includes a skip link, ARIA labels/roles, keyboard-focus states, and responsive layout work |

---

## Repository Structure

```text
/
├── README.md
├── SPRINT PLAN.md
├── docs/
│   ├── Personas.md
│   ├── wireframes/                       # Versioned — never delete old versions
│   └── diagrams/                         # ERD and class diagrams — versioned
├── frontend/                             # Not the active frontend root
└── ktor-sample/                          # Active Ktor application
    ├── gradlew / build.gradle.kts / settings.gradle.kts
    ├── gradle/
    └── src/
        ├── main/kotlin/
        │   ├── Application.kt            # Ktor entry point
        │   ├── Routing.kt                # API routes + static asset serving
        │   ├── Serialization.kt          # JSON config
        │   ├── Templating.kt             # Thymeleaf setup/sample route
        │   ├── Models.kt                 # Sites, LivestockReadings, AlertsLog schemas
        │   ├── DataTransferObjects.kt    # LivestockPayload, AlertDTO, ReadingDTO
        │   ├── Databases.kt              # H2 setup, seeded sites, CSV import
        │   └── AlertEngine.kt            # Livestock alert rules
        └── main/resources/
            ├── application.yaml
            ├── livestock_tracking.csv    # 140,154 readings across two herd sites
            ├── logback.xml
            ├── static/                   # Static frontend served by Ktor
            │   ├── index.html            # Dashboard
            │   ├── alerts.html           # Alerts page
            │   ├── css/
            │   │   ├── style.css
            │   │   └── trends.css
            │   └── js/
            │       ├── api.js            # Dashboard API wrapper
            │       ├── main.js           # Dashboard rendering logic
            │       └── trends.js         # Chart.js trends logic
            └── templates/thymeleaf/
                ├── index.html
                └── trends.html           # Trends template asset; route is not currently registered
```

---

## Team Roles

| Person | Area |
|--------|------|
| **P1** | Dashboard UI & Layout (`ktor-sample/src/main/resources/static/index.html`) |
| **P2** | Charts & Historical Trends (`/trends`, `templates/thymeleaf/trends.html`, `static/js/trends.js`, `static/css/trends.css`) |
| **P3** | Data Management & Validation (`Models.kt`, `Databases.kt`, `Routing.kt`) |
| **P4** | Alert System (`static/alerts.html`, `AlertEngine.kt`, `/api/alerts`) |
| **P5** | Analysis & Reporting Portal planned work; no `portal.html` exists in the current codebase |

---

## Getting Started

### Prerequisites

- JDK 21, matching the Gradle Kotlin toolchain
- Gradle wrapper included in `ktor-sample/`
- A modern browser (Chrome, Firefox, Safari, Edge)

### Run the backend (serves the frontend too)

```bash
cd ktor-sample
bash ./gradlew run
```

Open [http://localhost:8080](http://localhost:8080). The root route redirects to `/static/index.html`.

### Run tests

```bash
cd ktor-sample
bash ./gradlew test
```

### Run linting

Detekt and KtLint are not currently configured as Gradle tasks in this checkout. `bash ./gradlew tasks --all` currently exposes the standard build/test tasks, including:

```bash
cd ktor-sample
bash ./gradlew test
bash ./gradlew build
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ingest` | Accepts a `LivestockPayload`, validates it, evaluates alerts, saves the reading, and records any alert rows |
| `GET` | `/api/readings?site=X&from=Y&to=Z` | Returns readings for one required site, optionally filtered by ISO datetime range |
| `GET` | `/api/alerts?site=X&severity=Y` | Returns the latest 50 alerts, optionally filtered by site and/or severity |
| `GET` | `/api/sites` | Returns the seeded monitoring sites/herds |
| `GET` | `/static/*` | Serves static frontend assets from `src/main/resources/static/` |
| `GET` | `/` | Redirects to `/static/index.html` |
| `GET` | `/dashboard` | Redirects to `/static/index.html` |
| `GET` | `/alerts` | Redirects to `/static/alerts.html` |
| `GET` | `/trends` | Serves the Chart.js trends page through Thymeleaf |

Payload fields used by the app: `siteId`, `timeStamp`, `latitude`, `longitude`, `accelMagG`, and `ambientTemperatureC`.

---

## Alert Severity Levels

| Severity | Meaning |
|----------|---------|
| `normal` | Reading within safe bounds |
| `warning` | Reading approaching a critical threshold — monitor closely |
| `critical` | Reading has exceeded a critical threshold — immediate action required |

Current alert rules:

- Temperature above 30 C creates a warning; above 35 C creates a critical alert.
- Movement below 0.6 g creates a low-activity warning; below 0.3 g creates a critical low-activity alert.
- Movement above 3.0 g creates a flee-event warning; above 4.0 g creates a critical flee-event alert.
- Temperature above 30 C combined with movement below 0.6 g creates a critical `heat_collapse` alert.

---

## Key Features

- **Live dashboard** — Ktor-served dashboard with GPS-style herd map, site filter, active herd count, average temperature, average motion, and recent alerts
- **Bundled livestock dataset** — `livestock_tracking.csv` seeds readings for `herd_cattle_A` and `herd_goat_B`
- **Active alerts** — `/api/alerts` exposes severity-graded alert rows and `alerts.html` provides a dedicated alerts interface
- **Historical trends page** — `/trends` loads Chart.js controls from `/api/sites` and `/api/readings`
- **Server-side validation** — blank site IDs, unknown sites, invalid coordinates, negative accelerometer values, extreme temperatures, malformed JSON, and bad date filters return 4xx responses
- **Accessibility** — dashboard includes skip-link support, semantic sections, ARIA labels, live regions, keyboard-focus styling, and responsive layout work

---

## Development Workflow

- **No direct commits to `main`** — all changes via feature branches and pull requests
- Every PR should be reviewed before merge
- Run `bash ./gradlew test` from `ktor-sample/` before opening a PR
- Update this README when behavior, routes, setup, or project structure changes
- Keep comments useful and human-readable; avoid stale generated-code or authorship notes

---

## Testing Standards

| Type | Scope |
|------|-------|
| Unit tests | Alert engine status and alert-rule behavior |
| Integration tests | Ktor route tests for root redirect, static dashboard assets, ingest, alerts, readings, and sites |
| UX / manual tests | Manual dashboard checks should cover loading, empty, error, and filtered states |
| Accessibility tests | Dashboard should be checked with keyboard navigation and an accessibility scanner before submission |
| Security tests | Malformed JSON, invalid date filters, unknown sites, and out-of-range inputs are covered by route tests |

---

## Definition of Done

1. Code is on a feature branch — never committed directly to `main`
2. Public Kotlin functions/classes have useful comments or KDoc where needed
3. Available Gradle checks pass; currently `bash ./gradlew test` from `ktor-sample/`
4. Tests written and passing for changed backend behavior
5. PR opened, reviewed, and approved by at least one teammate
6. CI passes on the PR once real build/test steps replace the placeholder workflow
7. Merged to `main`

---

## Key Dates

| Milestone | Date |
|-----------|------|
| Submission deadline | 17:00 Friday 8 May |
| Demo | Monday 11 May |
| Retrospective 1 | End of Week 3 |
| Retrospective 2 | End of Week 5 |

---

## Key Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| README or sprint docs drift from the actual `ktor-sample/` app | Verify setup, routes, and filenames against the code before updating docs |
| Missing routes for planned pages such as `/static/portal.html` | Register routes or update navigation before presenting those pages as complete |
| CSV import silently skips bad rows | Check seeded row counts and malformed rows when changing `livestock_tracking.csv` or import logic |
| Tests written only at the end | Keep route and alert-engine tests beside each feature change |
| WCAG failures found late | Run keyboard and scanner checks on dashboard and alerts pages before submission |
| Main branch broken | Use feature branches, PR review, and CI once the workflow has real Gradle commands |

---

## Documentation

Current repository documentation lives in:

- [`README.md`](README.md) — current setup, routes, feature state, and project structure
- [`SPRINT PLAN.md`](SPRINT%20PLAN.md) — sprint planning and role allocation
- [`docs/Personas.md`](docs/Personas.md) — project personas
- [`docs/wireframes/`](docs/wireframes/) — versioned wireframe artifacts
- [`docs/diagrams/`](docs/diagrams/) — diagram location
- [`ktor-sample/README.md`](ktor-sample/README.md) — generated Ktor starter README

Documentation should be updated in the same change as any behavior, route, setup, or structure change.
