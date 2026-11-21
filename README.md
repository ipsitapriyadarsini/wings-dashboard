# Wings Dashboard — Phase 1 backend

Java 21 · Spring Boot 3.5 · Spring Data JPA · H2 (dev) / PostgreSQL (prod) · springdoc OpenAPI

Implements the PRD "Wings Dashboard - Phase 1": Cafe Score, SM Score + leaderboard, home page, the
Attendance / Checklist / Rating / Documents drill-downs, Top Insights, the SM-app score views and
"Summarize this page". Tickets and Sales & Billing are Phase 2 and are not built.

## Run

```bash
mvn spring-boot:run          # profile "demo": in-memory H2 + ~10 weeks of generated sample data
mvn test
```

Swagger UI: http://localhost:8080/swagger-ui.html

On Java 25+ add `-Dspring-boot.run.jvmArguments="-Dnet.bytebuddy.experimental=true"` (Hibernate's ByteBuddy lags new JDKs).

Production: `SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://… DB_USER=… DB_PASSWORD=…`
(`ddl-auto: validate` — add Flyway/Liquibase migrations first).

## Identity (placeholder)

Callers identify themselves with an `X-User-Id` header. Swap `AccessGuard` for the real SSO/JWT principal when
integrating; nothing else reads the header. Demo IDs are printed at startup
(1 = Ops Head, 2 = City Ops Head BLR+PUN, 4 = Area Manager, 7 = Site Manager).

| Role | Access |
|---|---|
| `SITE_MANAGER` | `/api/v1/sm/**` only (own score; others only as leaderboard grades) |
| `AREA_MANAGER` | dashboard for cafes mapped to them (no AM breakdown table) |
| `CITY_OPS_HEAD` | dashboard for all cafes in their cities; can approve documents |
| `OPS_HEAD` | all cafes + `/api/v1/admin/**` |

## API

All dashboard calls take the global filters as query params:
`range` (+`from`,`to` for `CUSTOM`) · `scoreRange` (+`scoreFrom`,`scoreTo`) · `cityIds` · `companyIds` · `cafeIds` · `vendorIds`
(vendor filter applies to checklists and documents only). Defaults: `range=LAST_7_DAYS`, `scoreRange=LAST_WEEK`.

| Endpoint | PRD |
|---|---|
| `GET /api/v1/dashboard/filters/options` | filters |
| `GET /api/v1/dashboard/home` | 6.1–6.6 Cafe Score + trend + best/worst 3, attendance & checklist, rating, insights |
| `GET /api/v1/dashboard/scorecards/cafes?page&size&sort` | 7.1.1 |
| `GET /api/v1/dashboard/scorecards/sms?page&size&sort` | 7.1.2 |
| `GET /api/v1/dashboard/leaderboard` | 7.1.3 |
| `GET /api/v1/dashboard/attendance?sortBy=attendance\|punctuality&dir` | 7.2 |
| `GET /api/v1/dashboard/checklists?sortBy=fillRate\|quality&dir` | 7.3 |
| `GET /api/v1/dashboard/ratings` | 7.4 |
| `GET /api/v1/dashboard/documents`, `POST …/documents/{id}/approve`, `PUT …/documents/{id}/upload` | 7.5 |
| `POST /api/v1/dashboard/summary?page=HOME\|ATTENDANCE\|…` | 6.7 |
| `GET /api/v1/sm/home`, `/sm/score`, `/sm/score/checklist`, `/sm/leaderboard` | 8.1–8.4 |
| `GET/PUT /api/v1/admin/config`, `PUT /admin/cafes/{id}/score-exclusion`, `POST /admin/scores/recalculate?weekStart=` | configurable weights, benchmarks, excluded locations |

## How scoring works

* Weeks are Monday–Sunday. A **weekly job** (`ScoreJob`, Mon 02:00 IST) publishes `cafe_weekly_score` and
  `sm_weekly_score` for the week that just ended. There is no day-level scoring and the in-progress week is never scored,
  so "This Week" shows the latest published week.
* Snapshots store **raw numerators/denominators**. Any combination of cafes/weeks is re-scored from summed data
  (never an average of percentages) — e.g. 100/100 + 1/2 → 99.02 %.
* **Cafe Score** = Rating 50 + Fill 25 + Quality 25. Checklist-only → Fill 50 + Quality 50. Rating-only or nothing → *No Score*
  (never zero; excluded from rankings).
* **SM Score** = Attendance, Punctuality, Fill, Quality (25 each). Missing parameters are N/A and their weight is redistributed
  (no checklist → 50/50). Punctuality = 4 equal checks (check-in/out on time, check-in/out in location) sharing its weight.
  No roster / on leave → *No Score* with the PRD messages. Cafe-level checklist numbers are applied to every SM rostered there.
* Weights, insight benchmarks (50 / 50 / 85) and the monthly-trend threshold live in `score_config` (`ScoreConfigService.DEFAULTS`).
  Cafes can be excluded from scoring via the admin endpoint.
* Ranking rules (best/worst 3, ties, <6 cafes, single cafe), Top/Bottom-10 breakdowns, leaderboard rank movement
  (`↑/↓/—/New/blank`) and 5-per-page scorecards with a sort toggle at 10+ items follow the PRD tables.

## Data this service expects

Entities are in `domain/`. Until Wings/Nexus feeds exist, the demo profile generates them; in production, populate:
`cafe`, `app_user` (+`user_cafe`, `user_city`), `roster` (with the four punctuality flags and `punched`), `checklist` +
`checklist_response` (one row per mapped question, `UNFILLED` included), `rating`, `cafe_daily_orders`, `compliance_document`.

## Assumptions to confirm with product

1. **Grade gap** — the PRD bands skip 85–<90. B is extended to 75–<90 (`Grades`). Change if A should start at 85.
2. **Approved leave** is excluded from attendance, no-show and punctuality denominators (so attendance % + no-show % = 100 %).
3. **Punctuality has two definitions**: drill-down/insights use *valid attendance %* (all four checks pass ÷ punched);
   the SM score uses the average of the four checks.
4. **Month presets** assign a week to the month in which it *starts*. `LAST_3_MONTHS` = the 3 previous full months. Custom ranges
   snap outward to whole weeks; the trend goes monthly beyond 8 weeks (configurable).
5. **Insights** — "top 1–2 insights for each gap" is implemented as at most 2 cafés per gap type, then cafés are sorted by gap count and name.
6. **Default date filter** is Last 7 days (the PRD lists "Today vs Yesterday" as open).
7. **SM score on the SM app** is the latest *weekly* score, not cumulative (open question in the PRD).
8. **Leaderboard** covers all SMs (no filters, per PRD) and shows Top 10 plus the viewing SM's own rank in the SM app.
9. **Summarize this page** calls `wings.chatbot.url` with a placeholder contract; with no URL set it returns the page context only.
10. Not built: ticket summary (7.6), sales & billing (7.7), "compare range / delta" (Phase 2), logical city grouping (open), SM-app
    checklist counts of *checklists* (only question counts are stored), file storage for document uploads (only the file URL is saved).

## Performance notes

Aggregates run as `GROUP BY` queries (checklists, ratings) or scalar projections (rosters) — no entity graphs. Weekly snapshots keep
the home page cheap. If roster volume grows large, push the roster aggregation into SQL as well and add the indexes already declared on
the entities. The `ScoreJob` is single-instance; use ShedLock if you run several replicas.
