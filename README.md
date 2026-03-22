# PlotMeasure

PlotMeasure is an Android app for measuring land/plot distance, perimeter, and area directly from cadastral PDF maps. It is built in Kotlin with Jetpack Compose and keeps all measurement points in PDF page coordinates so overlays stay accurate during zoom and pan.

## About

Android app for measuring plot and land area from cadastral, survey, and revisional map PDFs using calibration and polygon tracing.

Releases: https://github.com/tanik-kumar/plotmeasure-android/releases

Topics: `android`, `kotlin`, `jetpack-compose`, `pdf`, `pdf-renderer`, `land-measurement`, `cadastral-maps`, `survey-maps`, `revisional-map`

## Install

- Platform: Android
- Minimum Android version: `minSdk 24` (`Android 7.0`)
- Latest signed release APK: `PlotMeasure-v1.0.0-release.apk`
- GitHub releases: https://github.com/tanik-kumar/plotmeasure-android/releases
- SHA-256: `d5d9cef4127d15598f0a8c70304b37300e381cd7f420c77e41ae74ddb1eb57d9`

Direct install steps:

1. Download the signed APK from the GitHub Releases page.
2. Allow installation from the browser or file manager if Android asks.
3. Open the APK and install.
4. Launch `PlotMeasure v1.0.0`.

## Screenshots

Screenshots are documented under [docs/screenshots/README.md](/Users/tanik/Projects/biharmapmeasure/docs/screenshots/README.md).

Reference set for `v1.0.0`:

- `01-home-empty-state.jpg`
- `02-page-picker-dialog.jpg`
- `03-map-loaded-overview.jpg`
- `04-calibration-large-view.jpg`
- `05-area-measurement-edit-selected-point.jpg`

## Stack

- Kotlin
- Jetpack Compose
- MVVM
- Repository pattern
- Android `PdfRenderer`
- JSON-based local persistence with `kotlinx.serialization`

## MVP

The MVP layer in this codebase includes:

- PDF import with Storage Access Framework
- Multi-page selection
- PDF rendering with visible-region high-detail rerendering
- Smooth zoom and pan
- Distance, path, and polygon area modes
- Manual, ratio, and text-scale calibration
- Live segment length, perimeter, area, and centroid
- Multiple plots on the same page
- JSON project persistence

## Improved Version

The improved layer adds:

- Edit mode with point dragging
- Long-press vertex insertion
- Selected-point deletion
- Undo/redo
- Calibration presets
- Export to JSON and CSV
- Layer visibility toggles
- Configurable Bihar land-unit conversions
- Magnifier loupe
- Local edge snapping based on PDF bitmap gradients

## Project Structure

```text
app/src/main/java/com/tanik/biharmapmeasure
├── MainActivity.kt
└── plotmeasure
    ├── core
    │   ├── calibration
    │   ├── export
    │   ├── geometry
    │   ├── pdf
    │   └── UndoManager.kt
    ├── data
    │   ├── JsonProjectRepository.kt
    │   └── SettingsRepository.kt
    ├── model
    │   └── PlotMeasureModels.kt
    └── ui
        ├── components
        ├── theme
        ├── PlotMeasureApp.kt
        ├── PlotMeasureUiState.kt
        └── PlotMeasureViewModel.kt
```

## Architecture Diagram

```text
Compose UI
  -> PlotMeasureViewModel
      -> JsonProjectRepository
      -> SettingsRepository
      -> PdfRenderEngine
      -> ReportExporter
      -> GeometryEngine
      -> CalibrationEngine

UI viewer gestures
  -> page-space points / viewport
      -> ViewModel state update
          -> repositories + geometry + export
              -> Compose redraw
```

## Coordinate Mapping

The viewer never stores interaction in screen pixels.

1. `PdfRenderEngine` exposes page size and rendered bitmap tiles.
2. The viewer keeps a transform:
   - `screenX = pageX * scale + offsetX`
   - `screenY = pageY * scale + offsetY`
3. Screen taps are converted back into page coordinates:
   - `pageX = (screenX - offsetX) / scale`
   - `pageY = (screenY - offsetY) / scale`
4. Only page coordinates are persisted in projects.

Because of that, zooming to a new tile resolution never changes stored points or computed measurements.

## Calibration Math

### Manual or Scale-Bar Calibration

If the user picks two points on the PDF with page distance `d_page`, and enters real distance `d_real` in meters:

```text
metersPerPageUnit = d_real / d_page
```

### Ratio Calibration `1:N`

Android `PdfRenderer` page units behave like PDF points, where:

```text
1 page unit = 1/72 inch
```

So for ratio `1:N`:

```text
metersPerPageUnit = N * (0.0254 / 72)
```

### Text Scale Calibration

For a printed statement like `16 inches = 1 mile`:

```text
pageUnitsForMapDistance = mapDistanceMeters / (0.0254 / 72)
metersPerPageUnit = groundDistanceMeters / pageUnitsForMapDistance
```

## Geometry Rules

- Distance uses Euclidean distance in page space
- Path length sums segment lengths
- Area uses the shoelace formula
- Concave polygons are supported
- Self-intersecting polygons are detected and flagged
- Area is blocked when the polygon is invalid

## Performance Notes

Large PDFs are handled with two render layers:

1. A full-page render for immediate viewing and global navigation
2. A viewport tile render that rerenders only the visible region at higher density during deep zoom

This reduces memory pressure versus rendering the full page at extreme zoom while keeping point placement sharp enough for cadastral work.

## Privacy and Data

- PDF files are accessed from device storage only after user selection.
- Measurement projects and settings are stored locally on device.
- Data is not uploaded by the app unless the user explicitly exports files or shares artifacts outside the app.
- Release signing files must remain private and must not be committed.

## Known Limitations

- OCR-based automatic scale extraction is not included in `v1.0.0`.
- Measurement accuracy depends on PDF quality, page calibration, and point placement precision.
- Edge snapping is bitmap-based and may be less reliable on noisy scans.
- Very large or highly detailed PDFs can still require more render time during deep zoom.

## Roadmap

- OCR-assisted scale detection
- Better snapped boundary detection for scanned maps
- Richer export layouts with annotated preview images
- Improved project management across multiple sheets
- More regional unit presets and conversion templates

## Setup

1. Open the project in Android Studio Giraffe+ or Hedgehog+.
2. Ensure the Android SDK for API 34 is installed.
3. Build with:

```bash
./gradlew assembleDebug
```

For a signed release build:

```bash
./gradlew assembleRelease copyNamedReleaseApk
```

## Usage

1. Tap `Open` and choose a PDF map.
2. Choose the page.
3. Open `Calibrate` and pick one of:
   - manual
   - ratio `1:N`
   - text scale
   - saved preset
4. Tap around the plot boundary.
5. Switch to `Edit` to drag or insert points.
6. Export JSON or CSV from the top bar.

## Tests

Run:

```bash
./gradlew testDebugUnitTest
```

## Release Notes

Current release: `v1.0.0`

- First signed Android release
- PDF calibration and polygon tracing workflow
- Distance, perimeter, and area calculation
- Local land-unit conversion support
- Export support and stable large-view editing

See [CHANGELOG.md](/Users/tanik/Projects/biharmapmeasure/CHANGELOG.md) for the detailed history.
