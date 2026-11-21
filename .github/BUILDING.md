# Building MirrorLink X

The project uses Android Gradle Plugin 8.7.3, Kotlin Android plugin 2.0.21, Java 17 and Android SDK 35.

From a machine with Android SDK 35 installed:

```bash
gradle :app:assembleDebug --no-daemon --stacktrace
```

The debug APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow performs the same build, checks that the APK is non-empty, and publishes the `MirrorLink-X-debug` artifact. It runs for pushes to `main` or `master`, pull requests, and manual dispatch.

The Kotlin plugin is required because all app activities and services are implemented in Kotlin. The AndroidX Gradle property is enabled for the QR scanner dependency.
