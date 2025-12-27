#!/usr/bin/env python3
"""Pre-download Output Quality & Safety models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.output_scanners import Relevance, NoRefusal, MaliciousURLs, Sensitive

print("Warming up Output Quality & Safety models...")
warmup_text = "This is a warmup message for model initialization"

print("1/4 Loading Relevance scanner...")
scanner = Relevance(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("2/4 Loading NoRefusal scanner...")
scanner = NoRefusal(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("3/4 Loading MaliciousURLs scanner...")
scanner = MaliciousURLs(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("4/4 Loading Sensitive scanner...")
scanner = Sensitive(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("\n✓ All Output Quality models cached successfully!")
print("First scan will now be fast (~60-120ms instead of 30s)")
