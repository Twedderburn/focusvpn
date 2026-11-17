# FocusVPN: remaining steps

Claude updates this as the work moves. `[x]` = done, `[~]` = in progress, `[ ]` = not started.
Last updated: 2026-11-16

## Done so far
- [x] PRD written
- [x] Project scaffolded and filter ported (WebFilterTest passing)
- [x] Status screen state logic (protectionState)
- [x] Hashed blocklist (5 MB, about 30 ms load) with category bits
- [x] Grow-only build check; first release keeps the 2026-11-16 list (642,645 domains)

## 1. SafeSearch and optional lists
- [x] SafeSearch rewrite: google.* → forcesafesearch.google.com, bing.com → strict.bing.com, duckduckgo.com → safe.duckduckgo.com
- [x] Test for the SafeSearch rewrite (6 tests)
- [ ] Gambling list from HaGeZi, bundled as blocklists/gambling-domains.txt, with an update script
- [ ] Social sites list (Reddit, X, TikTok, Instagram, Facebook, Snapchat; not YouTube)
- [ ] YouTube Restricted Mode (restrict.youtube.com)
- [ ] Custom sites (add only, never remove)
- [ ] One-way toggles: 3-second hold, "You won't be able to undo this", saved so they can't be undone
- [ ] Test: no way to loosen any setting

## 2. Setup and status
- [ ] Five-screen setup flow in plain language
- [ ] Samsung and Pixel "always-on" step cards
- [ ] Status screen: Protected / Not protected, "Test a site" (Blocked: <category> / Not blocked)
- [ ] Hidden Advanced: device-owner lock-in instructions (adb)

## 3. When protection drops
- [ ] Notification: "Your filter is off - tap to turn it back on"
- [ ] Restart after reboot and app update (BootReceiver)
- [ ] Handle onRevoke (another VPN took over)

## 4. Release
- [ ] Release keystore at D:\Android\keys\focusvpn.jks (password file outside the repo)
- [ ] Release build signed with the FocusVPN key
- [ ] GitHub repo Twedderburn/focusvpn (public, GPL-3.0) and first push (needs your OK)
- [ ] GitHub Release with the APK
- [ ] Daily update check ("A new version with more blocked sites is ready")
- [ ] Pages install guide with Download button and QR code
- [ ] Run snapshotReleasedBlocklists at release

## 5. On the Galaxy S25
- [ ] Install the signed release APK
- [ ] pornhub.com fails to load
- [ ] Google image search is SafeSearch-filtered
- [ ] Normal browsing works
- [ ] Filter survives a reboot
