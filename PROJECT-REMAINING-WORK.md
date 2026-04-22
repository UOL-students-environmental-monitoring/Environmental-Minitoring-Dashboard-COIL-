# Project Remaining Work

This is the easiest way to see what is still left if we want the project to feel complete and get the best marks we can. It is split by person so everyone can just look at their section and crack on with it.

## Priority order

1. Finish the backend/frontend features that the coursework brief actually promises.
2. Keep the backend stable with tests, linting, and CI on every change.
3. Bring the documentation back in line with the real system.
4. Add the evidence the rubric expects: accessibility testing, UX testing, meeting notes, and versioned design updates.

## Person A — Backend

### Must finish for a complete backend

- Add the missing API endpoints promised in the brief and README:
  - `GET /readings?site=X&from=Y&to=Z`
  - `GET /sites`
- Make validation complete instead of partial:
  - duplicate reading detection
  - descriptive JSON error bodies for all invalid input paths
  - consistent handling for malformed timestamps, missing fields, unknown sites, oversized site IDs, and extreme values
- Review the database setup and decide whether PostgreSQL support is real or only planned:
  - if real, wire it correctly
  - if not real yet, document H2-only status honestly
- Keep business logic out of route handlers as the project guidance requires.

### Needed for higher marks

- Add KDoc to all public classes and functions.
- Make alert logic fully documented and traceable to the chosen environmental rules.
- Ensure route behaviour matches the documented API contract exactly.

## Person B — Frontend

### Must build before the project feels complete

- Replace the placeholder UI with the actual dashboard pages required by the brief:
  - current readings dashboard
  - alerts panel
  - historical trends view
  - reporting portal
- Connect the frontend to the real backend endpoints instead of using placeholder content.
- Implement the expected states on each page:
  - loading
  - empty
  - error

### Must build for marks and demo quality

- Make the UI responsive at the expected breakpoints:
  - `375px`
  - `768px`
  - `1280px`
- Add accessibility support:
  - keyboard navigation
  - visible focus states
  - ARIA labels where needed
  - readable chart and alert content
- Implement chart and filtering behaviour that works with real backend data.
- Build the reporting flow:
  - site filter
  - date range filter
  - CSV export

## Person C — Testing, Integration, CI/CD, Documentation

### Already in place now

- Backend automated tests for alert logic and core ingest/alerts route behaviour
- Detekt and KtLint integrated into the Gradle project
- CI workflow updated to run against `ktor-sample`

### Still left for Person C

- Add tests for the next backend work Person A ships:
  - `GET /readings`
  - `GET /sites`
  - duplicate detection
  - expanded validation rules
- Add persistence-oriented tests once duplicate handling and reading retrieval are implemented.
- Review every PR against the checklist:
  - feature branch only
  - tests included
  - `detekt` passes
  - `ktlintCheck` passes
  - KDoc present
  - no raw exception responses
- Audit docs every time endpoints or behaviour change.
- Update the wiki with:
  - test setup
  - accessibility log
  - UX tests
  - API reference
  - architecture notes
  - security notes
- Run accessibility checks once the real frontend exists.
- Run at least one external UX test and record findings.

## Shared Team Work

### Needed to reach the “complete project” standard

- Align the personas, README, wiki, and implemented system so they describe the same product.
- Keep all work off `main`; use branches and PR review properly.
- Version design changes instead of overwriting old evidence.
- Keep meeting notes and decisions current.

### Needed to maximise marks

- Demonstrate evolution:
  - changing wireframes
  - changing documentation
  - testing logs
  - review history
- Make sure the final demo uses real system flows, not placeholder pages.
- Ensure the codebase and documentation tell a consistent story from requirements to implementation to testing.

## Blocked Until Frontend Exists

- Frontend integration tests
- Accessibility testing with real pages
- UX task-based testing
- CSV export verification
- End-to-end dashboard and portal flows

## Definition Of Done For Final Submission

- Backend endpoints required by the brief are implemented and tested.
- Frontend pages required by the brief are implemented and connected.
- `bash ./gradlew test`
- `bash ./gradlew detekt`
- `bash ./gradlew ktlintCheck`
- CI passes on GitHub.
- Documentation matches the real shipped behaviour.
- Wiki includes testing, accessibility, UX, API, architecture, and security evidence.

## Short version

- Person A needs to finish the missing backend endpoints and validation properly.
- Person B needs to build the actual frontend pages and hook them up to the backend.
- Person C needs to keep writing tests, keep CI green, and make sure the docs/wiki match what is really built.
- As a team, we need to stop the docs, code, and coursework brief from saying different things.
