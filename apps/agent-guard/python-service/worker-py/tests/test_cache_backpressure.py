"""cache_backpressure — the CACHE (embedder) breaker, and its independence from
the MODEL (cascade) breaker.

The CACHE breaker trips on sustained high *embed* latency and, when tripped, lets
the caller skip the fuzzy embed+KNN lookup. It shares the self-healing,
time-bounded window logic with the MODEL breaker but is configured separately
(AGW_CACHE_BACKPRESSURE_* vs AGW_CASCADE_BACKPRESSURE_*) so embedder degradation
never disables the cascade and Vertex degradation never disables the cache.
"""

import cascade_backpressure as bp


def _set_clock(monkeypatch, holder):
    monkeypatch.setattr(bp, "_now", lambda: holder[0])


def _configure(monkeypatch, **env):
    for k, v in env.items():
        monkeypatch.setenv(k, str(v))
    bp.configure_from_env()


def test_default_thresholds_differ():
    # The two breakers must not share a threshold: cache is far tighter (embed is
    # ~20ms healthy) than the model cascade (seconds).
    bp.reset_for_tests()
    assert bp.CACHE.get_config().threshold_ms == 500.0
    assert bp.MODEL.get_config().threshold_ms == 8000.0


def test_cache_trips_on_sustained_high_embed_latency(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500,
               AGW_CACHE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.CACHE.record(2000)            # saturated embed avg 2000 >= 500
    assert bp.CACHE.should_skip() is True


def test_cache_does_not_trip_on_healthy_embed(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500)
    for _ in range(20):
        bp.CACHE.record(180)             # worst healthy burst avg ~180 < 500
    assert bp.CACHE.should_skip() is False


def test_cache_self_heals_after_ttl(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500,
               AGW_CACHE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.CACHE.record(2000)
    assert bp.CACHE.should_skip() is True
    t[0] += 31                           # skipping → no new embeds recorded
    assert bp.CACHE.should_skip() is False
    assert bp.CACHE.recent_sample_count() == 0


def test_cache_disabled_never_skips(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CACHE_BACKPRESSURE_ENABLED="false",
               AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500)
    for _ in range(10):
        bp.CACHE.record(2000)
    assert bp.CACHE.should_skip() is False


def test_breakers_are_independent(monkeypatch):
    # A saturated embedder trips CACHE but NOT the cascade breaker, and vice versa.
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch,
               AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500,
               AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=8000)

    for _ in range(6):                   # embedder slow, Vertex silent
        bp.CACHE.record(2000)
    assert bp.CACHE.should_skip() is True
    assert bp.should_skip_cascade() is False   # MODEL untouched

    bp.reset_for_tests()
    _configure(monkeypatch,
               AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500,
               AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=8000)
    for _ in range(6):                   # Vertex slow, embedder silent
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is True
    assert bp.CACHE.should_skip() is False     # CACHE untouched


def test_cache_skip_details_labelled_distinctly(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CACHE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CACHE_BACKPRESSURE_AVG_LATENCY_MS=500)
    for _ in range(6):
        bp.CACHE.record(2000)
    assert bp.CACHE.skip_details()["reason"] == "cache_backpressure"
    assert bp.MODEL.skip_details()["reason"] == "cascade_backpressure"
