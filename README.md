<div align="center">

![Fractal App Icon](docs/assets/logo.png)

# Fractal

**A Human-Centric, Resource-Aware Edge Computing & Federated Learning Node for Android**

[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0%2B-0095D5.svg?logo=kotlin)](https://kotlinlang.org)
[![Android API](https://img.shields.io/badge/API-24%2B-073042.svg?logo=android)](https://developer.android.com/studio)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-FF6F00.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-4CAF50.svg)](LICENSE)
[![Build Status](https://github.com/Ahmad-Hassan-0/Fractal-Application/actions/workflows/android.yml/badge.svg)](https://github.com/Ahmad-Hassan-0/Fractal-Application/actions/workflows/android.yml)

---

**Empowering devices, preserving privacy, and bridging the gap between machine intelligence and human experience.**

[Philosophy](#philosophy) • [Architecture](#architecture-overview) • [System Flow](#system-workflow) • [Onboarding](#getting-started) • [Contributing](#contributing)

</div>

---

## Overview

**Fractal** is an advanced Android client designed to transform mobile devices into autonomous compute nodes within a distributed federated network. By localizing compute-heavy machine learning tasks, Fractal ensures that sensitive data remains exactly where it belongs: with the user.

In an era of centralized AI, Fractal represents a shift toward **Empathetic Engineering**. The system is built to respect the host device’s primary purpose—serving the human user. Through rigorous hardware telemetry gating, background operations are dynamically managed to ensure zero impact on device performance, thermal comfort, or battery longevity.

---

## Philosophy

### Human-Centric Edge Computing
The "Fractal" name reflects the project's vision: small, self-similar units of intelligence that, when combined, create a complex and powerful whole. 
*   **Privacy by Default**: Data never leaves the device. Only mathematical insights (weights) are shared.
*   **Resource Empathy**: The software "feels" the device's strain. If the phone gets warm or the battery drops, Fractal steps back.
*   **Democratic AI**: Decentralizing compute power gives individuals control over the future of distributed intelligence.

---

## Architecture Overview

Fractal is engineered with a strict decoupling of telemetry monitoring, execution engines, and presentation layers. This ensures stability and modularity at scale.

> [!TIP]
> For a comprehensive, low-level technical breakdown of the system's internal wiring, the [Master Architecture Diagram](docs/diagrams/architecture.drawio) is available. This file can be viewed or edited using [app.diagrams.net](https://app.diagrams.net/).

```mermaid
graph TD
    subgraph Cloud [Aggregation Server]
        MA[Model Aggregator]
        TD[Task Distributor]
    end

    subgraph FractalNode [Fractal Android Node]
        direction TB
        subgraph Frontend [Presentation Layer]
            UI[Insights & Auth UI]
            VM[Telemetry ViewModels]
        end

        subgraph Core [Execution Layer]
            RM[Resource Manager]
            LTE[Local Training Engine]
            VAL[Inference Validator]
        end

        subgraph Data [Data Layer]
            DAO[Server DAO]
            CM[Checkpoint Manager]
            Net[TLS Transmitter]
        end
    end

    TD -- "Encrypted Params" --> DAO
    DAO -- "Validates" --> VAL
    VAL -- "Initialized" --> LTE
    RM -- "Gating Signals" --> LTE
    LTE -- "Checkpoints" --> CM
    CM -- "Parameter Deltas" --> Net
    Net -- "Weight Updates" --> MA
    RM -- "Live Stats" --> VM
    VM -- "Update" --> UI
```

---

## System Workflow

### The Data Lifecycle
The following flow illustrates how a single training task progresses from discovery to completion while maintaining hardware safety.

```mermaid
sequenceDiagram
    participant S as Aggregation Server
    participant N as Network Layer
    participant R as Resource Manager
    participant E as Training Engine
    participant C as Checkpoint Manager

    Note over N,E: Standby Mode (Polling)
    N->>S: Fetch Task (id, params)
    S-->>N: Task Payload Received
    
    rect rgb(240, 240, 240)
        Note right of R: Hardware Telemetry Check
        R->>E: Resource Permit (GRANTED)
    end

    E->>E: Initialize Weights
    loop Every Epoch
        E->>E: Local Training
        E->>C: Save Checkpoint (Delta)
        R->>E: Telemetry Check (Temp/Battery)
        alt Thermal Halt
            R->>E: Resource Permit (REVOKED)
            E->>E: Pause & Wait
        end
    end

    E->>N: Transmit Parameter Deltas
    N->>S: TLS Encrypted Weight Sync
    S-->>N: Task ACK (Completed)
```

---

## Internal Module Structure

<details>
<summary><b>View Detailed Repository Map</b></summary>

```text
Fractal-Application/
├── app/src/main/java/
│   ├── AppBackend/
│   │   ├── DataManager/          # Caching & DTO initialization
│   │   ├── LocalTrainingModule/  # TFLite Training & Checkpointing
│   │   ├── Network/              # Secure DAOs & Transmitters
│   │   ├── ResourceManagement/   # Thermal & CPU Telemetry logic
│   │   ├── TaskContainer/        # Payload parsing (Image_Task)
│   │   └── Validator/            # Accuracy & Inference verification
│   ├── AppFrontend/
│   │   ├── Auth/                 # Secure Binding & Registration
│   │   ├── Home/                 # Real-time Training Dashboard
│   │   ├── Insights/             # Telemetry & Performance Charts
│   │   └── Settings/             # Global Node Configuration
│   └── AppGlobal/                # Cross-cutting Utilities & Configs
├── docs/assets/                  # Project visuals & logos
└── build.gradle.kts              # Modern Kotlin DSL Build Config
```

</details>

---

## Technical Pipeline

| Pipeline Stage | Implementation | Key Responsibility |
| :--- | :--- | :--- |
| **Ingress** | `Server_DAO` | Task polling with backoff and TLS payload decryption. |
| **Gating** | `OperationControl` | Multi-variable telemetry analysis (Thermal, SoC, RAM). |
| **Execution** | `Image_Trainer` | On-device gradient descent using optimized TFLite kernels. |
| **Persistence** | `CheckpointManager` | Fault-tolerant serialization of intermediate training states. |
| **Egress** | `ModelTransmitter` | Payload compression and encrypted delta synchronization. |

---

## Development & Build Pipeline

### Prerequisites
* **Environment**: Android Studio Jellyfish (2023.3.1+)
* **SDK**: Min API 24 / Target API 34
* **Physical Device**: Required for accurate telemetry feedback loops.

### Build Workflow
```bash
# 1. Clone the project
git clone https://github.com/Ahmad-Hassan-0/Fractal-Application.git

# 2. Sync Gradle dependencies
./gradlew build

# 3. Deploy to hardware
./gradlew installDebug
```

### CI/CD Status
Fractal utilizes GitHub Actions to ensure code quality:
- **Build Verification**: Automatic compilation check on all PRs.
- **Linting**: Kotlin style guide enforcement.
- **Artifact Generation**: Debug builds archived for rapid testing.

---

## Contributing

New contributors are welcomed to the Fractal ecosystem. Please review the [Contributing Guidelines](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) to maintain a collaborative and respectful environment.

- **Bug Reports**: Standardized templates are available in the issues tab.
- **Feature Requests**: Proposals for architectural improvements are encouraged.
- **Security**: Please refer to the [Security Policy](SECURITY.md) for vulnerability disclosure.

---

## Credits

### Lead Architect
**Ahmad Hassan (B-Ted)**
*Primary architecture, core engine development, and system design.*

### Community
Fractal is maintained and improved by its growing community of contributors.

---

<div align="center">
<i>Driven by Privacy. Powered by the Edge. Built for Humans.</i><br>
Licensed under [MIT](LICENSE).
</div>
