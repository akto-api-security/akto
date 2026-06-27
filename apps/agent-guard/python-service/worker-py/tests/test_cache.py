"""Semantic cache — mode resolution, key hashing, classify/serve logic, fail-open.

No Redis or embedder is touched: cache_store is monkeypatched and REDIS_URL /
EMBEDDER_URL are left unset to exercise the fail-open paths.
"""

import time

import cache
import cache_store


# --------------------------------------------------------------------------- #
# config_hash
# --------------------------------------------------------------------------- #
def test_config_hash_is_stable_and_16_hex():
    h1 = cache.config_hash("Toxicity", "prompt", {"a": 1, "b": 2})
    h2 = cache.config_hash("Toxicity", "prompt", {"b": 2, "a": 1})  # key order irrelevant
    assert h1 == h2
    assert len(h1) == 16
    int(h1, 16)  # valid hex


def test_config_hash_ignores_store_all_results():
    base = cache.config_hash("Toxicity", "prompt", {"x": 1})
    with_noise = cache.config_hash("Toxicity", "prompt", {"x": 1, "storeAllResults": True})
    assert base == with_noise


def test_config_hash_sensitive_to_scanner_type_and_config():
    a = cache.config_hash("Toxicity", "prompt", {"x": 1})
    assert a != cache.config_hash("BanTopics", "prompt", {"x": 1})
    assert a != cache.config_hash("Toxicity", "response", {"x": 1})
    assert a != cache.config_hash("Toxicity", "prompt", {"x": 2})


