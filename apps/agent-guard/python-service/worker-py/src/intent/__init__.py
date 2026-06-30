"""Intent prefilter: JSON-strip → chunk → (embed + classify) → decide.

Layered on top of the existing per-scanner semantic cache (see cache.py). Pure
Python on the worker side so it runs under Pyodide and in the FastAPI container;
the embedding + per-agent classifier live in the embedder container.
"""
