# ML Inference Engine

> A real-time adaptive ML inference pipeline for Android — portfolio project targeting engineering roles at Microsoft, Apple, Amazon, and Tesla.

---

## Overview

MLInferenceEngine runs image classification on Android using two TensorFlow Lite models with an **adaptive router** that automatically selects between fast and accurate inference based on latency budgets — the same algorithmic pattern used in Netflix and YouTube's Adaptive Bitrate (ABR) streaming systems.

Built from scratch without using cloud ML APIs (no AWS Rekognition, no Google Vision). The inference pipeline, routing logic, network simulator, and metrics system are all custom implementations.

---

## Why this project

Most ML portfolio projects call a cloud API and display the result. This project does something harder:

- Trains two models with different accuracy/speed tradeoffs
- Builds a server that measures latency in real time
- Implements an ABR-style router that adapts model selection based on observed performance
- Simulates bad network conditions to demonstrate routing adaptation live
- Records every routing decision with its reason for full observability

This mirrors what Apple (Core ML), Amazon (SageMaker multi-model endpoints), and Tesla (Autopilot inference stack) actually build internally.

---

## Architecture

```
Android App
    ↓ image bytes (multipart)
Ktor Inference Server (port 8080)
    ↓
Network Simulator (optional delay injection)
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
- Early stopping + ReduceLROnPlateau callbacks

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

## Network Simulator

The network simulator injects artificial delay into inference responses, allowing live demonstration of how the adaptive router responds to degraded network conditions.

```
Good     → 0ms extra delay    → router stays on fast model
Poor     → +500ms extra delay → latency climbs, router may switch
Terrible → +2000ms extra delay → latency exceeds budget, router adapts
```

Controlled via the Android UI (Good/Poor/Terrible chips) or directly via API:

```bash
# Set network quality
curl -X POST http://localhost:8080/simulate/network/TERRIBLE

# Check current quality
curl http://localhost:8080/simulate/network
```

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
| 5 | Network simulator — Good/Poor/Terrible toggles with live adaptive routing response | ✅ Complete |
| 6 | Model drift detection using CUSUM algorithm | 📋 Planned |

---

## API endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/health` | GET | Server status + current network quality |
| `/infer/fast` | POST | Always use MobileNetV2 |
| `/infer/accurate` | POST | Always use ResNet50 |
| `/infer/adaptive` | POST | ABR router decides based on latency budget |
| `/metrics` | GET | Session statistics |
| `/metrics/history` | GET | Full inference record history |
| `/simulate/network/{quality}` | POST | Set network quality: GOOD / POOR / TERRIBLE |
| `/simulate/network` | GET | Get current network simulation status |

---

## Run locally

**Prerequisites:** JDK 17, Python 3.9+, Android Studio

```bash
# Clone repo
git clone https://github.com/sujanuj/MLInferenceEngine
cd MLInferenceEngine

# Set up Python environment
python3 -m venv venv
source venv/bin/activate
pip install tensorflow numpy pillow

# Terminal 1 — start inference server
cd server && ./gradlew run

# Terminal 2 — verify server is up
curl http://localhost:8080/health

# Terminal 3 — test inference directly
python3 models/inference/run_inference.py --image /path/to/image.jpg --model fast

# Terminal 3 — simulate bad network
curl -X POST http://localhost:8080/simulate/network/TERRIBLE

# Android Studio — open android/ folder, press Run
# Use the Network Simulator card to switch between Good/Poor/Terrible
```

---

## Demo walkthrough

1. Start server → open Android app → Server Online shows green
2. Select an image → tap **Run Inference** → see prediction with confidence bars
3. Tap **Terrible** in the Network Simulator → latency climbs to ~4s+
4. Run several inferences → watch the metrics dashboard routing timeline fill with F blocks
5. Tap **Good** → latency drops back → router stays on fast model
6. Tap the chart icon → **Live Metrics Dashboard** shows full routing history

---

## What I would change at scale

1. **Replace Python subprocess** with a persistent TFServing container — eliminates 1-2s cold start per inference
2. **Add request batching** — process multiple images per model invocation for throughput
3. **Implement model versioning** — A/B test new model weights without restarting the server
4. **Add causal latency tracking** — separate network latency from inference latency for better routing decisions
5. **CUSUM drift detection** — flag when confidence scores drop below historical baseline, triggering retraining

---

## What you say in the interview

*"I built a system that runs two ML models and routes inference requests based on latency budgets — the same pattern Netflix uses for adaptive bitrate streaming. The fast model handles most requests; if its latency stays below 70% of the budget, the router upgrades to the more accurate model. I also built a network simulator that injects artificial delay so you can watch the routing adapt in real time — Good network stays on the fast model, Terrible network causes latency to spike and the dashboard shows every routing decision with its reason. The interesting engineering isn't the classifier — it's the routing layer and the observability system around it."*

---

## Author

**Sujan Uppalli Jayadevappa**
MS Software Engineering — Arizona State University

- Android app: [github.com/sujanuj/MLInferenceEngine](https://github.com/sujanuj/MLInferenceEngine)