# --------------------------------------------------------------------------- #
# mode / enabled / serving
# --------------------------------------------------------------------------- #
def test_mode_defaults_to_observe(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_MODE", "")
    monkeypatch.setattr(cache.settings, "CACHE_SHADOW_ENABLED", "")
    assert cache.mode() == "observe"
    assert cache.enabled() is True
    assert cache.serving() is False


def test_mode_explicit_wins(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_SHADOW_ENABLED", "true")
    for raw, want in (("off", "off"), ("observe", "observe"), ("decide", "decide"),
                      ("DECIDE", "decide")):
        monkeypatch.setattr(cache.settings, "CACHE_MODE", raw)
        assert cache.mode() == want
    monkeypatch.setattr(cache.settings, "CACHE_MODE", "decide")
    assert cache.serving() is True


def test_mode_alias_back_compat(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_MODE", "")
    monkeypatch.setattr(cache.settings, "CACHE_SHADOW_ENABLED", "true")
    assert cache.mode() == "observe"
    monkeypatch.setattr(cache.settings, "CACHE_SHADOW_ENABLED", "false")
    assert cache.mode() == "off"
    assert cache.enabled() is False


# --------------------------------------------------------------------------- #
# threshold / ttl parsing
# --------------------------------------------------------------------------- #
def test_threshold_and_ttl_defaults_and_bad_values(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "")
    monkeypatch.setattr(cache.settings, "CACHE_TTL_SECONDS", "")
    assert cache._threshold() == cache._DEFAULT_DISTANCE_THRESHOLD
    assert cache._ttl_seconds() == cache._DEFAULT_TTL_SECONDS

    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "0.3")
    monkeypatch.setattr(cache.settings, "CACHE_TTL_SECONDS", "60")
    assert cache._threshold() == 0.3
    assert cache._ttl_seconds() == 60

    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "oops")
    assert cache._threshold() == cache._DEFAULT_DISTANCE_THRESHOLD


# --------------------------------------------------------------------------- #
# _classify
# --------------------------------------------------------------------------- #
def _cached(is_valid=True, distance=0.05, age=0.0):
    return {"is_valid": is_valid, "risk_score": 0.0, "reason": "",
            "distance": distance, "inserted_at": time.time() - age}


def test_classify_miss_when_no_neighbour():
    assert cache._classify(None, 0.15, 6000, time.time(), True) == "miss"


def test_classify_miss_over_threshold():
    assert cache._classify(_cached(distance=0.9), 0.15, 6000, time.time(), True) == "miss"


def test_classify_miss_when_expired():
    assert cache._classify(_cached(age=10000), 0.15, 6000, time.time(), True) == "miss"


def test_classify_hit_match_and_mismatch():
    now = time.time()
    assert cache._classify(_cached(is_valid=True), 0.15, 6000, now, True) == "hit_match"
    assert cache._classify(_cached(is_valid=True), 0.15, 6000, now, False) == "hit_mismatch"


# --------------------------------------------------------------------------- #
# try_serve — only fresh, within-threshold, safe hits short-circuit
# --------------------------------------------------------------------------- #
def _prep(cached):
    return {"scanner_key": "abc123", "vec": [0.1] * 384, "cached": cached}


def test_try_serve_serves_safe_hit(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "0.15")
    monkeypatch.setattr(cache.settings, "CACHE_TTL_SECONDS", "6000")
    cached = _cached(is_valid=True, distance=0.05, age=1)
    cached["risk_score"] = 0.12
    cached["reason"] = "looks fine"
    served = cache.try_serve(_prep(cached), "Toxicity", "prompt", "hi")
    assert served is not None
    assert served["risk_score"] == 0.12
    assert served["details"]["cache"] == "served"
    assert served["alert"]["outcome"] == "served"


def test_try_serve_never_serves_a_block(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "0.15")
    monkeypatch.setattr(cache.settings, "CACHE_TTL_SECONDS", "6000")
    assert cache.try_serve(_prep(_cached(is_valid=False, distance=0.01)),
                           "Toxicity", "prompt", "hi") is None


def test_try_serve_falls_through_on_near_miss_expired_and_empty(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_DISTANCE_THRESHOLD", "0.15")
    monkeypatch.setattr(cache.settings, "CACHE_TTL_SECONDS", "6000")
    assert cache.try_serve(_prep(None), "Toxicity", "prompt", "hi") is None
    assert cache.try_serve(_prep(_cached(distance=0.9)), "Toxicity", "prompt", "hi") is None
    assert cache.try_serve(_prep(_cached(age=10000)), "Toxicity", "prompt", "hi") is None


# --------------------------------------------------------------------------- #
# fail-open: no embedder / no redis
# --------------------------------------------------------------------------- #
async def test_embed_noop_when_url_unset(monkeypatch):
    monkeypatch.setattr(cache.settings, "EMBEDDER_URL", "")
    assert await cache._embed("hi") is None


async def test_prepare_fail_open_without_embedder(monkeypatch):
    monkeypatch.setattr(cache.settings, "EMBEDDER_URL", "")
    prep = await cache.prepare("Toxicity", "prompt", "hi", {})
    assert prep["vec"] is None
    assert prep["cached"] is None
    assert prep["scanner_key"]


async def test_observe_alerts_embed_error_and_skips_store(monkeypatch):
    monkeypatch.setattr(cache.settings, "CACHE_MODE", "observe")
    posted = {}

    async def _fake_post(info):
        posted.update(info)

    async def _boom_upsert(*a, **k):
        raise AssertionError("upsert must not run when embed fails")

    monkeypatch.setattr(cache.alerts, "post_cache_shadow", _fake_post)
    monkeypatch.setattr(cache.cache_store, "upsert", _boom_upsert)
    monkeypatch.setattr(cache, "_embed", lambda text: _async_none())

    await cache.observe("Toxicity", "prompt", "hi", {}, {"is_valid": True, "details": {}})
    assert posted.get("outcome") == "error"
    assert posted.get("error") == "embed_unavailable"


async def _async_none():
    return None


async def test_cache_store_query_noop_without_redis(monkeypatch):
    monkeypatch.setattr(cache_store.settings, "REDIS_URL", "")
    # reset the lazy client memo so the unset URL is re-read
    cache_store._client = None
    cache_store._client_init = False
    assert await cache_store.query([0.1] * 384, "abc") is None
