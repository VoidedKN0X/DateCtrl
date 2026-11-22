# DateCtrl

Calendar app (month, agenda, events, reminders) in the VKNXWorks Ctrl series. Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Reads and writes Android's CalendarContract provider; keeps no copy of events.

## Layout

- `data/calendar/CalendarRepository.kt`: calendars, instances (occurrences) for a range, event details, saving (diffed), exceptions for one occurrence, local calendars; reloads on a ContentObserver.
- `data/calendar/Models.kt`: CalendarInfo, Instance (all-day dates are UTC), EventDetail, EventDraft, RRULE/DURATION helpers.
- `reminders/Reminders.kt`: EVENT_REMINDER receiver, CalendarAlerts → notifications, Snooze/Dismiss.
- `reminders/Snoozes.kt`: a snooze is a scheduled CalendarAlerts row (state shared with the calendar storage) + an exact alarm (`USE_EXACT_ALARM`, `setExactAndAllowWhileIdle`, falls back to inexact if `canScheduleExactAlarms()` is false) + a record in the private `snoozes` SharedPreferences (ids and times only, no titles). `SnoozeRestoreReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED), app start and every EVENT_REMINDER re-arm from that record: AOSP CalendarProvider2 deletes every STATE_SCHEDULED alert row on BOOT_COMPLETED, so the row alone does not survive a restart. Dismiss, opening the notification and deleting the event cancel the snooze. Decisions are in `SnoozePlan.kt` (pure, unit tested).
- `Launch.kt`: VIEW events/<id> and time/<ms>, INSERT, EDIT. `EditActivity.kt`: add/edit for other apps (not single-task, so the caller gets the result).
- `ui/month`, `ui/agenda`, `ui/calendars`: the three tabs. `ui/event`: details and the editor.
- `data/ics/`: RFC 5545 reader (`IcsParser`, `IcsZones` for TZID/VTIMEZONE) and writer (`IcsWriter`), plain Kotlin with unit tests in `app/src/test`. Reading is bounded by `IcsLimits` (size, events, line length, nesting); nothing in a file is ever fetched. `data/calendar/IcsStore.kt`: import (skips UID+DTSTART duplicates, only adds) and export through CalendarContract. `ui/ics`: import/export screens and event sharing (FileProvider, cache/shared only).
- `ImportActivity.kt`: takes .ics links from other apps (`requireContentUriPermissionFromCaller="read"`) and hands them to MainActivity, which only imports the link ImportActivity told it about.
- Unit tests: `gradlew testDebugUnitTest` (needs JDK 17, the build's toolchain).

## Hard rules

- **No `INTERNET` permission, ever** (the manifest removes it; CI fails the build if the APK has any network permission).
- **Google-free:** no Google libraries, services or SDKs, including transitive ones (CI checks `releaseRuntimeClasspath`). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Settings open only from the profile picture. No settings tab, menu or gear icon, no You tab.
- Must work as a normal user app on any ROM (Android 15+), not only when built into the system.
- All-day events are stored in UTC (midnight to midnight); timed events in their own time zone. Repeating events use DURATION, never DTEND.
- Only write columns that changed, and never write to calendars without contributor access. Delete and rename only calendars of `ACCOUNT_TYPE_LOCAL`.
- Notification actions go through the non-exported `ReminderActionReceiver` with immutable PendingIntents.

## Build toolchain

- AGP 9.5.1, Gradle 9.9.2, Kotlin 2.4.21, Compose BOM 2026.09.00, `compileSdk` 37 (Compose 1.12 needs it), `minSdk` 35, `targetSdk` 36. All versions are in `gradle/libs.versions.toml`.
- AGP 9 has built-in Kotlin: there is no `org.jetbrains.kotlin.android` plugin any more. The Kotlin version is pinned by the `kotlin-gradle-plugin` classpath entry in the root `build.gradle.kts`; the Compose and serialization compiler plugins use the same `kotlin` version. Don't re-add the Kotlin Android plugin.
- AGP 9's R8 is stricter (`-keep class X` without a member block no longer keeps the constructor; resources are shrunk together with code). Keep rules in `proguard-rules.pro` must name the members they need, and resources must be referenced through `R`, never by name.
- Any change here needs the dependency verification procedure below.

## Dependency verification

Gradle checks every downloaded library and plugin against the SHA-256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/DateCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (between the BEGIN/END markers, `base64 -d | gunzip`), check the diff (only Maven Central and Google's Maven) and commit it in the same push as the change. Never turn verification off or make it lenient.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "DateCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- Push straight to `main`; no branches or pull requests.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
