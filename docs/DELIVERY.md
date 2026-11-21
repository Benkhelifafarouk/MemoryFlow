# Delivery report — Memory Flow 1.2

Date: 21 November 2026 · Build environment: Windows, JDK 17.0.9 (Zulu), Android SDK 34, Gradle 8.7

> **1.2 — weak words, session caps, question variety.** Added on top of 1.1 without redesigning
> anything: a "I don't know" action, a weak-word list with priority, daily new-card caps for
> sessions, three question kinds, and TXT labels for plural/verb forms. Section 10 has the details.

---

## 1. What you received

| File | What it is |
| --- | --- |
| `MemoryFlow-1.2-debug.apk` | 10.23 MB installable Android app, signed with the Android debug key |
| `memory-flow-android.zip` | Complete source project (Gradle wrapper, GitHub Actions workflow, docs) |
| `docs/GUIDE-AR.md` | Beginner Arabic guide: install, first setup, GitHub upload, FAQ |
| `docs/DELIVERY.md` | This report |

APK identity (read with `aapt2 dump badging`):

```
package: com.memoryflow.app
versionCode: 3     versionName: 1.2
minSdkVersion: 26  targetSdkVersion: 34
APK SHA-256: B0A81F290E9F25429451C28C12B6EA9FB758152B499C56FEE3B8757E75D3942E
Signer: CN=Android Debug (SHA-256 2f6f8a0b5819b9a771aa29bd64782991aa410c179be99fcccc1199d3209fd08d)
```

Debug signing means: installable right now, and future builds signed the same way upgrade over it
without losing data. It is **not** suitable for Google Play (that needs your own release key — the
`keystore.properties` path is documented in the README).

---

## 2. What was actually executed

Nothing in this table is an estimate.

| Check | Command / tool | Result |
| --- | --- | --- |
| Debug APK build | `gradle assembleDebug` | **BUILD SUCCESSFUL** |
| JVM unit tests | `gradle testDebugUnitTest` | **87 tests, 0 failures, 0 errors, 0 skipped** |
| Android Lint | `gradle lintDebug` | **0 errors, 44 warnings** (deprecations, unused resources) |
| APK metadata | `aapt2 dump badging` | package/version/min-max SDK as above |
| APK signature | `apksigner verify --print-certs` | valid, Android Debug certificate |
| APK checksum | `Get-FileHash -Algorithm SHA256` | `B0A81F29…D3942E` |

### Test suites

| Suite | Tests | Covers |
| --- | --- | --- |
| `FsrsTest` | 19 | FSRS-5 equations, default weights, official numeric vectors, scheduling transitions |
| `AnswerEvaluatorTest` | 12 | Arabic normalisation, alternatives, typos, numbers, multi-point partial credit, grade mapping |
| `CardTextParserTest` | 25 | bulk paste, one term per line, labelled blocks (Plural/Arabic/verb forms), slash alternatives, blank lines, comments, binary junk, German/Arabic/French text, CSV quoting, 5,000 lines |
| `XlsxTest` | 4 | XLSX write→read, 30 columns, escaping, empty sheet |
| `SelectionPolicyTest` | 8 | deck eligibility, daily caps, quiet hours across midnight, min gap, all gates |
| `ImportPlannerTest` | 8 | duplicate detection, case/space normalisation, in-file duplicates, 5,000 terms |
| `SessionBuilderTest` | 9 | daily new-card caps (deck + global), weak-first ordering, weak-only sessions, drafts excluded |
| `LocaleDigitsTest` | 2 | Western digits in the Arabic locale |

FSRS-5 was verified against the official hand-computed examples, not just self-consistency:

