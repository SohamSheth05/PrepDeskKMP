# PrepDesk Supabase setup and release

The repository includes the client integration, PostgreSQL migration, one Edge Function, content publisher, Android signing configuration, and iOS host. No cloud project has been created or deployed from this checkout. Offline preparation works without configuration; email login, sync, question updates, AI coaching, transcription, and code execution require the steps below.

## 1. Create a project and configure email

Create a Supabase project in your chosen region. Keep its database password and service-role key in your deployment secret store. Copy the project URL and publishable key from project settings; these two values may be bundled in the app. Never put the service-role key, `sb_secret_` key, OpenAI key, or runner token into a mobile build.

Enable email authentication and configure **custom SMTP** with your verified sending domain. Supabase's default SMTP is for development, has restrictions, and should not be used for a public launch. Edit the **Magic Link** email template to show the numeric token, for example:

```html
<h2>Your PrepDesk sign-in code</h2>
<p>Enter this code in PrepDesk: <strong>{{ .Token }}</strong></p>
<p>If you did not request this code, ignore this email.</p>
```

The app calls email OTP verification with `type=email`; it does not handle magic-link deep links. Check confirmation templates for first-time sign-ins too. Choose an expiry and configure Supabase Auth email/IP rate limits. Test delivery with real addresses outside your project organization, including first signup and subsequent login. If abuse requires CAPTCHA, add a native CAPTCHA flow before enabling that requirement in Supabase; the current client does not submit CAPTCHA tokens.

Official references: [Email templates](https://supabase.com/docs/guides/auth/auth-email-templates), [Production SMTP](https://supabase.com/docs/guides/auth/auth-smtp).

## 2. Apply database access rules

Install the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started), authenticate on your deployment machine, and run from the repository root:

```sh
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push
supabase functions deploy prepdesk
```

The migration creates:

- `progress`: each account can read its own document. Writes use `save_progress`, which authenticates the caller and locks/checks the expected revision. A stale write returns HTTP 409.
- `content_releases`: clients can only read the active release. `publish_content` is server-only and atomically publishes the release plus the trusted question catalog. Content changes require increasing question versions.
- `interview_questions`: a trusted server-only reference/rubric catalog for AI assessment.
- `service_usage`: atomic daily per-user quotas for optional provider calls.
- `code_jobs`: server-only job ownership for code-output retrieval.

All user-owned tables cascade when their Auth user is deleted. There are no direct client write grants on these tables. The Edge Function has gateway JWT verification disabled to support current publishable keys, but **every action validates the bearer token against Supabase Auth before acting**, including capabilities and deletion. Do not remove that handler authentication.

Before launch, repeat the isolation and conflict checks with two real test accounts against your deployed project. Local tests exercise the SQL in PostgreSQL through PGlite; they do not validate your hosted Supabase settings.

## 3. Publish the starter library

Configure `PREPDESK_SUPABASE_URL` and `PREPDESK_SUPABASE_SERVICE_ROLE_KEY` only in your administrator shell/CI secret store, then run:

```sh
node tools/generate-questions.mjs --check
node tools/publish-content.mjs
```

The publisher checks generated content before calling the server-only transaction. It publishes 308 questions, their source records, and the reading catalog. Never add the server key to `.env.example` or commit an environment file. Use stable IDs, increase versions for corrections, and verify all sources before a future content release. A failed transaction leaves the previous active release intact.

## 4. Enable optional online tools

Copy `supabase/.env.example` to an ignored local file and fill it from your secret manager. Load it with the CLI:

```sh
supabase secrets set --env-file supabase/.env.local
```

Set `OPENAI_API_KEY`, `OPENAI_FEEDBACK_MODEL`, and `OPENAI_TRANSCRIPTION_MODEL` to models available to your OpenAI project. Feedback and Ask PrepDesk answers both use `OPENAI_FEEDBACK_MODEL` through the Responses API with strict JSON structured output and `store=false`. Ask sends the learner's question, up to six earlier turns, and the referenced library questions loaded from `interview_questions`, never client-supplied reference text. It is limited to 40 requests per user per day; transcription uses multipart m4a uploads. Select supported models using the current [structured output](https://developers.openai.com/api/docs/guides/structured-outputs) and [speech-to-text](https://developers.openai.com/api/docs/guides/speech-to-text) documentation. No key or model is supplied by this repository.

Code execution requires an authenticated HTTPS **Judge0 CE-compatible** service, configured with `JUDGE0_URL` (without a trailing slash) and `JUDGE0_AUTH_TOKEN`. The handler uses the `X-Auth-Token` convention; providers using another authentication scheme need a server adapter. Use a maintained runner with patched isolation, restricted host credentials, concurrency limits, network disabled, and an output/storage retention policy. Client submissions cannot override the server's 2-second CPU, 5-second wall, 64 MB memory, or 128 KB file limits. Do not run submitted code inside the Edge Function or on an application/database host. See [Judge0 API documentation](https://ce.judge0.com/).

