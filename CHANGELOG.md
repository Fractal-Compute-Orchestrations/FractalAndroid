# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v1.0.0] - 2026-11-13

### Added
- Wire-compatible `Int4BlockCodec` (`FRQ4` / `FRQD`) matching `FractalCore`'s `fractal_quant` protocol for 7.1x model upload reduction (`ModelTransmitter` and `Server_DAO`).
- Centralized `FractalPaths` resolver replacing hardcoded app data paths with `context.filesDir` and canonical path-traversal guards.
- Pure JVM-testable `OperationControl.evaluateConditions` enforcing battery temperature (`<= 40.0 C`) and `PowerManager.THERMAL_STATUS_SEVERE` gating.
- JUnit4 Kotlin test suite (`Int4BlockCodecTest`, `OperationControlTest`, `FractalPathsTest`) integrated into GitHub Actions Android CI.
- Institutional `NOTICE`, `CITATION.cff`, and FRACTAL Proprietary Source-Available & Non-Commercial Restrictive License v3.1 under Fractal Compute Orchestrations.

### Removed
- Untracked IDE workspace artifacts (`.idea/` and `.kotlin/errors/*.log`) and set executable git permissions on `gradlew`.

## [2.1.0] - 2024-05-11

### Added
- Professional project governance structure.
- Fractal Proprietary Source-Available & Non-Commercial Restrictive License v3.0.
- Contribution guidelines (`CONTRIBUTING.md`).
- Code of Conduct (`CODE_OF_CONDUCT.md`).
- Security policy (`SECURITY.md`).
- GitHub issue and pull request templates.
- GitHub Actions CI workflow for automated builds.
- Comprehensive `.gitignore` for Android projects.

### Changed
- Updated `README.md` with modern badges and improved navigation.
- Refined build configuration for 16-KB page support.

## [2.0.0] - Previous

### Added
- Local training module for on-device dataset processing.
- Hardware telemetry gating system.
- Federated learning node lifecycle management.