| Case | Expected (official) | Implementation |
| --- | --- | --- |
| First review, Good | S=3.173, D=5.28243442, I=3 | matches |
| S=5, D=5, R=0.9, Good | S′=17.46568510, D′=4.99183271, I=17 | matches |
| S=20, D=8, R=0.8, Hard | S′=30.30998530, D′=8.30107319, I=30 | matches |
| S=5, D=5, R=0.9, Again | S′=1.42655877, D′=6.60703511, I=1 | matches |
| Same-day S=2, Easy | S′=4.71950979 | matches |

---

## 3. Acceptance criteria — where each one stands

Legend: **A** = verified automatically in this build · **I** = implemented, exercised only on a
phone later · **D** = requires a physical device, therefore **not verified here**.

| # | Criterion | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Create A1 group | A | `SelfCheck`: deck count asserted |
| 2 | Create A2 group | A | same run |
| 3 | Create B1 group | A | same run |
| 4 | Add vocabulary to each | A | 4 cards inserted, counts asserted |
| 5 | Activate A2 only | A | active deck ids == `[A2]` |
| 6 | Notifications contain only A2 | A (queue) / D (delivery) | queue candidates all from A2 |
| 7 | Activate A2 + B1 | A | both decks eligible |
| 8 | Both groups included | A | candidates found in both decks |
| 9 | Deactivate A2 | A | zero A2 candidates in the queue |
| 10 | A2 no longer in queue | A | asserted after deactivation |
| 11 | Receive a real notification | **D** | needs a phone |
| 12 | Submit answer via RemoteInput | **D** | needs a phone |
| 13 | First hint | A (logic) / D (notification UI) | hint counter + grade penalty asserted |
| 14 | Second hint | A (logic) / D (notification UI) | same |
| 15 | Third hint counts as incorrect | A | card graded Again, moved to relearning |
| 16 | FSRS schedule updates | A | stability, due and reps asserted; 19 unit tests |
| 17 | Card survives closing the app | A (database reopen) / D (process kill) | scratch DB closed and reopened |
| 18 | Scheduled and random notifications independent | A (policy) / D (delivery) | gate tests; random path leaves FSRS untouched |
| 19 | CSV import | A | import + duplicate detection asserted |
| 20 | XLSX import | A | writer→reader round trip with Arabic text |
| 21 | Export data | I | CSV, XLSX and JSON backup implemented; writer covered by tests |
| 22 | Install on a real phone | **D** | no phone or emulator available here |

**Bottom line:** 1–10 and 13–20 are verified by real assertions. 11, 12, 22 and the device half of
6, 13, 14, 17, 18 are **not** verified, because this machine has no Android device or emulator.

---

## 4. What is inside the app

* **Kotlin + Jetpack Compose (Material 3)**, single-activity, five tabs: Home, My groups, Add
  knowledge, Statistics, Settings.
* **SQLite** with explicit schema and versioned migrations (`Db.kt`), all access through one
  `Repository`. Room was deliberately not used: no annotation processor, deterministic builds,
  and the whole schema readable in one file.
* **FSRS-5** in `fsrs/Fsrs.kt` — 19 default weights, forgetting curve with `FACTOR = 19/81`,
  same-day branch, forget-stability cap `S / e^(w17·w18)`.
* **Animated activation**: hand-drawn circular power dial (Canvas), spring animation, glow,
  progress ring, haptics, `Role.Switch` semantics, reduced-motion support. The dial reflects the
  database row, never a local UI flag — a failed write shows OFF.
* **Notifications**: `NotificationCompat` + `RemoteInput` inline reply, hint action, open action,
  feedback and the next question in the same notification, every hint state persisted in a
  `sessions` row so dismissing cannot reset the counter.
* **Scheduling**: WorkManager periodic worker, quiet hours, allowed window, daily notification and
  question caps, minimum gap by pace, re-armed on boot / app update / time change.
* **Import/export**: CSV, TSV, TXT, XLSX (own ZIP+XML reader/writer, no Apache POI), duplicate
  detection, preview before saving; export to CSV/XLSX and a full JSON backup + restore.
