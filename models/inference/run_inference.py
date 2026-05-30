import argparse
import json
import sys
import os
import warnings
warnings.filterwarnings("ignore")

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["PYTHONWARNINGS"] = "ignore"

import numpy as np

import tensorflow as tf
tf.get_logger().setLevel("ERROR")

from PIL import Image

SCRIPT_DIR     = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR     = os.path.join(SCRIPT_DIR, "..", "output")
FAST_MODEL     = os.path.join(MODELS_DIR, "fast_model.tflite")
ACCURATE_MODEL = os.path.join(MODELS_DIR, "accurate_model.tflite")
LABELS_FILE    = os.path.join(MODELS_DIR, "labels.txt")

with open(LABELS_FILE) as f:
    LABELS = [line.strip() for line in f.readlines()]

def load_and_preprocess(image_path: str) -> np.ndarray:
    img = Image.open(image_path).convert("RGB")
    img = img.resize((96, 96))
    arr = np.array(img, dtype=np.float32) / 255.0
    return np.expand_dims(arr, axis=0)

def run_tflite(model_path: str, input_data: np.ndarray) -> np.ndarray:
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    input_details  = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    interpreter.set_tensor(input_details[0]["index"], input_data)
    interpreter.invoke()
    return interpreter.get_tensor(output_details[0]["index"])[0]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--model", required=True, choices=["fast", "accurate"])
    args = parser.parse_args()

    model_path = FAST_MODEL if args.model == "fast" else ACCURATE_MODEL

    if not os.path.exists(model_path):
        print(json.dumps({"error": f"Model not found: {model_path}"}))
        sys.exit(1)

    if not os.path.exists(args.image):
        print(json.dumps({"error": f"Image not found: {args.image}"}))
        sys.exit(1)

    input_data = load_and_preprocess(args.image)
    scores     = run_tflite(model_path, input_data)

    predicted_idx   = int(np.argmax(scores))
    predicted_class = LABELS[predicted_idx]
    confidence      = float(scores[predicted_idx])
    all_scores      = {LABELS[i]: float(scores[i]) for i in range(len(LABELS))}

    result = {
        "predicted_class": predicted_class,
        "confidence":      round(confidence, 4),
        "all_scores":      {k: round(v, 4) for k, v in all_scores.items()}
    }
    # Print ONLY the JSON — nothing else on stdout
    print(json.dumps(result), flush=True)

if __name__ == "__main__":
    main()
