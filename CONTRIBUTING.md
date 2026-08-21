# Contributing to FractalAndroid

Thank you for contributing to FractalAndroid. This document outlines coding standards, development workflows, and pull request procedures for the Android edge client.

---

## Code of Conduct

All contributors are expected to uphold the [FractalAndroid Code of Conduct](CODE_OF_CONDUCT.md).

---

## Development Workflow

### 1. Environment Setup
- Install [Android Studio Jellyfish (2023.3.1+)](https://developer.android.com/studio).
- Ensure JDK 17 is configured in Android Studio Gradle settings.
- Connect a physical Android device (API 24+) with USB Debugging enabled.

### 2. Architectural Standards
- Follow modern Android architecture guidelines using the **Model-View-ViewModel (MVVM)** pattern.
- Decouple telemetry and hardware gating logic (`AppBackend/ResourceManagement/`) from presentation view models (`AppFrontend/`).
- Adhere to the [official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html).
- All markdown documentation must be technical, precise, and completely free of emojis.

### 3. Build & Test Verification

Before submitting a pull request, verify that all local checks pass:

```bash
# Run unit tests
./gradlew test

# Verify lint checks
./gradlew lint

# Build debug APK
./gradlew assembleDebug
```

---

## Pull Request Guidelines

1. Fork the repository and create a feature branch: `git checkout -b feature/your-feature-name`.
2. Maintain backward compatibility with Min SDK 24.
3. Write clean, descriptive commit messages in the imperative mood (`feat: add battery temperature exponential smoothing`).
4. Ensure all GitHub Actions CI checks pass.
