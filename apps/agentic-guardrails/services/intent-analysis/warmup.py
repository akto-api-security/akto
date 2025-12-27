#!/usr/bin/env python3
"""Pre-download IntentAnalysis models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from intent_analyzer import IntentAnalysisScanner

print("Warming up Intent & Semantic Analysis models...")
warmup_text = "This is a warmup message for model initialization"

print("Loading IntentAnalysis scanner (dual-model system)...")
print("  - Sentiment model: TangoBeeAkto/distilbert-base-uncased-finetuned-sst-2-english")
print("  - Zero-shot model: TangoBeeAkto/deberta-v3-base-zeroshot-v1.1-all-33")

scanner = IntentAnalysisScanner(threshold=0.5, use_zero_shot=True, use_sentiment=True)
scanner.scan(warmup_text)

print("\n✓ All Intent Analysis models cached successfully!")
print("First scan will now be fast (~50-100ms for zero-shot, <10ms for fast-path)")
