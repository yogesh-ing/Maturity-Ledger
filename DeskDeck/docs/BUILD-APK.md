# Building the APK

Every command below is run from `C:\learning\Andriod\DeskDeck\android` in PowerShell, unless a step
says otherwise. The app has no native code, so a plain debug build needs nothing but the JDK and the
Android SDK.

## What has to be installed

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Android SDK platform | 36 | `%ANDROID_HOME%\platforms\android-36` exists |
| Android build-tools | 36.x | `%ANDROID_HOME%\build-tools\36.0.0` exists |
| platform-tools (adb) | any recent | `adb version` |

`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) must point at the SDK, for example
`C:\Users\<you>\AppData\Local\Android\Sdk`. If the variable is missing, Gradle can still find the SDK
through `android\local.properties`:

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

`local.properties` is git-ignored, so this is a per-machine file.

## Build the debug APK

```powershell
cd C:\learning\Andriod\DeskDeck\android
.\gradlew.bat assembleDebug
```

Output:

```
android\app\build\outputs\apk\debug\app-debug.apk
```

The debug variant installs as `dev.deskdeck.panel.debug`, so it can sit next to a release build. The
first build downloads Gradle 8.13 and the Android Gradle Plugin (network needed) and takes roughly
5–10 minutes; later builds are much faster because `org.gradle.caching=true`.

## Install it on the phone

Phone connected over USB with debugging enabled, then either:

```powershell
.\gradlew.bat installDebug
```

or, with the APK already built:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`-r` keeps the app data (the paired token) — but a new build with a changed native bridge is safest as
a fresh install, so uninstall first if the panel shows stale behaviour:

```powershell
adb uninstall dev.deskdeck.panel.debug
```

Upgrading from a build with the same `versionCode` works, but bump the version for anything you keep:

- `android\app\build.gradle.kts` → `versionCode` and `versionName`
- `agent\src\main.ts` → `VERSION`, and `agent\package.json`

## Clean rebuild (when the build looks stuck or stale)

```powershell
.\gradlew.bat --stop
Remove-Item -Recurse -Force .\app\build, .\.gradle -ErrorAction SilentlyContinue
.\gradlew.bat assembleDebug --no-daemon
```

The debug build has no signing configuration to set up; it is signed with the local debug key.

## Release APK (signed, fails closed)

Release signing is read from Gradle properties or the environment. Put these in
`%USERPROFILE%\.gradle\gradle.properties` (never in the repository):

```
DESKDECK_STORE_FILE=C:\\keys\\deskdeck.jks
DESKDECK_STORE_PASSWORD=...
DESKDECK_KEY_ALIAS=deskdeck
DESKDECK_KEY_PASSWORD=...
```

Then:

```powershell
.\gradlew.bat assembleRelease
```

Output: `android\app\build\outputs\apk\release\app-release.apk`, installable as `dev.deskdeck.panel`.
If the keystore settings are missing, the build **fails on purpose** instead of falling back to the
debug key (`build.gradle.kts`, `release` block).

## Checking the build

```powershell
.\gradlew.bat lintDebug        # NewApi is fatal: a missing API guard crashes Android 6 phones
adb shell dumpsys package dev.deskdeck.panel.debug | findstr versionName
```

## Common failures

| Message | Cause and fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME`, or add `sdk.dir` to `android\local.properties` |
| `Failed to install the following SDK components ... licenses` | Run `"%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses` and accept |
| `Unsupported class file major version` | Gradle is running on an old JDK; point `JAVA_HOME` at JDK 17+ |
| `Could not resolve com.android.tools.build:gradle:8.11.0` | No network to `dl.google.com` / `repo.maven.apache.org` |
| Build hangs at `:app:compileDebugKotlin` on a locked-down machine | Already handled: `kotlin.compiler.execution.strategy=in-process` in `gradle.properties` |
| `Running multiple emulators with the same AVD` | `adb kill-server`, delete `*.lock` in `%USERPROFILE%\.android\avd\<avd>.avd`, or start with `-read-only` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A build with a different signing key is installed; `adb uninstall` it first |

## Build from a fresh machine (no SDK yet)

Only when the SDK really is absent; it needs a machine where you may install software.

```bash
# JDK 17 (Windows: use the Temurin .msi instead)
curl -fL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
mkdir -p "$HOME/jdk" && tar xzf jdk.tar.gz -C "$HOME/jdk" --strip-components=1
export JAVA_HOME="$HOME/jdk" PATH="$HOME/jdk/bin:$PATH"

# Command-line tools
curl -fL -o cmdline.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p "$HOME/android-sdk/cmdline-tools"
unzip -q cmdline.zip -d "$HOME/android-sdk/cmdline-tools"
mv "$HOME/android-sdk/cmdline-tools/cmdline-tools" "$HOME/android-sdk/cmdline-tools/latest"

SDKM="$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --sdk_root="$HOME/android-sdk" --licenses
"$SDKM" --sdk_root="$HOME/android-sdk" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# Build
export ANDROID_HOME="$HOME/android-sdk"
./gradlew assembleDebug
```
