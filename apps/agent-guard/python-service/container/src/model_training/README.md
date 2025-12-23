# Model Training Pipeline

This module fine-tunes ONNX models using client feedback from MongoDB.

## Overview

The training pipeline:
1. **Fetches training data** from MongoDB (events with `status: "TRAINING"`)
2. **Fine-tunes** the base model (e.g., `protectai/deberta-v3-base-prompt-injection-v2`)
3. **Converts to ONNX** format for fast inference
4. **Validates** the model with test examples

## Usage

### Basic Training

```bash
python train_fine_tuned_model.py \
  --mongo-uri "mongodb://localhost:27017" \
  --account-id "1669322524" \
  --category "PROMPT_INJECTION" \
  --epochs 3 \
  --output-dir "./models/prompt_injection_v3"
```

### Advanced Options

```bash
python train_fine_tuned_model.py \
  --model-name "protectai/deberta-v3-base-prompt-injection-v2" \
  --mongo-uri "mongodb://user:pass@host:27017" \
  --account-id "1669322524" \
  --category "PROMPT_INJECTION" \
  --epochs 5 \
  --learning-rate 1e-5 \
  --batch-size 16 \
  --output-dir "./models/custom" \
  --no-quantization
```

## Training Data Format

The script expects MongoDB events with:
- `status: "TRAINING"`
- `label: "GUARDRAIL"`
- `latestApiOrig`: The text payload to train on
- `subCategory`: Category filter (optional)

## Output Structure

```
fine_tuned_models/
├── checkpoints/          # Training checkpoints
├── logs/                 # Training logs
├── pytorch_model/        # Fine-tuned PyTorch model
└── onnx_model_quantized/ # Final ONNX model (ready for deployment)
```

## Integration

After training, update the agent guard service to use the new model:

1. Copy the ONNX model to the service's model directory
2. Update model path in scanner configuration
3. Restart the service

## Requirements

- Minimum 10 training examples
- GPU recommended for training (CPU works but slower)
- Sufficient disk space (~2GB per model)