* **i18n**: Arabic (default, RTL), English, German — every user-facing string is a resource.
* **Diagnostics**: on-device self-check running 20 functional assertions against a throw-away
  database, plus four rows that explicitly say they need a phone.

---

## 5. Known limitations (no sugar-coating)

1. **Not run on a device.** No phone or emulator was available, so no notification was ever posted
   by this build and no APK was installed. The first real test is yours.
2. **Timing is approximate.** WorkManager's minimum period is 15 minutes and Doze can delay it.
   A question arrives *within* the window, not at an exact minute.
3. **Inline reply is not universal.** Some manufacturers and lock-screen states do not offer the
   text field. Tapping the notification opens the in-app review instead. The app never pretends
   an answer was submitted when it was not.
4. **One question at a time.** By design: a second notification would make hint counting and
   grading ambiguous.
5. **No AI evaluation, no cloud sync, no account.** The app has no INTERNET permission at all.
6. **Restore creates new groups** instead of merging into existing ones — a safe default, not a
   merge tool.
7. **Release signing is not set up for you.** Without `keystore.properties` the release build
   falls back to the debug key, which is fine for sideloading and wrong for Google Play.

---

## 6. GitHub: not uploaded

No GitHub credentials or authorization were available in this environment, so **nothing was
uploaded** and no repository exists yet. The project is prepared for it:

* `.github/workflows/android-build.yml` — on every push: JDK 17, unit tests, `assembleDebug`,
  uploads `memory-flow-debug-apk` as an artifact.
* `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` — wrapper included, so Actions
  needs no local Gradle install.
* `.gitignore` excludes build output, `local.properties`, keystores.

Upload steps by hand are in `docs/GUIDE-AR.md`, sections 4–7.

---

## 7. First 10 minutes on the phone

1. Install `MemoryFlow-1.2-debug.apk` (allow "install unknown apps" for your browser/file manager).
2. Open it, allow notifications.
3. **مجموعاتي → مجموعة جديدة** → name it `German A2` → **إنشاء**.
4. **إضافة معرفة** → pick the group → add two cards with hints → **حفظ البطاقة**.
5. Back in **مجموعاتي**, tap the circular dial next to the group until it reads **مفعّلة**.
6. **الرئيسية** → tap the big dial (**وضع التعلّم**) so learning is on.
7. **الإعدادات → التشخيص → إشعار تجريبي** to get a question immediately.
8. **الإعدادات → التشخيص → الفحص الذاتي → تشغيل الفحص** to run the 20 built-in checks on your
   own phone and see PASS/FAIL.
9. Exempt the app from battery optimisation (the Arabic guide lists paths per manufacturer).

If step 7 produces nothing, the cause is almost always the notification permission or battery
optimisation — both are covered in the guide.

---

## 9. Update 1.1 — bulk TXT vocabulary import

### What changed (minimal, additive)

| File | Change |
| --- | --- |
| `io/CardTextParser.kt` | A line without a separator is a term instead of being discarded; trimming, blank/comment/binary skipping |
| `io/ImportPlanner.kt` *(new)* | Normalises terms (NFKC, whitespace collapse, lower-case) and splits new vs duplicate, including in-file duplicates |
| `io/ImportExportManager.kt` | BOM-aware decoding (UTF-8 / UTF-16, Windows-1252 fallback), preview counts, transactional batch insert, drafts |
| `data/Repository.kt` | `cardFronts(deckId)` + `insertCardsBatch(cards)` in one transaction |
| `ui/AppViewModel.kt` | Import preview with found/duplicates/new, recount on group change, `updateCard` |
| `ui/screens/AddScreen.kt` | Explicit **Import from TXT** button, counts preview, confirm disabled when nothing is new |
| `ui/screens/DeckDetailScreen.kt` | Per-card edit button (to complete imported terms) and a **Draft** badge |
| `util/LocaleHelper.kt` | Locale pinned to `-u-nu-latn`, so numbers render 0-9 even in Arabic |
| `diagnostics/SelfCheck.kt` | 17th check: 1,005 TXT lines → 1,000 inserted, 5 duplicates skipped |
| 3 × `strings.xml` | New labels in Arabic, English, German |