A missing provider configuration disables only that provider in the capabilities response. Current daily limits are 30 AI assessments, 20 transcriptions, 30 code submissions, and 300 result polls per account. Quotas limit authenticated per-user use; configure provider/project spending caps, monitoring, signup abuse controls, and runner concurrency limits as well. Quotas alone are not a total spending limit.

Audio is recorded only after microphone permission, is limited to two minutes/3 MB, and is sent only after the user opts in. Temporary recorder files are discarded when recording ends or the screen/app becomes inactive. The Edge Function does not persist audio. Provider retention is governed by your provider account settings, not by `store=false` on the separate feedback endpoint. Verify those settings before writing a public retention promise. Schedule cleanup of old `service_usage` rows and expired `code_jobs`; the current migration does not create a scheduler or delete provider job artifacts.

## 5. Build Android and iOS with public configuration

Set these environment variables before **both** Gradle and Xcode builds:

```sh
export PREPDESK_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
export PREPDESK_SUPABASE_PUBLIC_KEY=sb_publishable_YOUR_PUBLIC_KEY
export PREPDESK_PRIVACY_URL=https://YOUR_SITE/privacy
export PREPDESK_SUPPORT_EMAIL=support@YOUR_DOMAIN
```

Account shows the configured privacy-policy link and support email. The generated Kotlin config includes only these public values and rejects server keys. For development:

```sh
./gradlew :composeApp:assembleDebug testDebugUnitTest
xcodebuild -project iosApp/PrepDesk.xcodeproj -scheme PrepDesk \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Use Android SDK API 37 (`platforms;android-37.0`) to compile current dependencies; the app targets Android 16/API 36 and supports Android 7/API 24+. Build tools 36.0.0 are sufficient. Use the checked-in Gradle wrapper and a JDK 17 installation; daemon provisioning is configured for JetBrains JDK 25. The KMP modules use the `com.android.application`/`com.android.library` plugins with AGP compatibility flags and emit deprecation warnings; move the libraries to `com.android.kotlin.multiplatform.library` and the app to a separate Android host before an AGP 10 upgrade.

The Xcode host supports iOS 15+, physical arm64 iPhones/iPads, and Apple Silicon simulators. Intel simulators are not configured. In Xcode, select your team and final bundle identifier; use a unique identifier you own. A simulator build does not validate an App Store archive or device signing.

For an Android store bundle, configure all four signing variables in `.env.example` using a private keystore/secret manager and run:

```sh
./gradlew :composeApp:productionBundle
```

This task requires service configuration, published policy/support details, and release signing. An ordinary `assembleRelease` can create an unsigned optimized APK for verification; it is not a publishable artifact. For iOS, select Release and Product → Archive in Xcode with your team and public service environment configured, then validate in Organizer. Do not commit signing files, credentials, certificates, or profiles.

## 6. Verify staging before inviting users

1. Sign up, request another OTP, verify expired/wrong codes, restart, refresh an expired session, and sign out offline. Tokens use Android Keystore encryption or iOS Keychain; backups contain no tokens.
2. Sync two devices on one account. Change both, verify the conflict choice and recovery copy, and confirm a stale upload cannot overwrite a newer revision. Sync is explicit through Account → Sync progress; background/automatic sync is not implemented.
3. Restore/export a file, reject malformed/oversized backups, and confirm cards, history snapshots, custom answers, bookmarks, and study progress survive restart.
4. Publish a higher question version and verify local progress and historical answers remain intact. Verify a downgrade or same-version correction is rejected by server publication.
5. Exercise real AI feedback, permission denial, short/long recordings, app backgrounding, consent, transcription errors, compiler errors, timeouts, result ownership, and request quotas. Run on physical Android and iOS devices.
6. Delete a disposable account and verify Auth, progress, quota rows, and job ownership are gone. Other devices, exported backups, provider artifacts, and infrastructure backups have separate retention/deletion requirements.
7. Enable hosted database backups, confirm recovery procedures, set provider spending alerts/caps, and monitor service failure rates without logging answers, recordings, access tokens, or secrets.
8. Supply the real app operator, support email, published privacy-policy URL, provider list, retention periods, and account deletion instructions. Review the privacy manifest and Google Play Data Safety/App Store privacy declarations against the actual deployed providers. No analytics/tracking SDK is installed. Complete release signing, archive validation, accessibility/device QA, and store listings before publication.

The in-app Privacy section explains feature data flows. `docs/PRIVACY_POLICY_TEMPLATE.md` is an owner-completion draft, not a hosted policy. Public release remains gated on the above deployment, provider, operator, signing, and device checks.
