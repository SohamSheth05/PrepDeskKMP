# PrepDesk

An offline Kotlin Multiplatform interview preparation app with 26 selectable interview tracks. Runnable Android and iOS hosts share the preparation UI and Room database. Optional Supabase services are implemented and require deployment/configuration before public use.

## Implemented

- **Today:** a countdown to your interview date, due reviews, the day's new questions for your plan phase, whether a mock is due, your readiness and three weakest topics, and recent weak mock answers.
- **Play:** a player card with XP, level and title; three daily challenges that change each day (with a +50 XP bonus for completing all three); **Answer Rush**, a 60-second multiple-choice game per track with combo multipliers up to ×4, a 3-second penalty for misses, personal bests, and one tap to add missed questions to the revision deck; 16 badges; and level-up and badge celebrations with confetti. XP is derived only from counters that never decrease (study actions, mock checklist points, focus sessions, game points, and challenge sweeps), so a missed review never removes XP. Game progress is stored with other progress and included in backups and sync. Celebrations wait while a mock answer timer is running.
- **Interview countdown:** set an interview date and an optional company or role in Plan. Preparation moves through Build (new material, finished a week before the interview), Sharpen (last 7 days: fewer new questions, mocks every 2 days), Final recap (no new material), and Interview day. New questions per day are capped at 15. Without a date, the manual day budget applies.
- **Readiness:** each plan question scores review stage (60%) and latest mock checklist coverage (40%); unpractised questions count as zero. Plan lists every topic weakest first, and **Drill** starts a study quest on a topic's five least-mastered questions. Readiness is self-assessed coverage, not a hiring prediction.
- **Practical:** hands-on exercises (write code, debug, predict output, hands-on tasks) with starter code, test cases, progressive hints, a reference solution, and a self-check rubric that schedules revision. Exercises with a test harness run every test in one code-service submission when signed in; all exercises work offline. Mock rounds exclude practical exercises. The free code workbench is a section of this tab.
- **Prepare:** searchable track catalog for all 26 supported technologies/interview areas. Select experience and topic, study one prompt at a time, reveal its answer and follow-ups, bookmark it, then assess confidence. Assessments save revision cards and schedule reviews. Question position, revealed-answer state, and assessed-question IDs persist on this device. Skipping never marks a question as assessed. Track controls connect to due reviews, mocks, and the preparation plan.
- **Resources:** track- and experience-specific interview articles/collections plus the technical references used by matching questions. All nine user-supplied Android links are included at All levels; narrower levels show relevant links. A reading note identifies the Samset article as unavailable for text review. Resource links need a browser/internet and may require membership; the in-app Q&A remains offline.
- **Ask PrepDesk:** a chat tab for any interview or engineering question. Signed in with AI enabled, answers come from the server, grounded in the related library questions it finds, and link to them so you can open or add them to your deck. Offline, signed out, or if AI is unavailable, Ask answers from the closest saved questions instead. One-tap buttons on question cards in Learn, Review, and Library (Explain simpler, Real example, Show code, Follow-ups) open Ask with that request.
- **Navigation and theme:** five bottom tabs (Today, Learn, Ask, Review, More), with the other screens grouped under More. A calm indigo theme follows the system light/dark setting.
- **Library:** 1,157 questions across 26 tracks, including 10 practical exercises (Android and Kotlin pilot) and 76 retained basic questions. Search by concept and filter by track, topic, experience, round, and bookmarks. Rich exercises appear before the retained basic material.
- **Question details:** short answer, deeper explanation, expected points, common mistakes, follow-ups, illustrative code where appropriate, content version, date, and source links.
- **Practice:** on-device spaced repetition at 1/3/7/14 days after successful reviews; misses are immediately due.
- **Mock:** every track is selectable, alongside All areas and My deck. Up to five questions are balanced across available round types. When at least five enriched exercises match, the round uses those; narrower pools can include retained basic questions. Two minutes for the main answer, one minute for follow-ups, then a self-assessment checklist. Drafts and deadlines survive tab changes and app recreation. Weak answers enter the revision deck. A narrow experience filter may yield a shorter round.
- **History:** saved main answers, follow-up notes, checklist points, elapsed main-answer time, and versioned reference-answer snapshots.
- **Plan, focus timer, custom cards:** experience-based question selection, incremental deck additions, Pomodoro timer, and personal question cards. Focus deadlines persist across tabs and restart; completion is counted once when the app observes expiry. No background alarm/notification is scheduled.

