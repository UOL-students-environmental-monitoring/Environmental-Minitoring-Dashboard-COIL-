# Environmental Monitoring Dashboard — COMP2850 COIL Project

A full-stack environmental monitoring system for **livestock farm management**, built with **Kotlin (Ktor)** on the backend and **HTML/CSS/JavaScript** on the frontend (served as static assets directly by Ktor). The system ingests sensor readings from farm sites, evaluates alert thresholds, and provides a live dashboard and reporting portal for farmers and agricultural field officers.

---

## Project Context

Developed as part of the **COMP2850 COIL (Collaborative Online International Learning)** programme.

The system monitors conditions critical to livestock welfare — GPS location, accelerometer-based movement activity, and ambient temperature — across multiple herds and farm sites. It surfaces real-time readings, historical trends, severity-graded alerts, and a reporting portal with CSV export.

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
| Charts | Chart.js (CDN) |
| Database | H2 in-memory (dev) / PostgreSQL (prod) via Exposed ORM |
| Linting | Detekt + KtLint |
| In-code docs | KDoc on all public functions and classes |
| CI/CD | GitHub Actions |
| Testing | JUnit (backend unit + integration) |
| Accessibility | WCAG 2.1 Level AA target — axe DevTools / WAVE every sprint |

---

## Repository Structure

```text
/
├── README.md
├── SPRINT PLAN.md
├── docs/
│   ├── Personas.md
│   ├── wireframes/                   # Versioned — never delete old versions
│   └── diagrams/                     # ERD and class diagrams — versioned
├── frontend/                         # Stub — frontend lives inside Ktor (see below)
└── backend/
    ├── gradlew / build.gradle.kts / settings.gradle.kts
    ├── gradle/
    └── src/
        ├── main/kotlin/com/environmental/
        │   ├── Application.kt        # Entry point
        │   ├── Routing.kt            # Route registration + static file serving
        │   ├── Serialization.kt      # JSON config
        │   ├── models/               # LivestockReading, Herd, AlertRule, AlertEvent, AlertSeverity
        │   ├── routes/               # LivestockRoutes.kt, AlertsRoutes.kt
        │   ├── services/             # AlertEngine.kt, ValidationService.kt
        │   └── database/             # DatabaseConfig.kt, schemas
        └── main/resources/
            ├── application.yaml
            ├── logback.xml
            └── static/              # ← FRONTEND LIVES HERE (served by Ktor)
                ├── index.html       # Live dashboard (P1)
                ├── trends.html      # Historical trends + charts (P2)
                ├── alerts.html      # Active alerts panel (P4)
                ├── portal.html      # Reporting portal + CSV export (P5)
                ├── css/style.css
                └── js/
                    ├── api.js       # All fetch calls to the backend API
                    ├── charts.js    # Chart.js rendering helpers (P2)
                    └── main.js      # Shared utilities
```

---

## Team Roles

| Person | Area |
|--------|------|
| **P1** | Dashboard UI & Layout (`index.html`) |
| **P2** | Charts & Historical Trends (`trends.html`, `js/charts.js`) |
| **P3** | Data Management & Validation (backend models, DB schemas, validation) |
| **P4** | Alert System (`alerts.html`, `AlertEngine.kt`, `AlertsRoutes.kt`) |
| **P5** | Analysis & Reporting Portal (`portal.html`) |

---

## Getting Started

### Prerequisites

- JDK 17+
- Gradle (wrapper included — no global install needed)
- A modern browser (Chrome, Firefox, Safari, Edge)

### Run the backend (serves the frontend too)

```bash
cd backend
./gradlew run
```

