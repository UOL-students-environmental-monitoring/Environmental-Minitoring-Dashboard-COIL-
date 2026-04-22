# Environmental Monitoring Dashboard — COMP2850 COIL Project

This repo currently contains the backend side of our COMP2850 Environmental Monitoring Dashboard. The frontend from the coursework brief is still being built, so the main working part right now is the Ktor service in `ktor-sample/`, plus the project docs in `docs/`.

## Current project state right now

- Backend stack: Kotlin, Ktor, Exposed, H2 for local development
- QA gates now wired in: `test`, `detekt`, and `ktlintCheck`
- Implemented API surface:
  - `POST /api/ingest` to validate and store incoming water-quality readings
  - `GET /api/alerts` to retrieve persisted alerts, with optional `site` and `severity` filters
  - `GET /` and `GET /static/*` for the current server/static placeholders
- Frontend status: still mostly placeholder, so the dashboard, charts, reporting portal, and proper UX/accessibility checks are not done yet

## Repository structure

```text
/
├── README.md
├── CLAUDE.md
├── docs/
│   └── Personas.md
├── .github/
│   └── workflows/ci.yml
└── ktor-sample/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradle/
    ├── src/
    │   ├── main/
    │   │   ├── kotlin/
    │   │   └── resources/
    │   └── test/
    │       └── kotlin/
    └── README.md
```

## Running the backend

```bash
cd ktor-sample
bash ./gradlew run
```

The app runs on `http://localhost:8080`.

## Quality checks

Run all backend checks from the `ktor-sample/` directory:

```bash
bash ./gradlew test
bash ./gradlew detekt
bash ./gradlew ktlintCheck
```

## Testing coverage available now

- Alert engine rule evaluation
- API ingest validation for valid input, malformed timestamps, out-of-range values, and unknown sites
- Alert retrieval for empty state, filtering, and unknown-site handling

These checks are in place now so backend work can still be reviewed properly even before the frontend is finished.

## Documentation notes

- `docs/Personas.md` still needs cleaning up so it matches the agreed personas
- The wiki, project board, branch protection, accessibility log, and UX testing still need doing on GitHub
- We should not document planned frontend screens as if they already exist
