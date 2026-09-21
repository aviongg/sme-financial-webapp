# Verification with Suleman's modules

Run from the repository root in PowerShell:

```powershell
./backend/scripts/verify-suleman-integration.ps1 -MavenRepository "$env:USERPROFILE/.m2/repository"
```

The script creates a new directory below `backend/target`, copies Fatima's current backend (including uncommitted changes), and overlays the locally available `origin/dev/suleman` scoring, records, cash flow, dashboard, search, and profile entity/DTO sources. It does not fetch, merge, edit the current source tree, delete previous runs, or change Git state. `-PrepareOnly` produces the combined source without running Maven. `-SulemanRef` selects another locally available ref; `-MavenCommand` selects an installed Maven executable if needed. `-BackendSourceRoot` selects an exported backend directory instead of the working tree, allowing verification of only the files staged for publication.

The reviewed upstream baseline is `d571629b0378ee5b7f00e35b50992023cf47dac2`. Its Zakat implementation is identical to Fatima's already committed version; it adds Search since the earlier `6a3c576` verification. Search is included in the combined build and tests.

The combined build uses the current Flyway migrations, preserves Fatima's profile lock and language update endpoint, and adds Suleman's three profile creation assignments: `paymentBehavior`, `ntnRegistered`, and `businessRegistered`. It applies the reviewed `DashboardService.java` in this directory only after checking that the upstream dashboard still matches `DashboardService.upstream.sha256`. A changed upstream dashboard requires reviewing and updating this handoff.

The dashboard handoff acquires `AdviceContextService.latest(userId)` before any profile or scoring reads, within its existing writable transaction. It then reads the selected score month through Suleman's service and requests both advice sets for that exact month. The shared context holds the profile, current score, and previous score locks until the dashboard finishes, preserving the score entity's ID while keeping displayed score values, computation time, language, and generated advice consistent.

`CombinedWorkflowIT` boots the complete combined application with a temporary embedded PostgreSQL database and applies Flyway. It creates profiles and monthly records through HTTP, exercises Suleman's actual automatic scoring hook, checks dashboard and advice source consistency, stable reads, current/previous month corrections, Urdu, historical months, month rollover, and Zakat against the same saved records. Search must return the same refreshed advice IDs and text in English, after rescoring, and in Urdu. It also verifies the empty dashboard before a first score. No production database or mock score is used.

The harness also copies and executes Suleman's scoring, cash flow, dashboard, search and monthly-record unit/controller tests. `DashboardServiceTests.java` in this directory retains the upstream cases and updates them for the locked exact-month contract. Bare upstream `*IntegrationTests` are compiled but excluded from execution because they use the application's configured datasource; `CombinedWorkflowIT` supplies the isolated database coverage instead.

Each retained run includes `integration-inputs.json` with the exact Suleman commit and source hashes. Maven results are under that run's `target/failsafe-reports`. The separate regular `postgres-it` suite remains responsible for Fatima's full direct persistence and concurrent generation coverage.

For the eventual merge, retain Fatima's advice/i18n/Zakat and profile language changes, retain Suleman's owned modules and extended profile fields, add the three profile creation assignments, and apply both dashboard service and test handoffs. The scoring module's `save()` is a deferred JPA write: if an eager advice hook is later added inside the same scoring transaction, flush and reload its persisted score before passing it to `generateAndSaveInsights` or `generateAndSaveRecommendations`. The current GET-based lifecycle reads committed persisted scores automatically.
