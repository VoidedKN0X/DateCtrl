# DateCtrl

Calendar app (month, agenda, events, reminders) in the VKNXWorks Ctrl series. Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Reads and writes Android's CalendarContract provider; keeps no copy of events.

## Layout

- `data/calendar/CalendarRepository.kt`: calendars, instances (occurrences) for a range, event details, saving (diffed), exceptions for one occurrence, local calendars; reloads on a ContentObserver.
- `data/calendar/Models.kt`: CalendarInfo, Instance (all-day dates are UTC), EventDetail, EventDraft, RRULE/DURATION helpers.
- `reminders/Reminders.kt`: EVENT_REMINDER receiver, CalendarAlerts → notifications, Snooze/Dismiss.
- `Launch.kt`: VIEW events/<id> and time/<ms>, INSERT, EDIT. `EditActivity.kt`: add/edit for other apps (not single-task, so the caller gets the result).
- `ui/month`, `ui/agenda`, `ui/calendars`: the three tabs. `ui/event`: details and the editor.

## Hard rules

- **No `INTERNET` permission, ever** (the manifest removes it; CI fails the build if the APK has any network permission).
- **Google-free:** no Google libraries, services or SDKs, including transitive ones (CI checks `releaseRuntimeClasspath`). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Settings open only from the profile picture. No settings tab, menu or gear icon, no You tab.
- Must work as a normal user app on any ROM (Android 13+), not only when built into the system.
- All-day events are stored in UTC (midnight to midnight); timed events in their own time zone. Repeating events use DURATION, never DTEND.
- Only write columns that changed, and never write to calendars without contributor access. Delete and rename only calendars of `ACCOUNT_TYPE_LOCAL`.
- Notification actions go through the non-exported `ReminderActionReceiver` with immutable PendingIntents.

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
