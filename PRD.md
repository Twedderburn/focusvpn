# FocusVPN — Product Requirements

Status: draft v1 · 2026-11-16 · Owner: Tyler

## 1. Summary

FocusVPN is a standalone Android app that blocks adult websites on the whole phone, in every app and browser. It is a **local** filter: a VpnService that carries only DNS lookups, answers blocked ones with "no such site", and forwards everything else to the network's normal DNS server. No traffic leaves the phone through us, and there is no server.

You set it up once and it runs. **It can only ever get stricter.** No setting, list update or app version can loosen it.

It is built from the web filter on Focus Home's `dev` branch (`DnsFilter.kt`, `DnsFilterService.kt`, `adult-domains.txt`), but it is a separate app with no QR key and no launcher.

## 2. Users and goals

- **Primary user:** an adult using it on their own phone for self-accountability. They want friction between an impulse and a site, and they want that friction to be hard to undo.
- **Audience:** includes people who are **not tech literate**. Setup must work without the user knowing what a VPN, DNS or adb is.
- **Not a goal:** parental control against a determined adversary, privacy or ad/tracker blocking, or a remote VPN tunnel.

## 3. Core principles

1. **One-way.** Anything that blocks more can be switched on. Nothing that blocks less exists.
2. **Set and forget.** There is no Stop button in the app.
3. **Plain language.** The UI never says *VPN*, *DNS* or *domain*. It says "Filter", "Blocked sites" and "Add a site to block". The one exception is that we warn before Android's own consent dialog: *"Android will call this a VPN. Nothing leaves your phone."*
4. **Nothing leaves the phone.** There is no analytics, no logs of visited sites, and no account.

## 4. Features

### 4.1 Filtering (always on)
- **Adult sites:** about 642k domains bundled from StevenBlack porn-only plus `adult-extra-domains.txt` from Focus Home dev, along with the built-in `ADULT_DOMAINS` set and the `.xxx` `.porn` `.sex` `.adult` TLDs. Each entry also blocks all of its subdomains.
- **Forced SafeSearch** (always on, no toggle): lookups for `google.*` resolve to `forcesafesearch.google.com`, `bing.com` to `strict.bing.com`, and `duckduckgo.com` to `safe.duckduckgo.com`. This means answering with the safe address instead of NXDOMAIN.
- **Closing bypass routes** (carried over from dev): block well-known encrypted-DNS providers, answer Firefox's `use-application-dns.net` canary, and detect strict Private DNS (treated as "not protecting").

### 4.2 Optional categories (one-way once switched on)
| Category | Source | Notes |
|---|---|---|
| Gambling | HaGeZi Gambling (GPL-3.0), bundled | One toggle |
| Social | Hand-curated, bundled | **A toggle per site:** Reddit, X, TikTok, Instagram, Facebook, Snapchat. YouTube is excluded because DNS can't separate Shorts from the rest of YouTube. |
| YouTube Restricted Mode | DNS rewrite to `restrict.youtube.com` | Opt-in. Hides some non-adult videos too. |
| Custom sites | Entered by the user | "Add a site to block". Permanent. |

- Switching any of these on uses **hold-to-confirm (3 s)** with the line *"You won't be able to undo this."*
- There is **no allowlist** and no "unblock" in any form.

### 4.3 Status screen (the only screen after setup)
- A large **Protected ✓** or **Not protected** state.
- Blocked count today, with a per-category breakdown. There is **no list of blocked sites**.
- "Unprotected time today": minutes the filter was down.
- **Test a site:** returns only *"Blocked: adult site"* (or another category name) or *"Not blocked"*.
- Add a site to block, and switches for the optional categories.
- A banner when a new version is available (see 4.6).

### 4.4 When protection drops
Triggers: consent revoked, another VPN takes over, strict Private DNS detected, or the service killed.
- A persistent high-priority notification: "Your filter is off — tap to turn it back on".
- Re-request VPN consent the next time the app opens.
- Count the time toward "unprotected time today".
- Restart automatically on boot and after an app update (`BootReceiver`), and come back automatically in `onRevoke` if consent still allows it.

