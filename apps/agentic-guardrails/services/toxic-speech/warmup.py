#!/usr/bin/env python3
"""Pre-download Toxicity and Bias models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import Toxicity
from llm_guard.output_scanners import Bias

print("Warming up Toxic Speech Detection models...")
warmup_text = "This is a warmup message for model initialization"

print("1/2 Loading Toxicity scanner...")
scanner = Toxicity(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("2/2 Loading Bias scanner...")
scanner = Bias(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("\n✓ All Toxic Speech models cached successfully!")
print("First scan will now be fast (~60-120ms instead of 30s)")
