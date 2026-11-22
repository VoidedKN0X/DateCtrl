# Changelog

What changed in each DateCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/DateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Built and checked by CI only: nothing here has been run on a phone yet.

### Fixed

- **A snoozed reminder now comes back exactly 10 minutes later.** Before, Android could show it up to about 10 minutes late, because DateCtrl could only set an inexact alarm. DateCtrl now uses an exact alarm for each snooze, which also goes off while the phone is idle. If Android ever refuses exact alarms for DateCtrl, it falls back to the old, inexact timing.
- **A snoozed reminder now survives a phone restart.** 0.6.1 said a snooze kept in the phone's calendar storage would come back after a restart, but the calendar storage actually removes waiting (not yet shown) reminders, snoozes included, every time the phone starts, and only recreates the reminders that belong to events. DateCtrl now keeps its own short list of waiting snoozes (only the event number and times, never the title, place or notes) and, when the phone has started, sets their alarms again and writes them back into the calendar storage. A snooze that came due while the phone was off is shown straight away (unless the event ended more than an hour ago). The same happens when DateCtrl is updated, when it starts, and whenever a calendar reminder comes in, so snoozes also come back after DateCtrl was force-stopped.
- **A snooze is cancelled when it's no longer needed.** Dismissing or swiping away a reminder, opening it, or deleting the event (or that one occurrence) in DateCtrl now also cancels a snooze still waiting for it, so it doesn't pop up later. A snooze for an event deleted in another app isn't shown either. Snoozing the same reminder again replaces the earlier snooze. A snooze is never shown twice, even when the calendar storage's own reminder and DateCtrl's alarm both go off.

### Security

- **Two new permissions, both granted at install without a prompt.** "Exact alarms" (`USE_EXACT_ALARM`), which Android allows calendar and alarm apps to have, so snoozes are on time; and "Run at startup" (`RECEIVE_BOOT_COMPLETED`), so DateCtrl can set snooze alarms again after a restart. At startup DateCtrl only reads its list of waiting snoozes (usually empty) and stops. DateCtrl still has no internet permission.

### Project

- **Tests** for the snooze decisions: exact or inexact alarm, the saved snooze list (format, broken entries, at most 50), what to do after a restart (set again, write back, show now, forget) and when a due snooze is shown (`SnoozePlanTest`, 12 tests, run on a computer with the Kotlin compiler: all pass).

## v0.6.1 (2026-11-22)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

Built and checked by CI only: nothing here has been run on a phone yet.

### Fixed

- **A snoozed reminder is no longer lost when DateCtrl is closed or the phone restarts.** Snooze used to live only in an alarm inside DateCtrl, which Android forgets on a restart. Like the phone's standard calendar app, DateCtrl now writes a snooze into the phone's calendar storage as a new reminder for that event, 10 minutes later (the one you snoozed is marked done). If the phone was off or restarting when the snooze was due, the calendar storage sends it again when it starts, and any other reminder that comes in also brings along a snooze that is due. While DateCtrl can run, its own alarm still shows it. Without permission to change calendars, snooze works as before.
- **Snooze timing is unchanged:** DateCtrl has no permission for exact alarms, so Android may still show a snoozed reminder up to about 10 minutes later than the 10 minutes you asked for. Making it exact would need the "Alarms & reminders" permission, which DateCtrl doesn't ask for.

### Changed

- **Quick add does less work per key press.** The text you type in Quick add is now read with patterns prepared once, instead of building some of them again on every key press. It understands the same things as before.

### Security

- **A shared event doesn't stay in DateCtrl's cache.** Sharing an event writes it as a temporary .ics file (title, place and notes in plain text) that was only removed the next time you shared something, so it could stay for weeks. Left-over shared files are now also removed when DateCtrl starts and when the app lock locks, once they are an hour old or from an earlier run.

## v0.6.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### New

