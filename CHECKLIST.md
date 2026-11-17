# FocusVPN: remaining steps

Claude updates this as the work moves. `[x]` = done, `[~]` = in progress, `[ ]` = not started.
Last updated: 2026-11-17

## Done so far
- [x] PRD written
- [x] Project scaffolded and filter ported (WebFilterTest passing)
- [x] Status screen state logic (protectionState)
- [x] Hashed blocklist (5 MB, about 30 ms load) with category bits
- [x] Grow-only build check; first release keeps the 2026-11-16 list (642,645 domains)

## 1. SafeSearch and optional lists
- [x] SafeSearch rewrite: google.* → forcesafesearch.google.com, bing.com → strict.bing.com, duckduckgo.com → safe.duckduckgo.com
- [x] Test for the SafeSearch rewrite (6 tests)
- [x] Gambling list from HaGeZi (662,330 domains, GPL-3.0), bundled as blocklists/gambling-domains.txt, with an update script; baseline saved
- [x] Social sites list (Reddit, X, TikTok, Instagram, Facebook, Snapchat; not YouTube)
- [x] YouTube Restricted Mode (restrict.youtube.com)
- [x] Custom sites (add only, never remove)
- [x] Custom sites refuse essential services (Google, Apple, Microsoft, Samsung, WhatsApp, NHS, gov.uk, PayPal, GitHub...) and endings like co.uk
- [x] One-way switches on screen (checked on the emulator): 3-second hold, "You won't be able to undo this", saved so they can't be undone
- [x] Test: no way to loosen any setting (10 tests)

## 2. Setup and status
- [x] Five-screen setup flow in plain language (checked on the emulator: welcome, extras, turn on, keep it on, status)
- [x] Samsung and Pixel "always-on" step cards (Pixel cards and "Open settings" checked on the emulator; Samsung cards check on the S25)
- [x] Status screen: Protected / Not protected, "Test a site" (Blocked: <category> / Not blocked)
- [x] Hidden Advanced: device-owner lock-in instructions (adb). Tap the status title 7 times. Lock-in now also blocks uninstall; blocking "clear data" is not done yet

## 3. When protection drops
- [x] Notification: "Your filter is off - tap to turn it back on" (checked on the emulator: appears when Android wants its OK again, tap reopens the app and asks, clears once the filter is back)
- [x] Restart after reboot and app update (BootReceiver) (checked on the emulator: back on after a reboot and after an update, without opening the app)
- [x] Handle onRevoke (another VPN took over): comes straight back if allowed, otherwise shows the notification (a real second VPN app is checked on the S25)

## 4. Release
- [x] Release keystore at D:\Android\keys\focusvpn.jks (password file outside the repo). Back this folder up somewhere safe: losing it strands every installed user
- [x] Release build signed with the FocusVPN key (verified with apksigner: CN=FocusVPN)
- [ ] GitHub repo Twedderburn/focusvpn (public, GPL-3.0) and first push (needs your OK)
- [ ] GitHub Release with the APK
- [x] Daily update check ("A new version with more blocked sites is ready") (checked on the emulator; the Download button opens the install guide)
- [~] Pages install guide with Download button and QR code
- [x] Run snapshotReleasedBlocklists at release (v0.1)

## 5. On the Galaxy S25
- [ ] Install the signed release APK
- [ ] pornhub.com fails to load
- [ ] Google image search is SafeSearch-filtered
- [ ] Normal browsing works
- [ ] Filter survives a reboot
