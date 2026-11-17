# FocusVPN

An Android app that blocks adult sites in every app and browser. It is built to be permanent: once it is on
there is no off switch, and nothing can be unblocked.

- Works on the phone itself. It only looks at the names of the sites the phone asks for, and sends nothing anywhere.
- Optional extras, each one-way: gambling sites, social sites, YouTube Restricted Mode, and any site you name.
- Google, Bing and DuckDuckGo are kept on SafeSearch.
- Lists only grow. The build fails if a site blocked in the last release is no longer blocked.

Install guide: https://twedderburn.github.io/focusvpn/

## Building

Needs JDK 17 or newer and the Android SDK (API 35).

    ./gradlew testDebugUnitTest assembleDebug

`assembleRelease` signs with the FocusVPN key when its properties file is on the machine (see
`app/build.gradle.kts`), and with the debug key otherwise. Set `FOCUSVPN_SIGNING` to use your own key.

## Releasing

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease`
3. Publish a GitHub release tagged `v<versionName>`, with the APK attached as `FocusVPN.apk`. The app's daily
   check and the install guide's Download button both rely on that tag and file name.
4. `./gradlew snapshotReleasedBlocklists`, and commit the new baseline.

## Licence

GPL-3.0. The gambling list comes from [HaGeZi's DNS blocklists](https://github.com/hagezi/dns-blocklists).