- **Quick add understands French and German.** Besides English and Dutch, Quick add now reads French and German sentences, for example "dentiste vendredi à 14h30", "réunion lundi de 14h à 16h", "anniversaire le 3 décembre", "marché samedi prochain", "Zahnarzt Freitag um 14 Uhr", "Besprechung Montag von 14 bis 16 Uhr", "Geburtstag 3. März" or "Kino heute Abend". It knows the weekday and month names (full weekday names only, so words like "mar" or "so" aren't mistaken for days), aujourd'hui/heute, ce soir/heute Abend, demain/morgen, après-demain/übermorgen, "dans 3 jours"/"in 3 Tagen", "semaine prochaine"/"nächste Woche", "1er mars", times like 14h, 14h30, 14:30, 14 Uhr and 14:30 Uhr, and ranges with "à" or "bis". As before, everything is read on the phone and you check the result in the editor before saving.
- **Tests** for the French and German quick-add sentences, and a check that English and Dutch read as before (`QuickAddTest`, run on a computer with the Kotlin compiler: all 12 tests pass).

### Changed

- **Quick add help and example in all four languages.** The help text now says Quick add understands English, Dutch, French and German, with examples in your language, and the example in the text field is French or German on a French or German phone (it was English there, because Quick add didn't read those languages yet).

## v0.5.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### New

- **DateCtrl in French and German, following the phone's language.** Every screen, message and setting now also comes in French (polite "vous" form) and German (informal "du"), next to English and Dutch. Android's per-app language setting (Settings > Apps > DateCtrl > Language) now offers all four. Translated by Claude and checked by script for missing strings and placeholders; not yet read through by a native speaker or run on a phone. Quick add still understands English and Dutch only: in French and German its help text says so, and its example stays in English so it can be typed as shown.

## v0.4.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### New

- **Richer repeats.** In the editor, **Repeat → Custom…** builds a repeat of every 1 to 99 days, weeks, months or years, ending never, on a date (the last day it may happen) or after 1 to 999 times. Weekly repeats fall on the weekday the event starts on, monthly ones on its day of the month. Events with such a repeat (also from other apps) now show it in full, like "Every 2 weeks, until 5 Jan 2027", and open in the custom sheet; repeats DateCtrl can't show (several weekdays, "second Tuesday" …) are still kept exactly as they are. An end date is written the way the calendar standard asks: as a date for all-day events, and as the last second of that day in the event's time zone, in UTC, for timed events.
- **More reminder options.** The reminder list now also has 20 and 45 minutes, 1½ and 3 hours, 3 days and 2 weeks before, and **Custom reminder…** sets any number of minutes, hours, days or weeks before, up to 4 weeks. An event still has at most five reminders, as many calendar accounts allow no more.
- **Quick add.** The new lightning button on the Month and Agenda tabs opens **Quick add**: type an event the way you'd say it, in English or Dutch ("dentist friday 14:00", "lunch with Anna tomorrow 12:30-13:30", "tandarts vrijdag om 14u", "verjaardag 3 dec", "review in 3 days", "vergadering maandag van 14u tot 16u"). It shows what it understood as you type; **Continue** opens the editor filled in, so nothing is saved until you check it and tap save. A sentence without a time becomes an all-day event; one without a day goes on today (tomorrow for a time already past), or on the day picked in the month view. Dates are read day first (3/12 is 3 December). The sentence is read on the phone only; DateCtrl still has no internet permission.
- **Search across all time.** Searching in Agenda now looks through every event in your visible calendars, past and future, by title, place or description, instead of only the months shown. Results are grouped as Coming up (soonest first), Repeating events and Past (newest first); tap one to open it.
- **Time zone picker.** A timed event now has a **Time zone** row: pick any time zone by city or region. The start and end times are shown and picked in the event's own time zone, and when that differs from the phone's, the row shows the same times on this phone. Changing the time zone keeps the times on the clock (14:00 stays 14:00, now in the other zone). Events from other apps in another time zone are now shown in the editor in their own zone too.
- **Overlap warning.** While you edit a timed event, a warning lists the other events at the same time (up to three, with their times). All-day events, events you declined and the event itself don't count. It's only a warning: you can still save.

- **Settings export and import.** **Settings → Export and import** saves the first day of the week, week numbers, the default length and reminder for new events, reminder notifications and Hide in screenshots / Hide in recent apps as a JSON file in a place you pick, and reads such a file back after saying what it replaces. It never holds events (those go in .ics files), the default calendar (calendar ids only mean something on one phone), the app lock, its PIN or **Lock again**; importing can only turn the two Hide switches on, and values out of range are skipped.

- **DateCtrl in Dutch.** When your phone is set to Dutch, DateCtrl shows everything in Dutch (informal "je"), including reminder notifications and their channel; in any other language it stays English. There is no language setting in the app: it follows the phone. Day and month names already followed the phone's language and still do.

### Project

- **Tests** for the quick-add reader (English and Dutch days, dates, times and ranges) and for reading and writing custom repeats (`QuickAddTest`, `RepeatRuleTest`) and for applying a settings file (`SettingsBackupTest`), run on a computer with the Kotlin compiler.

- **Checksum list regenerated without the old entries.** `gradle/verification-metadata.xml` was regenerated on CI with the fixed workflow and committed exactly as printed (its SHA-256 matches the log). It now lists only the 378 components the build downloads today; the leftovers from earlier library and build-tool versions are gone.
- **The checksum list no longer collects old entries.** The hand-started workflow that regenerates `gradle/verification-metadata.xml` used to add the new checksums to the existing file, so entries for library versions that are no longer used stayed in it. It now clears the old entries first and writes only what the build uses, and prints the file's SHA-256 so the copy that gets committed can be checked against the log. Nothing in the app changes.
## v0.3.2 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there.

### Changed

- **Newer build tools and libraries; nothing changes in how DateCtrl looks or works.** DateCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.11.1 → 9.5.1, Gradle 8.14.3 → 9.9.2, Kotlin 2.2.0 → 2.4.21 (with its Compose and serialization compiler plugins) and Compose BOM 2025.07.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). The libraries move to the versions the newest Ctrl apps use: AndroidX Activity 1.10.1 → 1.13.0, Core 1.16.0 → 1.18.0, Lifecycle 2.9.1 → 2.10.0, Navigation 2.9.1 → 2.9.8, DataStore 1.1.7 → 1.3.0, kotlinx.serialization 1.9.0 → 1.13.0 and kotlinx.coroutines 1.10.2 → 1.11.0. No library was added, and the app is still Google-free and offline (CI checks both).
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions DateCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. DateCtrl's own keep rules are unchanged.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

## v0.3.1 (2026-11-20)

A maintenance release: **the app itself is unchanged** from 0.3.0. Nothing looks or works differently, and you don't need to do anything.

### Security

- **First release made by the new build-and-sign pipeline.** DateCtrl's releases are now built by a job that has no access to the signing key, and signed afterwards by a separate job that runs none of the build's code and checks the result against the published certificate fingerprint (see "Project updates" below and SECURITY.md). This release exists to prove that pipeline end to end: it is signed with the same key as every earlier release, so it installs as a normal update over 0.3.0.

## Project updates (2026-11-20)

Nothing in the app changed.

### Security

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Releases are signed with the same key and the same signature scheme (v2) as before, so updates install as usual.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.

### Project

- **Builds no longer use a saved Gradle cache.** Other Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause, reproduced here with a temporary diagnostic workflow (added and removed again the same day): with a restored cache Gradle still fetches the plugin files from Maven Central on every build; when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the weekly code scan start from a clean Gradle home, and the plugin portal is no longer a plugin source at all (`settings.gradle.kts`): everything the build needs is on Maven Central and Google's Maven, which a full build with the portal blocked confirmed. Dependency verification itself is unchanged and as strict as before.

## v0.3.0 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Removed

- **The Agenda home-screen widget is gone.** The Ctrl apps no longer have home-screen widgets. After the update an Agenda widget on your home screen stops working and can be removed from the home screen; your events are not touched. Everything that only existed for the widget went with it: its background refresh job on the calendar storage, the refresh after midnight and on a change of clock or time zone, and the widget refresh every time DateCtrl came to the front.

### Fixed

- **"Today" no longer goes stale after midnight.** If DateCtrl stayed open past midnight, the Agenda kept starting at yesterday and the month view kept marking yesterday as today until the screen was rebuilt. Both now follow the date: they move on just after midnight, when you come back to the app, and after a change of clock or time zone.

### Changed

- **Opens with your settings already in place.** DateCtrl now waits a few milliseconds (half a second at most) for your saved settings before it draws its first screen, so the month view no longer shows for a moment with the default first day of the week or without week numbers and then changes.

### Privacy

- **"Hide in screenshots" is on from the very first frame.** The app's window now starts hidden from screenshots and screen recordings, and that is lifted only once your saved settings say the switch is off. Before, the protection was switched on a few frames after the app opened.

## Project updates (2026-11-19)

### Project

- **The weekly code scan no longer fails at random.** CodeQL has to watch the app being compiled, but its build could take the compiled code from Gradle's build cache instead, and then the scan stopped with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it" (and GitHub mailed a failed run). The scan's build now runs with `--no-build-cache`, so the code is always compiled. No app changes, so no new version.

## v0.2.0 (2026-11-13)

Import and export .ics calendar files, and an agenda widget for your home screen.

### New

- **Import .ics files.** Calendars > Import events picks an .ics file, and other apps can open or share .ics files with DateCtrl (for example an invitation from your mail app or a file in your file manager). DateCtrl first shows what's in the file: how many events, their titles and times, and which ones are already in your calendar. Pick the calendar they go into, then tap Import. Times keep their own time zone (also in files from Outlook and other apps that name zones their own way), all-day events stay all-day, repeating events keep their repeat and skipped dates, changed occurrences of a repeating event are kept, and the file's display and sound alarms become reminders.
- **No duplicates.** An event that's already in the calendar you import into (the same event, starting at the same time) is skipped, so importing the same file twice adds nothing. Importing only adds events: nothing in your calendars is changed or deleted.
- **Safe with any file.** Files up to 8 MB and 5,000 events are read; anything bigger is refused or cut off with a note, and nothing in a file is ever fetched from the internet (DateCtrl still has no internet permission). Links from other apps are only opened when that app could read the file itself.
- **Export .ics files.** Calendars > Export events saves a whole calendar, or only the events in a date range, as an .ics file wherever you choose. Repeating events, skipped dates, changed occurrences, reminders and time zones go into the file, in the standard format other calendar apps (and DateCtrl) import.
- **Share an event.** The new share button on an event sends it as an .ics file to the app you pick, for example to mail it to someone. The file is only readable by that app, only for that share.
- **Agenda widget.** A resizable home-screen widget with today's and the next six days' events from the calendars you show, in the Ctrl look (dark and light). Tap an event to open it in DateCtrl, the date to open today, or + to add an event. It updates by itself when your events change (also after a sync) and just after midnight. With the app lock on, it only shows "Locked".

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