Mock scores are self-assessments, not automated evaluation or hiring predictions. Code includes an optional authenticated Judge0 workbench with source/stdin, compiler output, and explicit consent. AI coaching and native voice transcription are optional configured server features; checklist scores remain self-assessed.

## Content and attribution

`content/questions.json` retains the 76 general questions. Track-specific `content/*-questions.json` files contain PrepDesk-written exercises and explanations. The generator discovers these packs automatically. `content/sources.json` identifies 93 learning references. `content/resources.json` assigns 39 curated interview/preparation resources to tracks and experience tiers; the Prepare tab also derives technical references from each track's questions. `content/coverage.json` is a generated inventory of counts, experience-level coverage, and sources per track.

| Track | Total | Exercises with sources and rubrics |
| --- | ---: | ---: |
| Android | 32 | 32 |
| Java and Spring | 12 | 8 |
| Python | 11 | 8 |
| DevOps and Cloud | 14 | 8 |
| JavaScript and TypeScript | 8 | 8 |
| React | 8 | 8 |
| Node.js | 8 | 8 |
| Kotlin | 8 | 8 |
| Go | 8 | 8 |
| C# and .NET | 8 | 8 |
| C and C++ | 8 | 8 |
| Rust | 8 | 8 |
| iOS and Swift | 8 | 8 |
| Flutter | 8 | 8 |
| Frontend | 15 | 8 |
| Backend | 15 | 8 |
| Mobile | 15 | 8 |
| Database | 14 | 8 |
| System Design | 14 | 8 |
| Testing and QA | 13 | 8 |
| Security | 11 | 8 |
| AI and Data | 11 | 8 |
| DSA and Coding | 11 | 8 |
| Tech Lead | 14 | 8 |
| Project Management | 14 | 8 |
| HR and Behavioral | 12 | 8 |

Every track has at least two sourced exercises at each experience tier: 0–2, 3–6, 7–10, and 10–15 years. These tiers are study guidance, not universal hiring standards. This is a starter collection across common stacks and interview areas; it does not exhaust every language, framework, or employer's interview process.

Amit Shekhar's [Android Interview Questions repository](https://github.com/amitshekhariitbhu/android-interview-questions) is the primary **topic reference** for the Android track. It is not the author of PrepDesk's answers/scenarios, and this app is not affiliated with or endorsed by Amit Shekhar or Outcome School. Official Android and Kotlin documentation supplies additional learning links. External articles and videos are linked, not copied into the app. The Apache-2.0 designation applies to the referenced repository, not automatically to its external links.

The reading catalog includes the supplied Anand Gaur, Mansi Shah, Sandeep Kella, Neeraja Gandla, and samsetdev Android/Kotlin articles; Yangshun Tay's frontend/technical interview handbooks; Sudheer Jonna's React/JavaScript collections; Donne Martin's system design primer; Arie Bregman's DevOps exercises; and Devinterview.io collections for Python, Java, Node.js, Go, Swift, Flutter, C#, C++, Rust, SQL, and Kotlin. These are external reading links with author attribution, not copies of their content or claims of endorsement.

Other material is informed by Oracle Java and Spring documentation, the Python documentation, MDN and TypeScript, React, Node.js, Go, Microsoft .NET/EF Core, the C++ Core Guidelines, the Rust book, Swift/Apple, Flutter, PostgreSQL, Kubernetes, Docker, Terraform, Google SRE, OWASP, Playwright, pytest, W3C accessibility tutorials, MIT OpenCourseWare algorithms, scikit-learn, Google ML, the Scrum Guide, and Google re:Work. Source links are included in each exercise for further study. They support the learning topics; the exercises are not represented as verbatim questions asked by those organizations. The original basic questions remain distinguishable by their lack of enriched details.

To update content:

