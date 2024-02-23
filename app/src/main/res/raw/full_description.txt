# (Kotlin)Quarks Browser refactored ***org.lineageos.jelly*** for AOSP compilation
Jelly browser with ads blocker, support for android 6.0+, a few ui changes and some bug fixes.
Ads blocker and favicon in search bar based on this: https://github.com/CarbonROM/android_packages_apps_Quarks

### Ads blocker:
 * https://pgl.yoyo.org/as/serverlist.php?hostformat=nohtml&showintro=0

### Offline reading:
 * .mht (chromiumPC compatible)
 * /Android/data/com.oF2pks.jquarks/files/*.mht
 * ✇Favorites
 * screen Shortcuts
 
### tab(s) manager:
 * tile & iconShortcut for allTabs kill
 * randomized UserAgent for each tab https://coveryourtracks.eff.org/kcarter?aat=1
 
### external launches:
 * local xml/mht/html/svg/eml, for both ^content^ (X-plore) & ^file^ (aosp/Files or GhostCommander)
 * local video (with screen-off audio)
 * ShareLink
 * ShareContent
 * web search
 
### aditionnal features
 * pageIcon in urlBar: click to Refresh & longclick for GoForward
 * secureIcon in urlBar: click to sslCertificate & longclick to /Settings
 * ping action, x509 and links to VirusTotal/MyWOT via sslCertificate screen
 
### new Settings
 * force NightMode in webview (min version 76!)
 * adjustable size for urlBar height
 * toggle URL vs Title in urlBar
 * anti-tracing & info for UserAgent
 
### More Search-engine(s):
chosen one (via /Settings/) triggered, from any selected text (anywhere via longpress)
 * Gibiru
 * Mojeek
 * Qwant
 * SearX
 * StartPage
 * Swisscows

### (android TV menu compatibility (+ w.i.p. samsung Dex))

## AOSP compilation: ***packages/apps/Quarks/***
```
use branch -b aosp (org.lineageos.jelly 21.0+)
```

```
etc/sysconfig/?.xml 
```
>__\<hidden-api-whitelisted-app package="org.lineageos.jelly"/\>__

prim-origin, Apache License v2.0 https://github.com/LineageOS/android_packages_apps_Jelly