Open [http://localhost:8080](http://localhost:8080) — all pages served directly by Ktor.

### Run tests

```bash
cd backend
./gradlew test
```

### Run linting

```bash
cd backend
./gradlew detekt
./gradlew ktlintCheck
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/readings?site=X&from=Y&to=Z` | Livestock readings (location, activity, temperature) for a herd within a date range |
| `GET` | `/alerts?site=X&severity=Y&type=Z` | Alerts filtered by herd, severity, and/or alert type (low_activity, geofence, flee) |
| `GET` | `/sites` | All registered herds and farm sites |
| `GET` | `/*` | Static frontend assets served by Ktor |

Full API documentation: GitHub Wiki → `Design/API-Reference`

---

## Alert Severity Levels

| Severity | Meaning |
|----------|---------|
| `NORMAL` | Reading within safe bounds — no alerts triggered |
| `WARNING` | A condition is approaching a critical threshold — monitor closely |
| `CRITICAL` | A critical condition has been detected — immediate action required |

### Alert Types

| Alert | Field | Meaning |
|-------|-------|---------|
| Low Activity | `alert_low_activity` | Animal movement (accel_mag_g) below expected threshold — possible injury or illness |
| Geofence Breach | `alert_geofence` | Animal GPS position (`latitude`, `longitude`) outside permitted boundary |
| Flee Event | `alert_flee` | Sudden high-acceleration movement indicating panic or predator threat |

Threshold rules per alert type: Wiki → `Requirements/Alert-Rules`

---

## Key Features

- **Live dashboard** — current livestock readings (location, activity, temperature) with colour-coded severity, designed for at-a-glance use on mobile (Tom persona)
- **Historical trends** — interactive time-series charts with herd and metric filtering (accel_mag_g, ambient_temperature_c)
- **Active alerts panel** — plain-language explanations for low activity, geofence breach, and flee events (no technical jargon)
- **Reporting portal** — date range and herd filters, cross-herd summaries (min/max/avg), CSV export (Priya persona)
- **Server-side validation** — null values, out-of-range inputs, and unknown herd IDs all rejected with descriptive errors
- **Accessibility** — WCAG 2.1 Level AA target; skip link, ARIA roles, keyboard navigation, contrast-checked colours; tested every sprint with axe/WAVE

---

## Development Workflow

- **No direct commits to `main`** — all changes via feature branches and pull requests
- Every PR requires at least one peer review and must pass CI before merge
- Run `./gradlew detekt` and `./gradlew ktlintCheck` before opening a PR
- KDoc comments required on all public functions and classes
- AI usage must be noted inline: `// Used [model] to assist with X — lines Y–Z`

---

## Testing Standards

| Type | Scope |
|------|-------|
| Unit tests | Every function with logic; all boundary conditions |
| Integration tests | Every API endpoint tested end-to-end |
| UX / manual tests | Documented in Wiki with date, tester, tasks, findings |
| Accessibility tests | axe/WAVE run every sprint; zero Level A WCAG errors required |
| Security tests | Malformed, oversized, and out-of-range inputs tested explicitly |

---

## Definition of Done

1. Code is on a feature branch — never committed directly to `main`
2. KDoc comments on all public functions
3. Detekt + KtLint pass — zero significant issues
4. Tests written and passing
5. PR opened, reviewed, and approved by at least one teammate
6. CI passes on the PR
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
| P3 API not ready — blocks P1, P2, P5 | P3 provides agreed mock JSON responses by end of Week 1 |
| P5 duplicating P3's data logic | P5 must reuse P3's API only — enforced in PR review |
| Tests written only at the end | Tests listed as tasks per week — non-optional |
| WCAG failures found late | P1 runs axe/WAVE every sprint and logs results |
| Documentation left to the last week | Wiki pages assigned by name and sprint — stubs created in Week 1 |
| Main branch broken | Branch protection + required CI pass before any merge to `main` |

---

## Documentation

All design and requirements documentation lives in the **GitHub Wiki**:

- `Requirements/COIL-Summary` — agreed scope and livestock monitoring focus
- `Requirements/Data-Model` — livestock tracking data fields and schema (`timestamp`, `site_id`, `latitude`, `longitude`, `accel_mag_g`, `ambient_temperature_c`, `status`, alert flags)
- `Requirements/Personas` — Tom (farmer) and Priya (field officer) personas
- `Requirements/User-Stories` — full backlog with MoSCoW priority and story points
- `Requirements/Alert-Rules` — threshold rules for low activity, geofence breach, and flee alerts
- `Design/API-Reference` — all endpoints with example requests and error codes
- `Design/Architecture` — class diagram
- `Design/Database` — ERD / schema diagram
- `Design/Wireframes` — all wireframe versions (never deleted — versioned as v1, v2, v3)
- `Testing/Accessibility-Log` — axe/WAVE results per sprint
- `Testing/UX-Tests` — user test records
