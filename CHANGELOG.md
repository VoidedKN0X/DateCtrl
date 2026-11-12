# Changelog

What changed in each DateCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/DateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.6 (2026-11-12)

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters DateCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v0.1.5 (2026-11-05)

DateCtrl now needs Android 15 or newer.

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's end early 2027, so DateCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v0.1.4 (2026-11-05)

Confirmations are readable by accessibility apps again.

### Fixed

- "Can't be undone" confirmations (such as deleting an event or a calendar) can be read again by apps that use accessibility, such as automation apps. 0.1.3 hid them from those apps; now only the lock screen, the PIN screens and "Forgot PIN?" are hidden from them. Other apps' floating windows are still kept off these confirmations, and screen readers such as TalkBack keep working everywhere.

## v0.1.3 (2026-11-05)

A security update for the app lock and for requests from other apps. Nothing changes in how you use DateCtrl.

### Security

- **No floating windows over the app lock.** While the lock screen, a PIN screen (turning the lock on or off, changing the PIN), "Forgot PIN?" or a delete or reset confirmation is showing, other apps' floating windows (chat heads, bubbles, overlays) are hidden on top of DateCtrl (Android 12 and newer), so no app can draw over the PIN pad or trick you into tapping a button. They come back as soon as that screen closes, and everywhere else in the app they keep working. This uses a new permission, "Hide other apps' floating windows", which Android grants at install without asking.
- **Lock and PIN screens are hidden from apps that misuse accessibility.** On Android 14 and newer, the same screens are marked sensitive, so only real accessibility tools such as screen readers (TalkBack) can read them, not other apps that use the accessibility API to watch the screen.
- **Stricter requests from other apps.** On Android 16 and newer, other apps can only open DateCtrl in the ways it offers (the launcher, opening an event or a day, "Add to calendar" and "Edit event"). A request aimed directly at a part of DateCtrl that doesn't accept it is now refused by Android.

### Project

- **Check a download:** the release notes now list the APK's SHA-256 checksum, and the APK has a signed build provenance attestation: `gh attestation verify DateCtrl-v0.1.3.apk -R VoidedKN0X/DateCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate fingerprint in the README still applies.
- **Checked dependencies:** the build now checks every library and Gradle plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and stops if one doesn't match.
- README: the new permission is in the permissions table, with a "Check a download" note and how dependency verification works.

## Project updates (2026-11-05)

### Project

- **Release checks:** from the next release, the release notes list the APK's SHA-256 checksum, and every release APK gets a signed build provenance attestation, so anyone can check with `gh attestation verify` that it was built by this repo's workflow from the tagged commit.
- **Workflow security:** a new zizmor check scans the GitHub Actions workflows on every change to them, and every workflow now starts with no permissions, giving each job only what it needs.
- **Code scanning:** CodeQL scans the code every week and on every app change; results go to the repo's Security tab.
- **Dependency checksums:** a new manual workflow writes the SHA-256 checksums of every library and Gradle plugin the build downloads, ready for the build to check them in the next release.

## v0.1.2 (2026-11-05)

A small security update for the app lock. Events, calendars and settings are unchanged.

### Security

- **"Forgot PIN?" now asks for your phone's screen lock.** Before, anyone holding your unlocked phone could tap "Forgot PIN?" on the lock screen and turn DateCtrl's app lock off without knowing the PIN. Now the reset first asks for your phone's own PIN, pattern, password or fingerprint. If you cancel or it fails, nothing is reset. If your phone has no screen lock at all, the reset works as before.

## v0.1.1 (2026-11-05)

A small security update. Events, calendars, settings and the app lock are unchanged.

### Security

- **Reminders can't be set off early by another app.** Android's "a reminder is due" message can be sent by any app, and DateCtrl trusted the time in it. Another app could have used a time far in the future to make DateCtrl show every upcoming reminder at once and mark them as shown, so they wouldn't have come at the right time. DateCtrl now only looks for reminders that are due now (or within the next minute), whatever time the message names.

### Privacy

- **No debug logging in release builds.** Debug, verbose and info messages from the libraries DateCtrl uses are now stripped from the app, so nothing can end up in the phone's system log. DateCtrl's own code already logged nothing.

### Project

- The build now also checks that the Gradle wrapper is an official Gradle release before building.
- `.gitignore` also covers `google-services.json`, so it can't be committed by accident.
- README: every permission in the app is listed with its Android name and what it's for, including the "dynamic receiver" permission AndroidX adds, and the Ctrl series list now has all 28 apps.

## v0.1.0 (2026-11-05)

The first release of DateCtrl: a private, offline calendar app in the Ctrl series, made to replace your phone's calendar app on any Android 13+ phone.

### New

- **Month view** with a coloured dot per event under each day, swipe between months, a Today button, and the chosen day's events below with previous and next day buttons.
- **Agenda** with every upcoming event day by day, search by title or place, three months ahead and 12 more months at a tap.
- **Add and edit events:** title, all day or start and end time, repeat every day, week, month or year (other repeats made in other apps are kept), calendar, place, description and up to five reminders. Leaving with unsaved changes asks first.
- **Repeating events:** change or delete only one occurrence or the whole series.
- **Event details** with time, calendar, repeat, place (opens in your maps app), description, reminders and guests with their replies; events in calendars you can't change are shown read-only.
- **Reminders** as notifications with Snooze (10 minutes) and Dismiss, private on the lock screen. They can be turned off in Settings if another calendar app shows them.
- **Calendars tab:** show or hide each calendar, grouped by account; add calendars on this phone and rename, recolour or delete them. Saving an event when there is no calendar yet makes one on this phone.
- **Works with other apps:** "Add to calendar" (with title, time, place and repeat filled in) returns to the app that asked; event links and "open this day" open DateCtrl.
- **Settings:** first day of the week, week numbers, the calendar, length and reminder for new events, and reminders on or off.
- **App lock** with its own PIN (at least 6 digits) and optional fingerprint unlock, **Hide in screenshots** and **Hide in recent apps**, both off by default.
- **Ctrl / Signal look** in dark and light, following your phone's theme, with layouts for phones, tablets and foldables.
