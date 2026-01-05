#!/usr/bin/env python3
"""Pre-download PromptInjection model to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import PromptInjection

print("Warming up Prompt Injection model...")
warmup_text = "This is a warmup message for model initialization"

print("Loading PromptInjection scanner...")
scanner = PromptInjection(threshold=0.75, use_onnx=True)
scanner.scan(warmup_text)

print("\n✓ PromptInjection model cached successfully!")
print("First scan will now be fast (~60-120ms instead of 30s)")
