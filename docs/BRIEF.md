# 今日小票 · Android — Build Brief (read me first)

Full product spec: `docs/SPEC.md` (written for Web+iOS; **this repo is the Android app**, which replaces iOS
and can read payment notifications directly).

## Product in one line
Every evening the app *prints* a playful AI receipt of your day. Bookkeeping is near-zero effort
(Alipay/WeChat payment notifications are captured automatically), weekly reports hand out funny titles,
a subscription detective finds silent auto-renewals, a regret simulator shows "what if", and a
"小店" mode shows how anonymized, aggregated, opt-in data could help a neighbourhood shop.

## Non-negotiable product rules
- **Humans make every choice.** The app records, summarizes, reminds and simulates. It never recommends
  what to buy, never compares prices to push a purchase, never places orders, never gives investment advice.
- **Raw notification text never leaves the phone.** Only structured fields (amount, merchant, time, category).
- Anything that is demo / simulated data is visibly labelled `演示数据`.
- Tone: 俏皮可爱, warm, never preachy or shaming about spending.

## Design language (Apple-flavoured, on Android)
- Warm paper canvas `XpColors.Canvas`, white cards, ink text, **one** coral accent. Use tokens in
  `ui/theme/Tokens.kt` — no raw hex colors in screens.
- Large bold titles (`LargeTitle`), generous spacing (16dp gutters), 16dp card radius, hairline separators
  instead of heavy dividers, no drop-shadow soup.
- Motion: spring physics (`spring(dampingRatio≈0.7–0.8)`), nothing linear except the receipt printer feed.
  Every state change animates (AnimatedContent / animate*AsState). Respect reduced-motion if cheap.
- Haptics on meaningful moments (stamp, confirm, delete) via `LocalHapticFeedback`.
- Every screen needs designed **empty / loading / error** states with friendly copy. Every destructive
  action has undo (snackbar-style toast) rather than a confirm dialog where possible.
- Receipts use monospace `ReceiptText`.
- Chinese UI copy throughout.

## Architecture
- Kotlin + Jetpack Compose + Material3 (restyled), Room, WorkManager, DataStore. minSdk 26, compileSdk 37.
- AGP 9 built-in Kotlin (there is NO `org.jetbrains.kotlin.android` plugin — don't add it).
- `AppContainer` is a tiny service locator; screens get it as a parameter.
- Domain contracts in `domain/` are shared: **don't change existing signatures**; additive changes only.
- Money is `Long` cents. Use `Cents.yuan()` for display.
- Keep files < 400 lines, functions small, pure logic separated from Compose so it can be unit-tested.

## File ownership (parallel agents — stay inside your lane)
| Lane | Owns |
|---|---|
| capture | `data/**`, `capture/**`, `importer/**`, `ui/ledger/**`, `ui/settings/**`, `AppContainer.kt`, manifest `<service>` entries |
| receipt | `ui/theme/**`, `ui/components/**`, `ui/today/**` |
| insights | `insights/**` (incl. `DailyReceiptBuilder` impl — keep signature), `ui/insights/**`, `work/**` |
| merchant | `merchant/**`, `ui/merchant/**`, `ui/game/**` |

- Shared files you may touch minimally: `app/build.gradle.kts` + `gradle/libs.versions.toml` (only if a new
  dependency is truly needed — prefer none), `AndroidManifest.xml` (only your own entries).
- Do NOT edit `ui/AppShell.kt` or another lane's files. Need a sub-page? Navigate inside your own screen
  (local state / nested NavHost / AnimatedContent).
- Read `AppContainer.ledger` (a `LedgerRepository`) for data. Until the capture lane lands Room, it is in-memory
  and empty — build your own preview/demo inputs inside your lane if you need data to look at.

## Build & verify (machine has 16 GB shared by 4 agents — be frugal)
- `./gradlew :app:compileDebugKotlin -q` for quick checks, `./gradlew :app:testDebugUnitTest -q` for tests,
  `./gradlew :app:assembleDebug -q` once at the end. Don't run `clean`. Don't start emulators.
- Write JUnit tests for all pure logic (parsers, detectors, engines, aggregation).
- Commit in your worktree branch with conventional commits (`feat: …`), ending with
  `Co-Authored-By: Claude Sonnet <noreply@anthropic.com>`. Don't push. Don't merge.
