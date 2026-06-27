# Building Gluco Context locally

## Prerequisites

| Tool | Minimum version |
|---|---|
| Node.js | 18 or higher |
| Java (JDK) | 21 |
| Android SDK | API 33 or higher |
| Android Studio | Any recent version (for Gradle) |

Set the following environment variables before building:

```
JAVA_HOME=<path to JDK 21>
ANDROID_HOME=<path to Android SDK>
```

---

## 1. Install dependencies

```bash
npm install
```

---

## 2. Build the web bundle

```bash
npm run build
```

This copies `webroot/` → `dist/` without transpilation.  
**Do not edit files inside `dist/` directly** — they are overwritten on every build.

---

## 3. Sync with Android

```bash
npx cap sync android
```

This copies `dist/` → `android/app/src/main/assets/public/`.

---

## 4. Build a debug APK

```bash
cd android
./gradlew assembleDebug
```

The generated APK will be at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Build a signed release APK

You will need your own keystore. Never use someone else's keystore.

Create the file `android/key.properties` with the following content:

```
storeFile=/full/path/to/your.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=key0
keyPassword=YOUR_KEY_PASSWORD
```

Then build:

```bash
cd android
./gradlew assembleRelease
```

The signed APK will be at:
```
android/app/build/outputs/apk/release/app-release.apk
```

**Delete `android/key.properties` after the build.** This file must never be committed.

---

## Project structure

```
webroot/          ← controlled source of the web bundle
dist/             ← generated artifact (do not commit)
reference/        ← human-readable version of the UI (not compiled directly)
android-plugin/   ← reference copies of Kotlin plugins
android/          ← Android project (Capacitor)
scripts/          ← auxiliary build scripts
```

---

## About the UI

The UI is a pre-compiled React bundle located at `webroot/assets/index-*.js`.  
The human-readable version is at `reference/main.readable.jsx`.  
UI patches must be applied surgically to `webroot/` — do not recompile the bundle from `reference/` without full validation.
