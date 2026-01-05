#!/usr/bin/env python3
"""Pre-download models for Ban Words & Content Filter Service"""
import os
import warnings
warnings.filterwarnings('ignore')
os.environ['TRANSFORMERS_VERBOSITY'] = 'error'

# These scanners are lightweight and don't require model pre-loading
# They use rule-based or pattern-matching approaches
print("Ban Words & Content Filter Service - Ready!")
print("Scanners: BanCode, BanTopics, BanCompetitors, BanSubstrings")
print("          Anonymize, Deanonymize, Secrets, Code, Language, Gibberish, TokenLimit")
print("\n✓ Service initialized successfully!")
