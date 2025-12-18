#!/usr/bin/env python3
"""Pre-download Gibberish detection model to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import Gibberish

print("Warming up Gibberish detection model...")

# Sample texts for model initialization
normal_text = "This is a normal sentence for model initialization"
gibberish_text = "ajsdkfj lkjasldkf qwerty zxcvbn nonsense text"

print("Initializing Gibberish scanner with ONNX optimization...")
scanner = Gibberish(threshold=0.7, use_onnx=True, match_type="full")

print("Testing with normal text...")
scanner.scan(normal_text)

print("Testing with gibberish text...")  
scanner.scan(gibberish_text)

print("\n✓ Gibberish detection model cached successfully!")
print("First scan will now be fast (~50-100ms instead of 5s)")