# Changelog

What changed in each DateCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/DateCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