1. Edit or add a `content/<track>-questions.json` pack. Keep each question ID permanent and existing area names stable. Increase its `version` when changing published content.
2. Supply original explanations and appropriate source links. Validate linked material and topic relevance before publication.
3. Run `node tools/generate-questions.mjs` to validate and generate the shared Kotlin seed pack and coverage report. New exercises require an explanation, common mistake, at least three rubric points, at least two follow-ups, sources, and a date. Generated packs use separate functions to keep the JVM initializer within its method-size limit.
4. Practical exercises live in `content/<track>-practical-questions.json` and use the `Practical` round with a `practical` object: `kind`, `language`, `task`, `starter`, `solution`, `hints`, `tests` (`input`/`expected` pairs), and an optional `harness`. A harness is appended to the learner's code and must print one `PASS n` or `FAIL n: …` line per displayed test. Run `node tools/verify-practicals.mjs` (needs `kotlinc` and `java`) to confirm each reference solution passes and each starter compiles without already passing. Write code compatible with older Kotlin versions, because the code service may run one.
5. Edit `content/resources.json` to assign a source to tracks, experience levels, and a reading note. Resource validation rejects unknown source IDs, unsupported areas, invalid levels, and duplicates. Run `node tools/generate-questions.mjs --check` in verification to catch stale generated content.
6. Build the app. On launch, higher content versions are imported transactionally without resetting progress or bookmarks. Older versions cannot overwrite newer content.

Current packs ship with the app. Account supports email OTP sign-in, explicit cloud sync with revision conflicts and recovery backups, remote content updates, native JSON backup import/export, secure session storage, sign-out, and account deletion. Content publishing uses an administrator CLI and server-only transaction; there is no browser editor/admin UI. Optional AI feedback, native recording/transcription, and Judge0 execution are routed through an authenticated Edge Function with per-user quotas. They are unavailable until provider credentials are deployed.

See [Supabase setup and release steps](docs/SUPABASE_SETUP.md), the [complete reading catalog](docs/INTERVIEW_RESOURCES.md), and the [privacy-policy draft](docs/PRIVACY_POLICY_TEMPLATE.md). This checkout is a tested implementation, not a deployed service or store release. It still needs the actual cloud project, SMTP/providers, operator/contact/policy, signing, staging checks, and physical-device verification.

## Architecture

MVVM in four Kotlin Multiplatform Gradle modules, wired with Koin. Hilt and Dagger are Android-only, so Koin provides dependency injection for both Android and iOS.

| Module | Depends on | Responsibility |
| --- | --- | --- |
| `:domain` | — | Plain Kotlin models and rules: spaced repetition, study quests, mock scoring, readiness, countdown plans, XP, badges, challenges, and Answer Rush. Also the repository contracts in `Repositories.kt`: a read-only `ProgressSource` (progress and question bank), `AppStatus` (save status and notices), feature repositories (`DeckRepository`, `StudyRepository`, `MockRepository`, `FocusRepository`, `RewardsRepository`, `BackupRepository`), `AccountRepository` (sign-in, sync, conflicts, content updates), and `ToolsRepository` (AI feedback, transcription, code execution). No UI, database, or network code. |
| `:data` | `:domain` | Implementations: the `Local*Repository` classes over `ProgressStore`, which owns the Room database (`PrepRepository`), and `SupabaseAccountRepository`/`SupabaseToolsRepository` over the Supabase API. Also the bundled question bank, the generated `ServiceConfig`, and the Koin `dataModule`. |
| `:ui` | `:domain` | One `ViewModel` per screen exposing an immutable UI state `StateFlow`, plus stateless Compose screens, the `App` root, and the Koin `viewModelModule`. Shared components are in `ui.components`; `Navigator` holds cross-screen navigation. |
| `:composeApp` | all three | The Android application and the iOS `ComposeApp` framework: `PrepDeskApplication`, `MainActivity`, `MainViewController`, and `initKoin`, which joins the platform module (database and secure session store) with `dataModule` and `viewModelModule`. |

