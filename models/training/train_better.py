import os
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.datasets import cifar10
from tensorflow.keras.utils import to_categorical

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "output")
CLASS_NAMES = ["airplane","automobile","bird","cat","deer",
               "dog","frog","horse","ship","truck"]
NUM_CLASSES = 10

print("[1/4] Loading and preparing data...")
(x_train, y_train), (x_test, y_test) = cifar10.load_data()
x_train = x_train.astype("float32") / 255.0
x_test  = x_test.astype("float32")  / 255.0
y_train_cat = to_categorical(y_train, NUM_CLASSES)
y_test_cat  = to_categorical(y_test,  NUM_CLASSES)
x_train_96 = tf.image.resize(x_train, [96, 96]).numpy()
x_test_96  = tf.image.resize(x_test,  [96, 96]).numpy()

# Augment training data offline (no Lambda layers — TFLite friendly)
print("  Augmenting training data...")
x_aug = np.concatenate([
    x_train_96,
    np.flip(x_train_96, axis=2),                          # horizontal flip
    np.clip(x_train_96 * 1.2, 0, 1),                     # brighter
    np.clip(x_train_96 * 0.8, 0, 1),                     # darker
])
y_aug = np.concatenate([y_train_cat, y_train_cat, y_train_cat, y_train_cat])
print(f"  Training samples: {len(x_aug)}")

print("[2/4] Building model...")
base = MobileNetV2(input_shape=(96, 96, 3), include_top=False, weights="imagenet")
for layer in base.layers[:-40]:
    layer.trainable = False
for layer in base.layers[-40:]:
    layer.trainable = True

model = models.Sequential([
    base,
    layers.GlobalAveragePooling2D(),
    layers.Dropout(0.3),
    layers.Dense(256, activation="relu"),
    layers.Dropout(0.2),
    layers.Dense(NUM_CLASSES, activation="softmax")
])

print("[3/4] Training (15 epochs)...")
model.compile(
    optimizer=tf.keras.optimizers.legacy.Adam(1e-4),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)
callbacks = [
    tf.keras.callbacks.EarlyStopping(patience=4, restore_best_weights=True),
    tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=2, min_lr=1e-6)
]
model.fit(x_aug, y_aug, epochs=15, batch_size=64,
          validation_data=(x_test_96, y_test_cat),
          verbose=1, callbacks=callbacks)

loss, acc = model.evaluate(x_test_96, y_test_cat, verbose=0)
print(f"\n  Test accuracy: {acc*100:.1f}%")

print("[4/4] Exporting TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()
path = os.path.join(OUTPUT_DIR, "fast_model.tflite")
with open(path, "wb") as f:
    f.write(tflite_model)
print(f"  Saved fast_model.tflite ({os.path.getsize(path)/1024:.1f} KB)")
with open(os.path.join(OUTPUT_DIR, "labels.txt"), "w") as f:
    f.write("\n".join(CLASS_NAMES))

print(f"\n{'='*50}")
print(f"  DONE — Accuracy: {acc*100:.1f}%")
print(f"{'='*50}")
