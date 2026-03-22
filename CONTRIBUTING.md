# Contributing

## Scope

PlotMeasure focuses on measurement workflows for cadastral, survey, and revisional PDF maps on Android.

## Setup

1. Install Android Studio with API 34 SDK.
2. Clone the repo.
3. Create `keystore.properties` only if you need signed local release builds.
4. Run:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Contribution Guidelines

- Keep measurement points in PDF page coordinates, not screen coordinates.
- Preserve calibration accuracy and double-precision geometry calculations.
- Prefer small, reviewable commits.
- Add or update tests when changing geometry, calibration, or export logic.
- Avoid committing secrets, keystores, or local machine configuration.

## Pull Request Checklist

- Code compiles with `./gradlew assembleDebug`
- Unit tests pass with `./gradlew testDebugUnitTest`
- README or changelog updated when behavior changes
- No signing secrets or generated build outputs committed
