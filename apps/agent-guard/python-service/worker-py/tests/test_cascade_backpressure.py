"""cascade_backpressure — trip on sustained high latency, and SELF-HEAL.

The core regression: a count-only window latches ON forever, because once it
skips, no cascade runs, so no fresh latency is ever recorded to push the stale
high samples out. These tests drive a controllable clock to prove the
time-bounded window expires stale samples and recovers without a restart.
"""

import cascade_backpressure as bp


def _set_clock(monkeypatch, holder):
    monkeypatch.setattr(bp, "_now", lambda: holder[0])


def _configure(monkeypatch, **env):
    for k, v in env.items():
        monkeypatch.setenv(k, str(v))
    bp.configure_from_env()


def test_does_not_skip_below_min_samples(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(4):                       # only 4 high samples (< min 5)
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is False


def test_trips_on_sustained_high_latency(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.record_cascade_latency(9000)      # avg 9000 >= 1000
    assert bp.should_skip_cascade() is True


def test_self_heals_after_ttl_without_new_samples(monkeypatch):
    # THE regression test: trip it, then advance the clock past the TTL with NO
    # new cascades (simulating the skip path) — it must recover on its own.
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is True   # latched on
    t[0] += 31                                # 31s pass, still skipping -> no new samples
    assert bp.should_skip_cascade() is False  # stale samples expired -> recovered
    assert bp.recent_sample_count() == 0


def test_reopens_when_probe_latency_drops(monkeypatch):
    # After recovery, fresh fast probes keep it open (Vertex healthy again).
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is True
    t[0] += 31
    for _ in range(6):                        # healthy probes
        bp.record_cascade_latency(120)
    assert bp.should_skip_cascade() is False  # avg 120 < 1000 -> stays open


def test_retrips_if_still_slow_after_probe(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=30)
    for _ in range(6):
        bp.record_cascade_latency(9000)
    t[0] += 31
    assert bp.should_skip_cascade() is False  # window expired
    for _ in range(5):                        # probes show Vertex still slow
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is True    # re-trips


def test_disabled_never_skips(monkeypatch):
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_ENABLED="false",
               AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=5,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000)
    for _ in range(10):
        bp.record_cascade_latency(9000)
    assert bp.should_skip_cascade() is False


def test_partial_expiry_keeps_recent_samples(monkeypatch):
    # Old high samples expire; only recent ones count toward the average.
    t = [1000.0]; _set_clock(monkeypatch, t)
    bp.reset_for_tests()
    _configure(monkeypatch, AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES=3,
               AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS=1000,
               AGW_CASCADE_BACKPRESSURE_TTL_SECONDS=10)
    for _ in range(5):
        bp.record_cascade_latency(9000)      # at t=1000
    t[0] += 6
    for _ in range(5):
        bp.record_cascade_latency(100)       # at t=1006 (fast)
    t[0] += 6                                 # now t=1012: the 9000s (t=1000) are >10s old
    assert bp.recent_sample_count() == 5      # only the fast ones survive
    assert bp.should_skip_cascade() is False  # avg 100 < 1000
