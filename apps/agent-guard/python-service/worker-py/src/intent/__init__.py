"""Intent prefilter: segment (instruction vs. data) → split into units →
batch-embed → batch-classify (per-agent multi-class model) → decide.

Layered on top of the existing per-scanner semantic cache (see cache.py),
which still keys off the whole-prompt canonical text independently of this
package. Pure Python on the worker side so it runs under Pyodide and in the
FastAPI container; the embedding + per-agent classifier live in the embedder
container.
"""
