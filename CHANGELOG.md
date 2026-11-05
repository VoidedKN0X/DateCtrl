# Changelog

What changed in each DateCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/DateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
