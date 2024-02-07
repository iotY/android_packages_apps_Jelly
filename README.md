# (Kotlin)Quarks Browser refactored ***org.lineageos.jelly*** for AOSP compilation
Jelly browser with ads blocker, a few ui changes and some bug fixes.
Ads blocker and favicon in search bar based on this: https://github.com/CarbonROM/android_packages_apps_Quarks


### Ads blocker:
 * https://pgl.yoyo.org/as/serverlist.php?hostformat=nohtml&showintro=0

### More Search-engine(s):
chosen one (via /Settings/) triggered, from any selected text (anywhere via longpress)
 * Gibiru
 * Mojeek
 * Qwant
 * SearX
 * StartPage
 * Swisscows

## AOSP compilation: ***packages/apps/Quarks/***
```
use branch -b aosp (org.lineageos.jelly 21.0+)
```

```
etc/sysconfig/?.xml 
```
>__\<hidden-api-whitelisted-app package="org.lineageos.jelly"/\>__

prim-origin: https://github.com/LineageOS/android_packages_apps_Jelly
