#!/usr/bin/env python3
"""
Loads + scales agent-query "traces" data into local Elasticsearch for the scale-test account.

- Re-indexes the original prod dump (227 docs) with accountId remapped and timestamps rebased to recent.
- Fabricates additional sessions/traces/spans per device (all N devices) with realistic prompts
  drawn from the real Q&A pool, organized into coherent sessions for the Traces/LLM-observability UI.

Env/config (all optional):
  ES_HOST (default http://localhost:9200), INDEX (default agent_query_logs)
  ACCOUNT (default 1785786030)  -- the scale-test account docs are scoped to
  RAW_HITS (default traces/raw_hits.ndjson), QA_POOL (traces/qa_pool.json), DEVICES (traces/devices.json)
  SESS_MIN/SESS_MAX (sessions per device, default 3/6)
  TURN_MIN/TURN_MAX (messages per session, default 3/12)
  DAYS (recent window, default 7), SEED (default 42)
  WIPE (default 1 -> delete+recreate index docs for ACCOUNT before load)
"""
import json, os, random, time, uuid, urllib.request, urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
ES_HOST = os.environ.get("ES_HOST", "http://localhost:9200").rstrip("/")
INDEX   = os.environ.get("INDEX", "agent_query_logs")
ACCOUNT = int(os.environ.get("ACCOUNT", "1785786030"))
RAW_HITS = os.environ.get("RAW_HITS", os.path.join(HERE, "traces", "raw_hits.ndjson"))
QA_POOL  = os.environ.get("QA_POOL",  os.path.join(HERE, "traces", "qa_pool.json"))
DEVICES  = os.environ.get("DEVICES",  os.path.join(HERE, "traces", "devices.json"))
SESS_MIN = int(os.environ.get("SESS_MIN", "3")); SESS_MAX = int(os.environ.get("SESS_MAX", "6"))
TURN_MIN = int(os.environ.get("TURN_MIN", "3")); TURN_MAX = int(os.environ.get("TURN_MAX", "12"))
DAYS = int(os.environ.get("DAYS", "7"))
SEED = int(os.environ.get("SEED", "42"))
WIPE = os.environ.get("WIPE", "1") == "1"

random.seed(SEED)
NOW_MS = int(time.time() * 1000)

MODELS = ["claude-haiku-4-5-20251001", "claude-sonnet-4-5-20250929", "claude-opus-4-1-20250805",
          "gpt-5-codex", "gpt-4.1-2025-04-14", "gemini-2.5-pro", "gemini-2.0-flash",
          "cursor-fast-1", "claude-3-7-sonnet-20250219", "o4-mini"]
SESSION_SERVICES_FALLBACK = ["claudecli", "codexcli", "cursor"]

def es(method, path, body=None, raw=False):
    data = body.encode() if (raw and body is not None) else (json.dumps(body).encode() if body is not None else None)
    ct = "application/x-ndjson" if raw else "application/json"
    req = urllib.request.Request(ES_HOST + path, data=data, method=method, headers={"Content-Type": ct})
    try:
        return urllib.request.urlopen(req).read().decode()
    except urllib.error.HTTPError as e:
        return "ERR %d %s" % (e.code, e.read().decode()[:400])

_bulk_buf = []
_bulk_count = [0]
def emit(doc):
    _bulk_buf.append('{"index":{"_index":"%s"}}' % INDEX)
    _bulk_buf.append(json.dumps(doc))
    if len(_bulk_buf) >= 4000:
        flush()
def flush():
    if not _bulk_buf: return
    payload = "\n".join(_bulk_buf) + "\n"
    r = es("POST", "/_bulk", payload, raw=True)
    if r.startswith("ERR") or '"errors":true' in r:
        print("  BULK error sample:", r[:300])
    _bulk_count[0] += len(_bulk_buf) // 2
    _bulk_buf.clear()

def span_id(): return "span_" + str(uuid.uuid4())
def trace_id(): return str(uuid.uuid4())

# ---------- load pools ----------
qa = json.load(open(QA_POOL))                       # topic -> subTopic -> [ {q,a,inTok,outTok} ]
TOPICS = [t for t in qa if any(qa[t].values())]
SUBS = {t: [s for s in qa[t] if qa[t][s]] for t in TOPICS}
devices = json.load(open(DEVICES))
print("Loaded qa topics=%d, devices=%d" % (len(TOPICS), len(devices)))

def pick_qa(topic, sub):
    lst = qa.get(topic, {}).get(sub) or []
    if not lst:
        # fall back to any qa in topic
        for s in qa.get(topic, {}):
            if qa[topic][s]:
                return random.choice(qa[topic][s]), s
    return (random.choice(lst) if lst else {"q": "Help me with a task.", "a": "Sure, here is how.", "inTok": 120, "outTok": 60}), sub

# ---------- wipe existing docs for this account ----------
if WIPE:
    r = es("POST", "/%s/_delete_by_query?refresh=true" % INDEX, {"query": {"term": {"accountId": ACCOUNT}}})
    print("Wiped existing account docs:", r[:120])

# ---------- 1) reindex original dump (account remap + timestamp rebase to recent) ----------
orig = []
if os.path.exists(RAW_HITS):
    for line in open(RAW_HITS):
        line = line.strip()
        if not line: continue
        try: src = json.loads(line)["_source"]
        except Exception: continue
        orig.append(src)
