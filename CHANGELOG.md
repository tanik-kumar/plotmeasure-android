# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2026-03-23

### Added

- Annotated PDF export with the measured map page drawn directly into the export
- Extra export details page with project, page, calibration, and plot summaries
- Calibration reference line/points included in exported PDF when manual calibration points are available

### Changed

- Export styling now uses thinner polygon outlines and smaller labels for cleaner printed output
- Export dialog now offers PDF alongside JSON and CSV

## [1.0.0] - 2026-03-22

### Added

- Initial Android release of PlotMeasure in Kotlin with Jetpack Compose
- PDF import with page selection and multi-page support
- Manual, ratio, and text-scale calibration
- Distance, path, and polygon area measurement modes
- Polygon validation, centroid, perimeter, and land-unit conversions
- Point editing with drag, delete, insert, undo/redo, and large-view nudge controls
- JSON/CSV export
- Signed release APK packaging workflow

### Changed

- Viewer interaction tuned for large-window calibration and measurement workflows
- High-zoom rendering refined for cadastral PDF use

### Fixed

- Overlay label drawing crash caused by off-canvas text measurement
- Calibration overlay reopening unexpectedly during point adjustment
- Multiple viewer stability issues around touch interaction and rendering
