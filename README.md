# DateCtrl

**A private, offline calendar app that replaces your phone's calendar.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/DateCtrl?label=download)](https://github.com/VoidedKN0X/DateCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) ![Offline](https://img.shields.io/badge/internet-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

DateCtrl shows and edits the events in your phone's calendar storage: calendars on the phone itself and from your accounts (DAVx⁵, Exchange and others that sync through their own apps). Month view, an agenda with search, repeating events, reminders as notifications, and "Add to calendar" for other apps. It never goes online and keeps no copy of your events of its own.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/DateCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Works fully offline.** DateCtrl doesn't have the internet permission and never goes online. It reads and writes Android's own calendar storage, so accounts keep syncing through their own apps
- **Month view** with a dot per event under each day, swipe between months, Today, week numbers if you want them, and the chosen day's events below
- **Agenda:** upcoming events day by day with search by title or place, three months ahead and more on request
- **Events** with title, all-day or start and end time, repeat (daily, weekly, monthly, yearly; other repeats from other apps are kept), calendar, place, description and several reminders. Repeating events can be changed or deleted for one occurrence or the whole series
- **Event details:** time, calendar, repeat, place (opens in your maps app), description, reminders and the guest list with replies
- **Reminders** as notifications with Snooze (10 minutes) and Dismiss; on the lock screen they show the event only when your phone allows private notification content
- **Calendars:** show or hide each calendar, grouped by account; add, rename, recolour and delete calendars on this phone
- **Works with other apps:** "Add to calendar" buttons, event links and "open this day" from widgets and other apps open DateCtrl
- **App lock** with its own PIN and optional fingerprint unlock, and **Hide in screenshots** / **Hide in recent apps** (both off until you turn them on)
- **Ctrl / Signal look**, shared by every Ctrl app: dark and light, following your phone's dark theme setting, high contrast, one violet accent, Space Grotesk and JetBrains Mono; landscape, tablet and foldable layouts

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Works on any Android ROM as a normal app: stock Android, LineageOS, GrapheneOS, CalyxOS, /e/OS and the phone makers' own systems. It doesn't need root or to be built into the system.
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google.

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/DateCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `DateCtrl-v0.1.0.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons above.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds DateCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/DateCtrl` in Obtainium by hand.

**Use it as your calendar app:** Android opens events and "Add to calendar" in DateCtrl; if another calendar app is installed, Android may ask which app to use the first time. Turn reminders off in your old calendar app so you don't get each one twice. To sync a calendar with a server, add the account with a sync app such as DAVx⁵; DateCtrl shows it straight away. DateCtrl works as a normal app on any Android 13+ ROM; it doesn't need to be built into the system.

## Getting started

- **Month:** tap a day to see its events, swipe for other months, tap Today to come back. The + adds an event on the chosen day.
- **Agenda:** scroll through what's coming up, or search by title or place.
- **Calendars:** tap a calendar to show or hide it. The + adds a calendar on this phone; the pencil renames, recolours or deletes it.
- **Events:** tap an event for its details, then the pencil to change it or the bin to delete it.

Settings and About are behind your profile picture at the top right of every tab.

## Is it safe to install?

- **Open source.** All of DateCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo. Each build also checks that the Gradle wrapper is an official Gradle release, that the app has no network permission and that it has no Google libraries.
- **Always signed with the same key.** Android refuses to install an update signed with a different key over DateCtrl, and the CtrlF-Droid repo only accepts APKs signed with this key. The SHA-256 fingerprint of DateCtrl's signing certificate is:

  ```
  49:FE:02:6B:14:11:A8:E4:2A:75:B2:E8:71:A9:3E:D6:54:C6:14:1C:E7:A7:8B:34:1A:E6:2F:51:62:4D:5C:07
  ```

  Advanced users can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

**Permissions.** DateCtrl has **no internet permission**: it can't go online at all. It asks only for what it needs, when it needs it:

| Permission | What DateCtrl uses it for |
| --- | --- |
| Read your calendar (`READ_CALENDAR`) | Showing your events and calendars, and the reminders that are due. |
| Add or change calendar events (`WRITE_CALENDAR`) | Saving the events and calendars you add, change or delete, and marking reminders as shown, snoozed or dismissed. |
| Notifications (`POST_NOTIFICATIONS`) | Event reminders. |
| Use biometric hardware (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock. |

Android also lists a "dynamic receiver" permission (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) that AndroidX adds to every app so only DateCtrl itself can send it internal broadcasts; it gives the app no access to anything. DateCtrl has no internet, location, contacts, storage, microphone or camera permissions.

See [Privacy](#privacy) for what happens to your data.

## App lock

Turn it on under **Settings > Privacy and security > App lock** (tap your profile picture at the top right, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, checked by the phone's secure hardware. The PIN is still needed after the phone restarts and at least every 72 hours.
- **The PIN is never stored.** It's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts.
- **Forgot your PIN?** It can't be recovered. The lock screen offers to reset DateCtrl's own settings and turn the lock off. Your calendars and events are not deleted: they belong to your phone's calendar storage, not to DateCtrl.

**Hide in screenshots** and **Hide in recent apps** (both off by default): the first keeps DateCtrl out of screenshots and screen recordings, the second shows a blank card in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card.

## Privacy

**Telemetry-free.** DateCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It has **no internet permission**, works fully offline and never connects to the internet. Every build checks that the app has no network permission and no Google libraries.

- Events stay in Android's calendar storage, where your accounts' sync apps can see them; DateCtrl keeps no copy, its own files hold only its settings.
- Calendars made in DateCtrl are only on this phone and aren't included in backups.
- Opening a place in a maps app hands that app the place's text.
- DateCtrl is left out of Android's cloud backup and its phone-to-phone transfer.
- The Support, Report a bug, Source code and Licence links on the You page open in your browser, only when you tap them.

## How DateCtrl is made

DateCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/DateCtrl.git
   cd DateCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

DateCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, tap your profile picture: **About** at the bottom of the You page has **Support DateCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/DateCtrl/issues). Please report bugs and ideas with the [bug report and feature request forms](https://github.com/VoidedKN0X/DateCtrl/issues/new/choose). Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| **DateCtrl** (this app) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

DateCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

Fonts, bundled in the app (nothing is downloaded): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License 1.1 ([FONT-LICENSE-SpaceGrotesk.txt](FONT-LICENSE-SpaceGrotesk.txt), [FONT-LICENSE-JetBrainsMono.txt](FONT-LICENSE-JetBrainsMono.txt)).
