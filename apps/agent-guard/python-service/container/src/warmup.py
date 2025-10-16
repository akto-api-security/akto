#!/usr/bin/env python3
"""Pre-download all ONNX models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import Toxicity, PromptInjection
from llm_guard.output_scanners import Bias, Relevance, NoRefusal, MaliciousURLs, Sensitive

print("Warming up ONNX models...")

warmup_text = "This is a warmup message for model initialization"

print("1/7 Toxicity (input)...")
scanner = Toxicity(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("2/7 PromptInjection (input)...")
scanner = PromptInjection(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("3/7 Bias (output)...")
scanner = Bias(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("4/7 Relevance (output)...")
scanner = Relevance(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("5/7 NoRefusal (output)...")
scanner = NoRefusal(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("6/7 MaliciousURLs (output)...")
scanner = MaliciousURLs(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("7/7 Sensitive (output)...")
scanner = Sensitive(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("\n✓ All ONNX models cached successfully!")
print("First scan will now be fast (~500ms instead of 30s)")

