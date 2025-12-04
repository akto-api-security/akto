#!/usr/bin/env python3
"""Pre-download essential models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import Toxicity, PromptInjection
from llm_guard.output_scanners import Toxicity as OutputToxicity

print("Warming up models...")

warmup_text = "This is a warmup message for model initialization"

print("1/3 Toxicity (input)...")
scanner = Toxicity(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("2/3 PromptInjection (input)...")
scanner = PromptInjection(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("3/3 Toxicity (output)...")
scanner = OutputToxicity(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("\n✓ All models cached successfully!")
print("Demo version: PromptInjection + Toxicity (input & output)")