### 4.5 Setup (aimed at non-technical users)
One action per screen, large text, no jargon.
1. **Welcome:** what it does, and that it's permanent.
2. **Choose extras:** gambling and social sites (all optional; hold-to-confirm applies).
3. **Turn on the filter:** the pre-warning line, then Android's VPN consent dialog.
4. **Keep it on:** a big "Open settings" button to VPN settings, illustrated step cards for **Samsung** and **Pixel** (chosen by device maker, generic otherwise) for enabling *Always-on VPN*, then a "Done, I turned it on" button. If the filter is later found to have been killed, show a gentle reminder.
5. **Done:** go to the status screen.

**Advanced (hidden, never part of setup):** device-owner lock-in through adb. It pins FocusVPN as the always-on VPN, disallows VPN and Private DNS configuration, blocks uninstalling, and blocks clearing app data. "Block connections without VPN" stays off, because we only carry DNS. A strong warning explains that removing it later needs adb or a factory reset.

### 4.6 Updates
- Lists are bundled in the APK. The app never downloads lists.
- Once a day the app checks GitHub's latest release. If a newer version is out, it shows *"A new version with more blocked sites is ready"* with a Download button that opens the browser. It only notifies and never installs anything itself.

## 5. Technical requirements

- **Stack:** Kotlin, Jetpack Compose with Material3, minSdk 29, targetSdk 35. Package `com.focusvpn`. Repo at `D:\Claude\focusvpn`, published to `Twedderburn/focusvpn` (public, **GPL-3.0** because of HaGeZi).
- **Tunnel:** the same design as dev. Route only `10.111.222.2/32`, set it as the DNS server, parse IPv4/UDP, use 4 forwarding threads, and `protect()` the upstream sockets. Upstream servers are the real networks' DNS servers, falling back to 1.1.1.1 and 8.8.8.8.
- **Lookup structure:** domains are stored as 64-bit hashes in a sorted `LongArray`, precomputed at build time into a binary asset and searched by binary search. Target about 10 MB of RAM. Lookup walks the parent domains, as `isBlocked` does today. There is one bit per category so the status screen can show "Blocked: <category>".
- **Grow-only lists:** the build scripts union each new list with the previous one for adult, gambling and social, and the **build fails** if any domain from the previous release is missing.
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `BIND_VPN_SERVICE`, and the device-admin receiver (dormant unless Advanced is used).
- **Signing:** a dedicated release keystore at `D:\Android\keys\focusvpn.jks`, with its password file outside the repo. Stored locally only, with no cloud copy (Tyler's decision, 2026-11-16). Losing it strands every installed user.

## 6. Distribution

- An APK attached to GitHub Releases. Not on Play for now.
- An install guide on GitHub Pages: a big Download button, screenshots for Samsung and Pixel covering "Install unknown apps", and a QR code people can scan from a computer screen.

## 7. Out of scope (v1)

Tracker and ad blocking, any allowlist or unblock, a remote VPN server, per-app network blocking, list downloads inside the app, Play Store release, IPv6 DNS handling beyond what dev already does, and iOS.

## 8. Known limits (say these honestly in the README)

- Without device owner, uninstalling or clearing data removes the filter.
- Apps that hard-code DNS-over-HTTPS by IP address can get around DNS filtering.
- Only one VPN can run at a time, so another VPN app switches FocusVPN off. That triggers 4.4.
- Users only get new list entries when they update.

## 9. Definition of done (v1)

- [ ] dev's `WebFilterTest` is ported and passing.
- [ ] Tests for the hash lookup (including parent-domain walk and category bit), the SafeSearch rewrite, the grow-only build check, and the lack of any loosening path.
- [ ] A release APK signed with the FocusVPN key and installed on the Galaxy S25.
- [ ] On the device: pornhub.com fails, Google image search is SafeSearch-filtered, normal browsing works, and the filter survives a reboot.
- [ ] The Pages install guide is live, with a working Download button and QR code.

## 10. Open items

- None. The signing key is stored locally (decided 2026-11-16).