### Behaviour to know

A line containing `=` (or a tab / `|`) imports a complete card. A line with only a term has no
answer, so it is stored as a **draft**: it appears in the group but stays out of reviews until the
answer is added with the edit button. A card without an answer cannot be graded, so this is the
only honest default.

### Verified

- 72 JVM tests, 0 failures (parser 19, planner 8, digits 2, plus the existing 43).
- Parser and planner are covered by 5,000-line tests with a 3 s ceiling (they run far below it).
- Insertion is one SQLite transaction, so 1,000 rows do not mean 1,000 commits.
- The on-device self-check now exercises 1,005 lines → 1,000 inserts.

### Not verified

No phone or emulator here: the system file picker, the preview screen and the 1,000-row insert were
not executed on a device. Run **الفحص الذاتي** after installing to confirm on your own phone.

---

## 10. Update 1.2 — weak words, session caps, question variety

### What changed

| File | Change |
| --- | --- |
| `data/Models.kt` · `data/Db.kt` | `cards.weak_count`, schema **v1 → v2** migration (`ALTER TABLE … DEFAULT 0`, existing rows preserved) |
| `data/Repository.kt` | weak counter updated on every grade (fail +1, success −1), weak-first ordering for due cards, `weakCards()`, weak counters in statistics |
| `io/CardTextParser.kt` | labelled blocks (`Plural:`, `العربية:`, `Präteritum:`, `Partizip II:` …) and `answer / alternative` splitting |
| `review/SessionBuilder.kt` *(new)* | builds a session: due first, weak first, new cards capped per deck and globally |
| `ui/AppViewModel.kt` | `reviewQueue()` returns the capped session plus the deck pool for distractors |
| `ui/screens/ReviewScreen.kt` | forward / reverse / multiple-choice questions, **لا أعرف** button, weak-only mode, session summary |
| `ui/screens/HomeScreen.kt` | weak-words card with count and **ابدأ الاختبار** |
| `ui/screens/DeckDetailScreen.kt` | pills: draft, weak, new, review, mastered |
| `notify/*` | **لا أعرف** action in the notification (reply · hint · don't know · open) |
| `diagnostics/SelfCheck.kt` | 3 new checks (labelled blocks, weak flow, session caps) → 20 total |
| 3 × `strings.xml` | new labels; the import tab is now called **استيراد** |

### Behaviour

* A failure (wrong answer, third hint, or **لا أعرف**) increments `weak_count`. A correct answer
decrements it. At 0 the word leaves the weak list — so it fades out after as many correct
answers as it had failures.
* Weak words sort first in due queues and in sessions. A weak-only session ignores due dates on
purpose (drilling is extra practice; it does not break FSRS — same-day reviews use the short-term
branch).
* Sessions now respect the daily new-card caps: importing 1,000 terms gives ~10–20 new words per
day, and the rest wait in the new queue. The caps are the existing settings (`بطاقات جديدة يوميًا`),
no new settings screen was added.

### Deliberately not implemented

* **Fill-in-the-blank** — the app stores no example sentences; generating one would invent data.
* **Automatic B1/B2/Goethe/Alltag classification** — no reliable offline source; wrong labels are
worse than none, as requested.

### Verified

* 87 JVM tests, 0 failures (parser 25, session builder 9, plus the existing 53).
* Lint: 0 errors, 44 warnings.
* The v1 → v2 migration and every new SQL query were executed against a real SQLite database
  before building (ALTER on a populated v1 table, weak ordering, weak counts).

### Not verified

No phone or emulator: the new buttons, the four-option quiz and the notification action were not
run on a device. The 20-check self-test on the phone covers the data side.
