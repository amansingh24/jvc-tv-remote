# JVC TV Remote

A tiny Android app that turns a phone with an IR blaster (e.g. Galaxy S6 / S6 edge) into a remote for JVC TVs.

**Download:** https://github.com/amansingh24/jvc-tv-remote/releases/latest/download/JVC-Remote.apk

## Using it

Point the top of the phone at the TV, pick **Set 1** and press **Menu**. If nothing happens, try Set 2, 3 and 4. The app remembers the set that works.

| Set | For | Protocol |
|-----|-----|----------|
| 1 | Most UK JVC TVs (made by Vestel) | RC5, address 1 |
| 2 | Vestel-made, alternative address | RC5, address 3 |
| 3 | JVC LT series | Samsung32, address 0x0E |
| 4 | Older JVC-made TVs | JVC, address 0x03 |

Codes come from [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB). Arrows, volume and channel repeat while held.

## Building

No Gradle: `build.sh` runs javac, d8, aapt2, zipalign and apksigner directly. It needs JDK 17 and an Android SDK with `platforms;android-28` and `build-tools;34.0.0`:

```
TOOLS=/path/to/folder-with-jdk-and-sdk ./build.sh
```

The signing key (`release.keystore`) is not in git; updates must be signed with the same key to install over the previous version.
