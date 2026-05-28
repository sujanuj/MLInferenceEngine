"""
MLInferenceEngine — Phase 1
Trains two models on CIFAR-10 and exports them as TensorFlow Lite:
  - MobileNetV2  (fast, ~50ms, lower accuracy)  → fast_model.tflite
  - ResNet50     (slow, ~300ms, higher accuracy) → accurate_model.tflite

Run:
    source venv/bin/activate
    python3 models/training/train_models.py
"""

import os
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2, ResNet50
from tensorflow.keras.datasets import cifar10
from tensorflow.keras.utils import to_categorical

# ── Output directory ────────────────────────────────────────────────────────
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── CIFAR-10 labels ──────────────────────────────────────────────────────────
CLASS_NAMES = ["airplane","automobile","bird","cat","deer",
               "dog","frog","horse","ship","truck"]
NUM_CLASSES = 10

# ────────────────────────────────────────────────────────────────────────────
# 1. Load and preprocess data
# ────────────────────────────────────────────────────────────────────────────
print("\n[1/6] Loading CIFAR-10...")
(x_train, y_train), (x_test, y_test) = cifar10.load_data()

# Normalise to [0, 1]
x_train = x_train.astype("float32") / 255.0
x_test  = x_test.astype("float32")  / 255.0

# One-hot encode labels
y_train_cat = to_categorical(y_train, NUM_CLASSES)
y_test_cat  = to_categorical(y_test,  NUM_CLASSES)

# Resize to 96×96 so MobileNetV2 and ResNet50 are happy
# (both need at least 32×32; 96 gives better accuracy without huge training time)
print("[1/6] Resizing images to 96×96...")
x_train_96 = tf.image.resize(x_train, [96, 96]).numpy()
x_test_96  = tf.image.resize(x_test,  [96, 96]).numpy()

print(f"      Train: {x_train_96.shape}  Test: {x_test_96.shape}")

# ────────────────────────────────────────────────────────────────────────────
# 2. Build MobileNetV2 (fast model)
# ────────────────────────────────────────────────────────────────────────────
print("\n[2/6] Building MobileNetV2 (fast model)...")

base_mobile = MobileNetV2(
    input_shape=(96, 96, 3),
    include_top=False,
    weights="imagenet"
)
base_mobile.trainable = False   # freeze backbone, only train head

mobile_model = models.Sequential([
    base_mobile,
    layers.GlobalAveragePooling2D(),
    layers.Dropout(0.2),
    layers.Dense(NUM_CLASSES, activation="softmax")
], name="MobileNetV2_CIFAR10")

mobile_model.compile(
    optimizer="adam",
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)
mobile_model.summary()

# ────────────────────────────────────────────────────────────────────────────
# 3. Train MobileNetV2
# ────────────────────────────────────────────────────────────────────────────
print("\n[3/6] Training MobileNetV2 (5 epochs — fast)...")

mobile_history = mobile_model.fit(
    x_train_96, y_train_cat,
    epochs=5,
    batch_size=64,
    validation_split=0.1,
    verbose=1
)

mobile_loss, mobile_acc = mobile_model.evaluate(x_test_96, y_test_cat, verbose=0)
print(f"\n  MobileNetV2 → Test accuracy: {mobile_acc:.4f}  Loss: {mobile_loss:.4f}")

# ────────────────────────────────────────────────────────────────────────────
# 4. Build ResNet50 (accurate model)
# ────────────────────────────────────────────────────────────────────────────
print("\n[4/6] Building ResNet50 (accurate model)...")

base_resnet = ResNet50(
    input_shape=(96, 96, 3),
    include_top=False,
    weights="imagenet"
)
base_resnet.trainable = False

resnet_model = models.Sequential([
    base_resnet,
    layers.GlobalAveragePooling2D(),
    layers.Dropout(0.3),
    layers.Dense(256, activation="relu"),
    layers.Dropout(0.3),
    layers.Dense(NUM_CLASSES, activation="softmax")
], name="ResNet50_CIFAR10")

resnet_model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-4),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)
resnet_model.summary()

# ────────────────────────────────────────────────────────────────────────────
# 5. Train ResNet50
# ────────────────────────────────────────────────────────────────────────────
print("\n[5/6] Training ResNet50 (5 epochs)...")

resnet_history = resnet_model.fit(
    x_train_96, y_train_cat,
    epochs=5,
    batch_size=32,
    validation_split=0.1,
    verbose=1
)

resnet_loss, resnet_acc = resnet_model.evaluate(x_test_96, y_test_cat, verbose=0)
print(f"\n  ResNet50 → Test accuracy: {resnet_acc:.4f}  Loss: {resnet_loss:.4f}")

# ────────────────────────────────────────────────────────────────────────────
# 6. Export both models as TensorFlow Lite
# ────────────────────────────────────────────────────────────────────────────
print("\n[6/6] Converting to TensorFlow Lite...")

def export_tflite(keras_model, filename):
    converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # quantize weights
    tflite_model = converter.convert()
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, "wb") as f:
        f.write(tflite_model)
    size_kb = os.path.getsize(path) / 1024
    print(f"  Saved {filename}  ({size_kb:.1f} KB)")
    return path

export_tflite(mobile_model, "fast_model.tflite")
export_tflite(resnet_model, "accurate_model.tflite")

# ── Save label list ──────────────────────────────────────────────────────────
labels_path = os.path.join(OUTPUT_DIR, "labels.txt")
with open(labels_path, "w") as f:
    f.write("\n".join(CLASS_NAMES))
print(f"  Saved labels.txt")

# ── Summary ──────────────────────────────────────────────────────────────────
print("\n" + "="*55)
print("  TRAINING COMPLETE")
print("="*55)
print(f"  MobileNetV2 accuracy : {mobile_acc*100:.1f}%  (fast model)")
print(f"  ResNet50    accuracy : {resnet_acc*100:.1f}%  (accurate model)")
print(f"\n  Output files in: {os.path.abspath(OUTPUT_DIR)}")
print("    fast_model.tflite")
print("    accurate_model.tflite")
print("    labels.txt")
print("="*55)
