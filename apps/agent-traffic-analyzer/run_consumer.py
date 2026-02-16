#!/usr/bin/env python3
"""
Standalone Kafka Consumer Service
Runs independently to consume events from Kafka and add embeddings to ChromaDB.
"""

import sys
import os

# Add src directory to path (works both from project root and Docker /app)
script_dir = os.path.dirname(os.path.abspath(__file__))
src_path = os.path.join(script_dir, 'src')
if os.path.exists(src_path):
    sys.path.insert(0, script_dir)
else:
    # Fallback: assume we're already in the right directory
    pass

from src.kafka.consumer import run

if __name__ == "__main__":
    run()

