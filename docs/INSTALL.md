# Installing Gluco Context (sideload)

Gluco Context is distributed as an APK for direct installation (sideload).  
It is not available on the Google Play Store.

---

## Before you install

- Back up your device or any data from a previously installed version of the app.
- The APK is signed by the developer. Verify the SHA-256 hash before installing (published in each Release's notes).

---

## Step by step

### 1. Download the APK

On the [Releases](../../releases) page of this repository, download the `.apk` file from the latest release.

### 2. Verify the hash (recommended)

On your computer, before transferring to your phone:

**Windows:**
```
certutil -hashfile GlucoContext_x_xx_RCx_xx_release.apk SHA256
```

**Linux / macOS:**
```bash
sha256sum GlucoContext_x_xx_RCx_xx_release.apk
```

Compare the result with the hash published in the release notes.

### 3. Transfer to your phone

Copy the APK to your phone via USB cable, Google Drive, or any method you prefer.

### 4. Allow installation from unknown sources

On Android:  
**Settings → Security → Install unknown apps**  
Enable it for the app you will use to open the APK (usually your file manager).

### 5. Install

Open the APK on your phone and confirm the installation.

---

## Updating

Installing a new version over an existing one preserves your local data.  
Even so, **back up before updating**.

---

## Uninstalling

Uninstall normally through Android. Local app data will be removed along with the app.

---

## Requirements

- Android 8.0 (API 26) or higher
- Nightscout integration requires a URL and token provided by the user inside the app
