# Memory Flow

**Learn anything. Forget nothing.**

An offline-first Android app that turns your own knowledge into scheduled questions delivered as
interactive notifications. Built with Kotlin, Jetpack Compose (Material 3) and a real FSRS-5
spaced-repetition scheduler.

> تطبيق أندرويد لتثبيت أي معرفة في الذاكرة: مجموعات تعلّم، أسئلة في الإشعارات، تلميحان لكل سؤال،
> وجدولة FSRS حقيقية. يعمل بلا إنترنت وبلا حساب.
> **دليل الاستخدام والتركيب بالعربية: [`docs/GUIDE-AR.md`](docs/GUIDE-AR.md)**

---

## Features

| Area | What it does |
| --- | --- |
| Learning groups | Unlimited decks with name, description, category, colour, card list, own daily limits, own notification switch, rename / archive / delete, per-group FSRS progress |
| Animated activation | Circular power dial with spring animation, glow, progress ring, haptics and an explicit ACTIVE/OFF label. The animation is driven by the database state, never by the tap |
| Global learning mode | One master dial that pauses or resumes all notifications without touching any progress |
| Interactive notifications | `NotificationCompat` + `RemoteInput` inline answer, hint action, open action, in-place feedback and the next question in the same notification |
| Hints | Two hints per card, stored offline. First and second hint lower the grade; a third request is recorded as a wrong answer and graded *Again* |
| Weak words | **I don't know** (in the quiz and as a notification action) reveals the answer, fails the card and raises its weak counter. Weak words are listed on Home, come first in every queue, and leave the list gradually as you answer them correctly |
| Question variety | Forward recall, reverse recall and four-option multiple choice, rotated per card; multi-point cards always use forward recall |
| FSRS-5 | Genuine implementation of the FSRS-5 equations and default weights (initial stability/difficulty, forgetting curve, recall/forget stability, difficulty mean reversion, interval from target retention, same-day short-term stability). Unit-tested against the reference behaviour |
| Answer evaluation | Arabic-aware normalisation (diacritics, tatweel, hamza, tāʾ marbūṭa, definite article), alternative answers, typo tolerance, numeric tolerance, point-by-point scoring for multi-point answers |
| Random practice | Optional extra questions that are logged but **never** modify the FSRS schedule |
| Scheduling | WorkManager periodic worker (15 min), quiet hours, allowed window, daily notification cap, daily question cap, minimum gap by pace, Doze/battery friendly, re-armed on boot and after updates |
| Content import | Manual card, bulk paste (`question = answer | hint1 | hint2`), CSV, TSV, XLSX, and TXT lists with **one term per line** or `term = meaning / alternative`, plus labelled blocks (`Plural:`, `العربية:`, `Präteritum:`). Bulk-inserted in one transaction, duplicates skipped, preview before saving |
| Export & backup | CSV, XLSX, full JSON backup and JSON restore (restores into new groups, never overwrites) |
| Statistics | Real SQL aggregates: totals, due, new, learned, accuracy, hint-assisted answers, weak cards, 7-day activity, streak, per-group retention estimate from FSRS stability |
| Languages | Arabic (default, RTL), English, German — all UI strings localised, runtime language switch |
| Accessibility | Material 3 semantics, `Role.Switch` on the dials, content descriptions, reduced-motion support (`ANIMATOR_DURATION_SCALE == 0`), large touch targets |
| Privacy | No account, no analytics, no INTERNET permission. Everything stays in SQLite on the device |

## Screens

1. **Home** — learning-mode dial, active groups, due/new/learned/streak, weekly chart, recent reviews
2. **My groups** — animated activation for each group, activate/deactivate all, per-group retention
3. **Add knowledge** — one card, bulk paste, or file import with preview
4. **Statistics** — overall and per-group numbers
5. **Settings** — learning parameters, notification limits, time windows, language, theme, export/backup, diagnostics, self-check

A deck detail screen adds editing, limits, card management, reset and delete.

## Project layout

```
app/src/main/java/com/memoryflow/app/
  data/          SQLite schema, migrations, repository, settings
  fsrs/          FSRS-5 maths + review scheduler (pure Kotlin)
  review/        answer normalisation and scoring (pure Kotlin)
  io/            CSV/XLSX parsing and writing, import/export (pure Kotlin)
  notify/        selection policy, review engine, notification flow, worker, receivers
  diagnostics/   on-device self-check that runs against a temporary database
  ui/            Compose theme, components, screens, ViewModel
app/src/test/    JVM unit tests (FSRS, evaluator, parsers, XLSX, selection policy)
```

## Build

### Requirements

* JDK 17
* Android SDK with **platform android-34** and **build-tools 34.0.0**
* Gradle 8.7 (or use the included wrapper)

### Command line

```bash
# macOS / Linux
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

```powershell
# Windows PowerShell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Android Studio

Open the project folder, let Gradle sync, then **Run ▶**. Android Studio installs the SDK
components automatically.

### Tests

```bash
./gradlew testDebugUnitTest
```

HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Release build

Create `keystore.properties` in the project root (never commit it):

```properties
storeFile=keystore.jks
storePassword=********
keyAlias=memoryflow
keyPassword=********
```

Then `./gradlew assembleRelease`. Without that file the release build falls back to the debug
signing key so the pipeline still produces an installable APK — replace it before publishing.

## Architecture notes

* **Plain SQLite with explicit migrations** (`Db.kt`) instead of Room. Deliberate: no annotation
  processor in the build, fully deterministic builds, and the schema is readable in one place.
  All access goes through `Repository`, which is the single source of truth for UI *and*
  notification code.
* **One active question at a time.** A row in `sessions` records the card, the hint count and
  whether it was answered. Dismissing or updating the notification cannot reset the hint counter.
* **`ReviewEngine` is shared** by the notification path and the in-app review screen, so a grade
  always produces exactly one database transition and one review log row.
* **Random practice is isolated** with an `is_practice` flag: it is logged, shown in the UI, and
  excluded from FSRS state changes and from scheduled statistics.
* **Self-check screen** runs 20 functional assertions (activation filtering, hint limits, FSRS
  updates, persistence across reopen, CSV/XLSX import, duplicate detection, limits, quiet hours)
  against a throw-away database, and lists the four checks that genuinely require a phone.

## Known limitations

* Inline reply availability depends on the device and Android version. On some manufacturers and
  on locked screens the reply field is not offered; tapping the notification opens the in-app
  review screen instead. Nothing pretends otherwise.
* WorkManager's minimum periodic interval is 15 minutes and Doze can delay a run further. The app
  therefore cannot promise a notification at an exact minute — only within the configured window.
* **Fill-in-the-blank** questions are not generated: the app stores no example sentences, and
  inventing them would be fabrication. Forward, reverse and multiple-choice cover the variety.
* **Automatic B1/B2/Goethe classification** is not implemented: there is no reliable offline
  source, and wrong labels are worse than no labels.
* A TXT line that contains only a term (no `=`) imports as a **draft**: it is stored in the deck
  but stays out of reviews until a meaning is added with the edit button, because a card without
  an answer cannot be graded honestly.
* No cloud sync, no AI evaluation, no text-to-speech. The AI-evaluation hook is intentionally not
  shipped: an API key must never live inside the APK.
* `alarm`-exact scheduling is not used, by design: the app does not need exact alarms and avoids
  the related permission.
* Restoring a JSON backup recreates decks as new groups; it does not merge into existing groups.

## License

MIT — do whatever helps you remember more.