- **Repositories:** `:ui` does not depend on `:data`, so ViewModels can only use the contracts; the compiler rejects any reference to `ProgressStore`, `OperationQueue`, or the API. Progress is still saved as one aggregate by `ProgressStore`, so backups, sync, conflict resolution, and changes spanning features (finishing a mock answer updates the deck, history, and the round) stay atomic. State transitions are pure domain functions shared by the repositories and test fakes.
- **Writes:** every database and network change goes through `OperationQueue`, a serial queue on an application-lifetime coroutine scope. Writes never interleave and are not cancelled by rotation or by leaving a screen. Callers that need the result (for example, starting a quest) await it instead of watching a "saving" flag.
- **ViewModels:** they are retained across configuration changes. Heavy derived state (readiness, daily plan, Library filtering) is computed on `Dispatchers.Default`. The Answer Rush game loop lives in `PlayViewModel`, so a game survives rotation.
- **Dependency injection:** `:data` defines `dataModule` (singletons bound to their contracts) and `:ui` defines `viewModelModule` (`viewModelOf`). Platforms start Koin through `initKoin` with their database and secure session store: `PrepDeskApplication` on Android and `MainViewController` on iOS. Screens obtain ViewModels with `koinViewModel()`.
- **Tests:** `:data` tests domain rules against the real question bank (`commonTest`) and covers persistence and sync (`RepositoryTest`, `SyncTest`). `:ui`'s `ViewModelTest` runs ViewModels against in-memory fakes (`Fakes.kt`) with no database or Android runtime. `:composeApp`'s `ArchitectureTest` starts the real Koin graph with an in-memory database, resolves every ViewModel, and exercises Library filtering, Answer Rush scoring, and drill navigation end to end.

## Database and upgrades

Room 2.7.2 with bundled SQLite supports Android and iOS through a shared schema. Room is the source of truth for questions. On launch, only the id and version of each stored question are read: questions whose version matches the bundled seed list use the bundled objects, and only questions replaced by a content update are read and decoded. Content changes always increase the version, so equal versions mean equal content. Study sessions use a nullable metadata document, so this addition needs no schema change and upgrades existing databases without resetting user progress. The database contains indexed question records, sources, question/source links, deck cards/progress, bookmarks, dated activity, metadata, mock sessions, and attempts. Per-question structured details and per-attempt historical snapshots use versioned JSON documents; user state is no longer one JSON blob.

Library cards point to stable question IDs and resolve current answers from the content table. Custom cards retain their own text. Content updates preserve review state; historical attempts preserve the answer and rubric that were used at the time.

The first launch migrates the former Settings `state` value inside a transaction. Card IDs, custom text, review intervals/due dates, activity, focus counts, and plan settings are preserved. Exact prompt/answer matches link to the library. Old implicit plan defaults are handled explicitly. The old Settings value remains untouched as a recovery copy. Invalid JSON or duplicate IDs produce a visible loading error rather than silently resetting progress.

Room schema version 1 is exported under `data/schemas/`. Future schema changes must increase the version, provide non-destructive migrations, and test upgrades from the exported schemas. No destructive fallback is configured.

## Build and verify

Open in Android Studio and run the `composeApp` app. Use JDK 17+ and the configured Gradle 9.6 distribution. The Gradle wrapper is included. Android compilation requires SDK 37; target SDK is 36.

```sh
node tools/generate-questions.mjs --check
./gradlew :composeApp:assembleDebug testDebugUnitTest
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Tests cover content filtering across stacks, experience-tier coverage, per-track balanced mock selection, stable IDs, old default-value migration, custom content preservation, checklist assessment, review scheduling, real SQLite transactions/foreign keys, malformed-state recovery, non-destructive pack imports into an existing database, bookmarks, persisted mock snapshots, reading coverage and experience filters, reveal-before-assessment, study/revision scheduling, and study-session recovery after repository recreation.

For iOS, open `iosApp/PrepDesk.xcodeproj` and run the shared `PrepDesk` scheme on an Apple Silicon simulator. The build phase embeds `ComposeApp`; configure a signing team for devices or archives. Deployment target is iOS 15. Public service configuration must be present in the Xcode build environment.

Additional checks:

```sh
npm ci --ignore-scripts
npm run test:server
npm run test:database
xcodebuild -project iosApp/PrepDesk.xcodeproj -scheme PrepDesk -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Server tests use fake provider responses. Database checks execute the real migration in PostgreSQL via PGlite with simulated Auth roles. Neither substitutes for staging checks against your hosted services. CI configuration is included under `.github/workflows/verify.yml`; it has not been run in hosted GitHub Actions from this checkout.
