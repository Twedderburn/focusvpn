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
- [x] GitHub repo Twedderburn/focusvpn (public, GPL-3.0) and first push (OK given 2026-11-17)
- [x] GitHub Release with the APK (v0.1, FocusVPN.apk)
- [x] Daily update check ("A new version with more blocked sites is ready") (checked on the emulator; the Download button opens the install guide)
- [x] Pages install guide with Download button and QR code (live at https://twedderburn.github.io/focusvpn/; scan the QR code once with a real phone)
- [x] Run snapshotReleasedBlocklists at release (v0.1)

## 5. On a real phone (tested on a Galaxy S21, SM-G991W, Android 15; the S25 is still to do)
- [x] Install the signed release APK (v0.1 over USB)
- [x] pornhub.com fails to load (Chrome: "This site can't be reached")
- [x] Google image search is SafeSearch-filtered (www.google.com answers with the SafeSearch server's address, and image search loads; Bing and DuckDuckGo match their safe servers too)
- [x] Normal browsing works (Wikipedia and Google load in Chrome; github.com resolves)
- [x] Filter survives a reboot (S21: back on without opening the app; pornhub.com still blocked, Google still on SafeSearch)

## 6. After v0.1
- [x] Uninstall protection: "Stop FocusVPN being removed" in Block more (3-second hold, then Android's own screen). Checked on the emulator: uninstall is refused while it's on. It can still be switched off in Settings, Device admin apps, where Android shows a warning first. Released in v0.2
- [x] v0.2 released on GitHub and installed on the S21 (2026-11-16 evening)
- [ ] S21: the filter is off. The phone's log shows "always-on" was switched off from Settings at 20:48, before the v0.2 install. Open the app, tap OK, then redo the Keep it on step
- [ ] S21: the "allow notifications" question was never answered, so the "Your filter is off" alert could not show. Find out why the question didn't appear during setup
- [ ] Check that an app update keeps the filter on (untested on a real phone: it was already off when v0.2 went on)
