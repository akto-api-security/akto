#!/usr/bin/env python3
"""Pre-download all models to warm up the cache"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

from llm_guard.input_scanners import Toxicity, PromptInjection, Gibberish
from llm_guard.output_scanners import Bias, Relevance, NoRefusal, MaliciousURLs, Sensitive
from intent_analyzer import IntentAnalysisScanner

print("Warming up models...")

warmup_text = "This is a warmup message for model initialization"
gibberish_text = "ajsdkfj lkjasldkf qwerty zxcvbn"

print("1/9 Toxicity (input)...")
scanner = Toxicity(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("2/9 PromptInjection (input)...")
scanner = PromptInjection(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text)

print("3/9 Gibberish (input)...")
scanner = Gibberish(threshold=0.5, use_onnx=True)
scanner.scan(gibberish_text)

print("4/9 IntentAnalysis (input)...")
scanner = IntentAnalysisScanner(threshold=0.5, use_zero_shot=True, use_sentiment=True)
scanner.scan(warmup_text)

print("5/9 Bias (output)...")
scanner = Bias(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("6/9 Relevance (output)...")
scanner = Relevance(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("7/9 NoRefusal (output)...")
scanner = NoRefusal(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("8/9 MaliciousURLs (output)...")
scanner = MaliciousURLs(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("9/9 Sensitive (output)...")
scanner = Sensitive(threshold=0.5, use_onnx=True)
scanner.scan(warmup_text, warmup_text)

print("\n✓ All models cached successfully!")
print("First scan will now be fast (~500ms instead of 30s)")