if orig:
    # only keep original docs whose device still exists in the current roster (otherwise the Traces
    # page shows a device that the Endpoints/Shield pages don't — e.g. a purged/deduped hostname)
    roster_hosts = set(dev["host"] for dev in devices)
    kept = [d for d in orig if d.get("deviceId") in roster_hosts]
    dropped = len(orig) - len(kept)
    max_ts = max((d.get("timestamp") or 0) for d in kept) or NOW_MS
    offset = NOW_MS - max_ts          # shift so newest original doc == now
    for d in kept:
        d = dict(d)
        d["accountId"] = ACCOUNT
        if d.get("timestamp"): d["timestamp"] = d["timestamp"] + offset
        emit(d)
    print("Reindexed %d original docs (dropped %d for devices not in roster, offset=%d ms)" % (len(kept), dropped, offset))

# ---------- 2) fabricate per-device sessions ----------
def gen_session(dev):
    host = dev["host"]; user = dev.get("user") or host.split("-")[0]
    services = dev.get("services") or []
    services = [s for s in services if s] or SESSION_SERVICES_FALLBACK
    service = random.choice(services)
    sid = str(uuid.uuid4())
    model = random.choice(MODELS)
    # session spans a window ending within the last DAYS
    start = NOW_MS - random.randint(0, DAYS * 86400 * 1000) - random.randint(0, 3600 * 1000)
    t = start
    cwd = "/Users/%s" % user
    transcript = "%s/.claude/projects/-Users-%s/%s.jsonl" % (cwd, user, sid)
    base = dict(accountId=ACCOUNT, deviceId=host, userName=user, serviceId=service,
                sessionIdentifier=sid, isAtlasTraffic=True, topicProcessed=True, model=model)

    def doc(**kw):
        d = dict(base); d.update(kw); return d

    # SessionStart
    stopic = random.choice(TOPICS); ssub = random.choice(SUBS[stopic])
    emit(doc(timestamp=t, topic=stopic, subTopic=ssub, traceId="", spanId=span_id(),
             inputTokens=random.randint(60, 400), outputTokens=random.randint(5, 20),
             queryPayload=json.dumps({"body": {"session_id": sid, "transcript_path": transcript,
                 "cwd": cwd, "hook_event_name": "SessionStart", "source": "startup", "model": model}}),
             responsePayload=json.dumps({"body": {}})))
    t += random.randint(1000, 8000)

    turns = random.randint(TURN_MIN, TURN_MAX)
    # a session leans toward 1-3 topics for coherence
    sess_topics = random.sample(TOPICS, k=min(len(TOPICS), random.randint(1, 3)))
    for _ in range(turns):
        topic = random.choice(sess_topics)
        sub = random.choice(SUBS[topic])
        (pair, sub) = pick_qa(topic, sub)
        tid = trace_id()
        intok = int(pair.get("inTok") or random.randint(40, 900))
        outtok = int(pair.get("outTok") or random.randint(10, 500))
        # user-prompt span
        emit(doc(timestamp=t, topic=topic, subTopic=sub, traceId=tid, spanId=span_id(),
                 inputTokens=intok, outputTokens=outtok,
                 queryPayload=json.dumps({"body": pair["q"]}),
                 responsePayload=json.dumps({"body": pair.get("a") or "", "model": model})))
        t += random.randint(400, 4000)
        # optional thought/stop span(s) sharing the traceId (enriches span waterfall)
        for _ in range(random.randint(0, 2)):
            ev = random.choice(["afterAgentThought", "stop", "SubagentStop", "Notification"])
            emit(doc(timestamp=t, topic=topic, subTopic=sub, traceId=tid, spanId=span_id(),
                     inputTokens=random.randint(10, 300), outputTokens=random.randint(0, 200),
                     queryPayload=json.dumps({"body": {"hook_event_name": ev, "session_id": sid}}),
                     responsePayload=json.dumps({"body": {}})))
            t += random.randint(300, 3000)
    # SessionEnd
    emit(doc(timestamp=t, topic=stopic, subTopic=ssub, traceId="", spanId=span_id(),
             inputTokens=random.randint(10, 80), outputTokens=random.randint(0, 10),
             queryPayload=json.dumps({"body": {"hook_event_name": "SessionEnd", "session_id": sid}}),
             responsePayload=json.dumps({"body": {}})))
    return turns

total_sessions = 0
for i, dev in enumerate(devices):
    for _ in range(random.randint(SESS_MIN, SESS_MAX)):
        gen_session(dev); total_sessions += 1
    if (i + 1) % 25 == 0 or i + 1 == len(devices):
        print("  ... %d/%d devices" % (i + 1, len(devices)))
flush()
es("POST", "/%s/_refresh" % INDEX)

# ---------- report ----------
cnt = es("GET", "/%s/_count" % INDEX, {"query": {"term": {"accountId": ACCOUNT}}})
try: total = json.loads(cnt).get("count")
except Exception: total = cnt
print("\n==== traces loaded ====")
print("  fabricated sessions: %d" % total_sessions)
print("  bulk docs indexed:   %d" % _bulk_count[0])
print("  docs in ES for account %d: %s" % (ACCOUNT, total))
