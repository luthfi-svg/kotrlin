# KasKita — Android Kotlin starter

A simple offline-first class cash app built with Kotlin + Jetpack Compose.

## Features
- Member list and payment status toggle (paid/unpaid)
- Income summary based on paid members × monthly fee
- Expense entries and total expense summary
- Balance calculation
- Editable monthly fee
- Local persistence using Android SharedPreferences (works offline)
- Claymorphism-inspired pastel UI
- GitHub Actions workflow to build a debug APK

## Build locally
Open this folder in Android Studio, sync Gradle, and run the `app` configuration.

Or build with Gradle installed:
```bash
gradle assembleDebug
```

## Build APK via GitHub Actions
1. Create a GitHub repository and upload this project.
2. Push to `main` or `master`.
3. Open **Actions** → **Build KasKita APK**.
4. Download the `KasKita-debug-apk` artifact after the workflow completes.

The workflow can also be started manually using **Run workflow**.
