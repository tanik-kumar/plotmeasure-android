# Changelog

All notable changes to this project will be documented in this file.

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
