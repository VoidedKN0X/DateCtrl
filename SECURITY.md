# Security policy

Thank you for helping keep DateCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/DateCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of DateCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

DateCtrl has no server and no internet permission: everything stays on your phone. So these count:

- Any way for DateCtrl to send your events or calendars off the phone
- Another app being able to make DateCtrl add, change or delete events without you tapping Save or Delete
- Event details showing while the app lock is on
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Weaknesses in the app lock PIN
- Anything that lets someone run code or read files they shouldn't

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
49:FE:02:6B:14:11:A8:E4:2A:75:B2:E8:71:A9:3E:D6:54:C6:14:1C:E7:A7:8B:34:1A:E6:2F:51:62:4D:5C:07
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
