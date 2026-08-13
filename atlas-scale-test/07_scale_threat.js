// Scales threat/guardrail data (malicious_events, actor_info) in the NEW account.
//  - Per fabricated device: clones the device-linked guardrail events + derives actor_info (host rewrite).
//  - Volume: adds API/agentic actors (reserved-range IPs) + their malicious_events for the actors /
//    world-map / severity / threat-API / timeline pages.
// Idempotent: deletes prior fabricated content (device events for non-source devices + reserved-range
// volume actors) before regenerating. api_distribution_data is intentionally NOT scaled (no visible page).
// Connected to NEW_ACCT db. Config injected: SRC (source acct db), NOW (epoch s), SEED,
//   EXTRA_ACTORS (default 250), WIPE (default true)
(function () {
  const src = db.getSiblingDB(String(SRC));
  const DAY = 86400;
  const NEXTRA = (typeof EXTRA_ACTORS !== "undefined") ? EXTRA_ACTORS : 250;

  let _s = (((SEED >>> 0) ^ (Math.imul(NOW >>> 0, 2654435761))) >>> 0) || 1;
  function rnd() { _s = (Math.imul(_s, 1664525) + 1013904223) >>> 0; return _s / 4294967296; }
  function pick(a) { return a[Math.floor(rnd() * a.length)]; }
  function ri(lo, hi) { return lo + Math.floor(rnd() * (hi - lo + 1)); }
  function hex(n) { let s = ""; for (let i = 0; i < n; i++) s += Math.floor(rnd() * 16).toString(16); return s; }
  function uuid() { return hex(8) + "-" + hex(4) + "-" + hex(4) + "-" + hex(4) + "-" + hex(12); }
  function esc(x) { return String(x).replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }
  function rewritePrefix(s, from, to) { return (s && s.indexOf(from) === 0) ? to + s.substring(from.length) : s; }
  function recentTs() { const r = rnd(); return r < 0.6 ? NOW - ri(0, 7) * DAY - ri(0, 86399) : (r < 0.9 ? NOW - ri(8, 21) * DAY : NOW - ri(22, 60) * DAY); }
  // timestamps MUST be stored as BSON Long — the threat backend reads them via Document.getLong()
  // (mongosh would otherwise store integers <= 2^31 as Int32, causing ClassCastException).
  function L(x) { return NumberLong(String(Math.floor(x))); }

  // source device names => which macbook hosts are "templates" (have real events) vs fabricated
  const srcNames = new Set(src.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { name: 1 }).toArray().map(d => d.name));
  const allDevices = db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { name: 1 }).toArray().map(d => d.name);
  const fabricated = allDevices.filter(n => !srcNames.has(n));

  // template device prefixes that actually have events
  const tmplCounts = {};
  db.malicious_events.find({ host: /-macbook-/ }, { host: 1 }).forEach(d => {
    const p = (d.host || "").split(".")[0];
    if (srcNames.has(p)) tmplCounts[p] = (tmplCounts[p] || 0) + 1;
  });
  const templates = Object.keys(tmplCounts);
  print("source devices=" + srcNames.size + " | fabricated=" + fabricated.length + " | event-templates=" + templates.length);
  if (!templates.length) { print("ERROR: no template device events found"); return; }

  // ---- idempotent cleanup of prior fabricated content ----
  const RESERVED = /^198\.(18|19)\./;   // volume actors live in the 198.18.0.0/15 benchmark range (131k IPs)
  if (typeof WIPE === "undefined" || WIPE) {
    // delete fabricated device events + actors (macbook hosts whose prefix is not a source device)
    let delDev = 0, eids = [];
    db.malicious_events.find({ host: /-macbook-/ }, { host: 1 }).forEach(d => {
      if (!srcNames.has((d.host || "").split(".")[0])) eids.push(d._id);
    });
    for (let i = 0; i < eids.length; i += 5000) delDev += db.malicious_events.deleteMany({ _id: { $in: eids.slice(i, i + 5000) } }).deletedCount;
    let delDevA = 0, aids = [];
    db.actor_info.find({ host: /-macbook-/ }, { host: 1 }).forEach(d => {
      if (!srcNames.has((d.host || "").split(".")[0])) aids.push(d._id);
    });
    for (let i = 0; i < aids.length; i += 5000) delDevA += db.actor_info.deleteMany({ _id: { $in: aids.slice(i, i + 5000) } }).deletedCount;
    const delVol = db.malicious_events.deleteMany({ actor: { $regex: RESERVED } }).deletedCount;
    const delVolA = db.actor_info.deleteMany({ actorId: { $regex: RESERVED } }).deletedCount;
    print("cleanup: deviceEvents=" + delDev + " deviceActors=" + delDevA + " volEvents=" + delVol + " volActors=" + delVolA);
  }

  // pools for volume fabrication
  const API_CATS = ["BOLA", "BUA", "MA", "EDE", "SSRF", "RL", "ApiRateLimiting", "AnomalousBehaviourDetection", "SchemaConform"];
  const API_SUB = { BOLA: "Broken Object Level Authorization", BUA: "Broken User Authentication", MA: "Mass Assignment",
    EDE: "Excessive Data Exposure", SSRF: "Server Side Request Forgery", RL: "Rate Limiting",
    ApiRateLimiting: "ApiRateLimiting", AnomalousBehaviourDetection: "AnomalousBehaviourDetection", SchemaConform: "SchemaConform" };
  const API_FILTERS = { BOLA: "BOLAInParams", BUA: "SQLInjectionInQueryParam", MA: "MassAssignmentInParams",
    EDE: "DataExfiltrationInParams", SSRF: "SSRFInParams", RL: "High4XXAlertFilter",
    ApiRateLimiting: "ApiRateLimiting", AnomalousBehaviourDetection: "AnomalousBehaviourDetection", SchemaConform: "SchemaConform" };
  const HOSTS = ["api.investment.bankone.com", "api.accounts.bankone.com", "juiceshop.akto.io", "api.payments.fintechx.io", "gateway.shopwave.io", "api.healthsync.io"];
  const ENDPOINTS = ["/investments/trades", "/investments/stocks/AAPL", "/rest/user/login", "/api/Feedbacks/",
    "accounts/STRING/beneficiaries/STRING", "/api/orders/INTEGER", "/api/users/INTEGER/profile", "/v2/payments/charge", "/graphql"];
  const METHODS = ["GET", "POST", "PUT", "DELETE", "PATCH"];
  const SEV = [["CRITICAL", 2], ["HIGH", 4], ["MEDIUM", 4]];
  const COUNTRIES = ["IN", "US", "GB", "DE", "FR", "CN", "RU", "BR", "SG", "AE", "NL", "PK", "KR", "ZA", "MX", "HK", "AU"];
  function wsev() { let t = 0; SEV.forEach(p => t += p[1]); let r = rnd() * t; for (const p of SEV) if ((r -= p[1]) <= 0) return p[0]; return "HIGH"; }
  // unique volume actor IPs in 198.18.0.0/15 (benchmark range) — actor_info is UNIQUE on (actorId, contextSource)
  const usedIps = new Set();
  function volIp() { let s; do { s = "198." + pick([18, 19]) + "." + ri(0, 255) + "." + ri(1, 254); } while (usedIps.has(s)); usedIps.add(s); return s; }
  // external agent hosts for AGENTIC-context volume
  const AGENTIC_HOSTS = ["vulnerable-agent-kong.akto.io", "api.smith.langchain.com", "damnvulnerableagent.akto.io",
    "887089841930.bedrock.amazonaws.com", "api.githubcopilot.com", "perplexity.ai", "janitorai.com"];
  const AGENTIC_CATS = ["DirectPromptInjection", "IndirectPromptInjection", "Data Exfiltration", "LLMExcessiveAgency",
    "Hidden Instructions", "harmful-content", "MaliciousCodeInjection", "AnomalousBehaviourDetection"];

  const totals = {};
  function bump(k, v) { totals[k] = (totals[k] || 0) + (v || 0); }
  let evBuf = [], acBuf = [];
  function flush() {
    if (evBuf.length) { try { db.malicious_events.insertMany(evBuf, { ordered: false }); bump("malicious_events", evBuf.length); } catch (e) { bump("malicious_events", (e.result && e.result.nInserted) || 0); } evBuf = []; }
    if (acBuf.length) { try { db.actor_info.insertMany(acBuf, { ordered: false }); bump("actor_info", acBuf.length); } catch (e) { bump("actor_info", (e.result && e.result.nInserted) || 0); } acBuf = []; }
  }

  // ---- 1) per-device guardrail events + actor_info ----
  const collCache = {};
  function fabColls(host) {
    if (collCache[host] === undefined) collCache[host] = db.api_collections.find({ hostName: new RegExp("^" + esc(host) + "\\.") }, { _id: 1 }).toArray().map(c => c._id);
    return collCache[host];
  }
  // NOTE: actor_info is a per-actor dedup table (UNIQUE on actorId+contextSource) — not read when
  // USE_ACTOR_INFO_TABLE=false (run-tbs.sh). The Actors page aggregates malicious_events by `actor`.
  // In the seed data the ENDPOINT `actor` is a shared scanner/client/seed-author name (claude, cursor,
  // settings-scanner, vrt...) reused across all devices, so device scaling alone gives only ~8 actors.
  // Fix: rewrite each cloned device's NON-IP actor to that device's own username, so endpoint actors
  // scale per device (the agent-client is still captured in `host` = <device>.ai-agent.<client>).
  const userByDevice = {};
  db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { name: 1, "additionalData.username": 1 }).forEach(d => {
    userByDevice[d.name] = (d.additionalData && d.additionalData.username) || d.name.split("-")[0];
  });
  const IPRE = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/;
  fabricated.forEach(function (fab, i) {
    const tmpl = pick(templates);
    const uname = userByDevice[fab] || fab.split("-")[0];
    const evs = db.malicious_events.find({ host: new RegExp("^" + esc(tmpl) + "\\.") }).toArray();
    const colls = fabColls(fab);
    evs.forEach(function (e) {
      const n = Object.assign({}, e);
      n._id = uuid();
      n.refId = uuid();
      n.host = rewritePrefix(e.host, tmpl, fab);
      if (colls.length) n.latestApiCollectionId = pick(colls);
      n.detectedAt = L(recentTs());
      // per-device actor: replace shared scanner/client/user names with this device's user; keep real IPs
      if (n.actor && !IPRE.test(String(n.actor))) { n.actor = uname; if (n.latestApiIp && !IPRE.test(String(n.latestApiIp))) n.latestApiIp = uname; }
      evBuf.push(n);
    });
    if (evBuf.length >= 3000) flush();
    if ((i + 1) % 50 === 0 || i + 1 === fabricated.length) print("  ... device threats " + (i + 1) + "/" + fabricated.length);
  });
  flush();

  // ---- 2) volume: unique API + AGENTIC actors + their events (198.18.0.0/15 range) ----
  for (let a = 0; a < NEXTRA; a++) {
    const isApi = rnd() < 0.7;                 // ~70% API actors, ~30% agentic
    const actor = volIp();
    const cs = isApi ? "API" : "AGENTIC";
    const cat = isApi ? pick(API_CATS) : pick(AGENTIC_CATS);
    const sub = isApi ? API_SUB[cat] : cat;
    const filterId = isApi ? API_FILTERS[cat] : cat;
    const host = isApi ? pick(HOSTS) : pick(AGENTIC_HOSTS);
    const sev = wsev();
    const country = pick(COUNTRIES);
    const endpoint = isApi ? pick(ENDPOINTS) : pick(["/v1/chat/completions", "/invoke", "/agent/run", "/mcp/tool/call", "/generate"]);
    const method = pick(METHODS);
    const coll = 1700000000 + ri(0, 99999999);
    const last = recentTs();
    acBuf.push({ _id: new ObjectId(), filterId: filterId, category: cat, apiCollectionId: coll, url: endpoint,
      method: method, country: country, severity: sev, host: host, latestMetadata: null, lastAttackTs: L(last),
      discoveredAt: L(last - ri(1, 40) * DAY), totalAttacks: ri(1, 300), actorId: actor, contextSource: cs,
      isCritical: sev === "CRITICAL", status: "ACTIVE" });
    const nev = ri(2, 15);
    for (let e = 0; e < nev; e++) {
      const ts = recentTs();
      evBuf.push({ _id: uuid(), actor: actor, category: cat, subCategory: sub, country: country,
        detectedAt: L(ts), eventType: pick(["AGGREGATED", "SINGLE"]), filterId: filterId,
        latestApiCollectionId: coll, latestApiIp: actor, latestApiMethod: method, latestApiEndpoint: endpoint,
        type: pick(["Anomaly", "Rule-Based"]), refId: uuid(), severity: sev, successfulExploit: rnd() < 0.25,
        contextSource: cs, host: host, status: "ACTIVE", label: "THREAT" });
    }
    if (evBuf.length >= 3000 || acBuf.length >= 3000) flush();
  }
  flush();

  print("\n==== threat scaled ====");
  Object.keys(totals).sort().forEach(k => print("  inserted " + k + ": " + totals[k]));
  print("  malicious_events total: " + db.malicious_events.countDocuments({}));
  print("  actor_info total: " + db.actor_info.countDocuments({}));
  print("  by contextSource:");
  db.malicious_events.aggregate([{ $group: { _id: "$contextSource", n: { $sum: 1 } } }]).forEach(d => print("    " + d._id + ": " + d.n));
})();
