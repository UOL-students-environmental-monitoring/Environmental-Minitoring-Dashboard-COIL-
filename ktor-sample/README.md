# Environmental Monitoring Dashboard Backend

This is the current Ktor backend for the COMP2850 Environmental Monitoring Dashboard.

## What is implemented

- JSON ingest endpoint for water-quality readings
- Threshold-based alert evaluation
- Alert persistence using Exposed
- H2-backed local runtime for development and tests
- Automated backend QA checks with JUnit, Detekt, and KtLint

## Run locally

```bash
bash ./gradlew run
```

The server starts on `http://localhost:8080`.

## Run quality gates

```bash
bash ./gradlew test
bash ./gradlew detekt
bash ./gradlew ktlintCheck
```

## Endpoints currently available

- `POST /api/ingest`
- `GET /api/alerts`
- `GET /`
- `GET /static/*`
- `GET /html-thymeleaf`

## Notes

- The backend source lives in `src/main/kotlin/`
- Tests live in `src/test/kotlin/`
- The bigger frontend and reporting portal from the coursework brief are not built in this module yet
