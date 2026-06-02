# ML Inference Engine

> A real-time adaptive ML inference pipeline for Android — portfolio project targeting engineering roles at Microsoft, Apple, Amazon, and Tesla.

---

## Overview

MLInferenceEngine runs image classification on Android using two TensorFlow Lite models with an **adaptive router** that automatically selects between fast and accurate inference based on latency budgets — the same algorithmic pattern used in Netflix and YouTube's Adaptive Bitrate (ABR) streaming systems.

Built from scratch without using cloud ML APIs (no AWS Rekognition, no Google Vision). The inference pipeline, routing logic, and metrics system are all custom implementations.

---

## Why this project

Most ML portfolio projects call a cloud API and display the result. This project does something harder:

- Trains two models with different accuracy/speed tradeoffs
- Builds a server that measures latency in real time
- Implements an ABR-style router that adapts model selection based on observed performance
- Records every routing decision with its reason for analysis

This mirrors what Apple (Core ML), Amazon (SageMaker multi-model endpoints), and Tesla (Autopilot inference stack) actually build internally.

---

## Architecture

```
Android App
    ↓ image bytes (multipart)
Ktor Inference Server (port 8080)
    ↓
Adaptive Router
    ├── if avg_fast_latency < budget × 0.7 → ResNet50 (accurate)
    └── else → MobileNetV2 (fast)
    ↓
Python subprocess → TFLite runtime → prediction JSON
    ↓
MetricsStore (rolling 500 records)
    ↓
Live Dashboard (Android)
```

---

## Models

| Model | Accuracy | Avg Latency | Purpose |
|---|---|---|---|
| MobileNetV2 | **87.9%** | ~2.3s | Fast inference — default path |
| ResNet50 | 27.2% | ~2.5s | Accurate inference — upgraded path |

MobileNetV2 was fine-tuned on CIFAR-10 (96×96) with:
- Transfer learning from ImageNet weights
- Last 40 layers unfrozen for fine-tuning
- Offline data augmentation (horizontal flip + brightness)
- Two-phase training: head training → fine-tuning with ReduceLROnPlateau

---

## Adaptive Routing Algorithm

```kotlin
// From Routes.kt — the core ABR routing decision
val useAccurate = when {
    stats.totalRequests == 0 -> false           // no history, be safe
    stats.avgFastLatencyMs < latencyBudget * 0.7 -> true   // fast has headroom
    else -> false
}
```

Every routing decision is logged with:
- Which model was selected
- Why it was selected (the routing reason string)
- Latency of the inference
- Confidence of the prediction

This lets the dashboard show a full routing timeline — green = fast model, blue = accurate model.

---

## Tech stack

| Component | Technology |
|---|---|
| Android UI | Jetpack Compose + Material 3 |
| Networking | OkHttp |
| Image loading | Coil |
| Inference server | Ktor (Kotlin) |
| ML runtime | TensorFlow Lite (Python subprocess) |
| Model training | TensorFlow / Keras |
| Async | Kotlin Coroutines + StateFlow |

---

## Project phases

| Phase | Description | Status |
|---|---|---|
| 1 | Train MobileNetV2 + ResNet50 on CIFAR-10, export TFLite | ✅ Complete |
| 2 | Ktor inference server — `/infer/fast`, `/infer/accurate`, `/infer/adaptive`, `/metrics` | ✅ Complete |
| 3 | Android app — image picker, inference UI, confidence bars, all-scores breakdown | ✅ Complete |
| 4 | Live metrics dashboard — routing timeline, latency comparison, session stats | ✅ Complete |
| 5 | Network simulator — throttle bandwidth to demo adaptive routing switching | 🔄 In progress |
| 6 | Model drift detection using CUSUM algorithm | 📋 Planned |

---

## API endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/health` | GET | Server status |
| `/infer/fast` | POST | Always use MobileNetV2 |
| `/infer/accurate` | POST | Always use ResNet50 |
| `/infer/adaptive` | POST | ABR router decides |
| `/metrics` | GET | Session statistics |
| `/metrics/history` | GET | All inference records |

---

## Run locally

**Prerequisites:** JDK 17, Python 3.9+, Android Studio

```bash
# Clone both repos
git clone https://github.com/sujanuj/MLInferenceEngine
cd MLInferenceEngine

# Set up Python environment
python3 -m venv venv
source venv/bin/activate
pip install tensorflow numpy pillow

# Terminal 1 — start inference server
cd server && ./gradlew run

# Terminal 2 — verify
curl http://localhost:8080/health

# Terminal 3 — test inference directly
python3 models/inference/run_inference.py --image /path/to/image.jpg --model fast

# Android Studio — open android/ folder, press Run
```

---

## What I would change at scale

1. **Replace Python subprocess** with a persistent TFServing container — eliminates 1-2s cold start per inference
2. **Add request batching** — process multiple images per model invocation for throughput
3. **Implement model versioning** — A/B test new model weights without restarting the server
4. **Add causal latency tracking** — separate network latency from inference latency for better routing decisions
5. **CUSUM drift detection** — flag when confidence scores drop below historical baseline, triggering retraining

---

## What you say in the interview

*"I built a system that runs two ML models and routes inference requests based on latency budgets — the same pattern Netflix uses for adaptive bitrate streaming. The fast model handles most requests; if its latency stays below 70% of the budget, the router upgrades to the more accurate model. Every routing decision is logged with its reason, so you can see in the dashboard exactly why each model was chosen. The interesting engineering isn't the classifier — it's the routing layer and the observability system around it."*

---

## Author

**Sujan Uppalli Jayadevappa**
MS Software Engineering — Arizona State University

- Android app: [github.com/sujanuj/MLInferenceEngine](https://github.com/sujanuj/MLInferenceEngine)
