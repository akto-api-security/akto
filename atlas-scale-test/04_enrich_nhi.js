// Wipes and regenerates rich NHI data (identities + violations) for EVERY endpoint-shield
// device in the NEW account. Idempotent: always delete-all then regenerate.
// Variety/values are drawn from BOTH accounts (this db + OTHER) plus synthetic pools, tuned so the
// Identities table, summary tiles, tabs, and the agent/identity graph all exercise varied data.
// Config injected by run.sh header: OTHER (acct db for enrichment), NOW (epoch s), SEED,
//   PER_MIN, PER_MAX (identities per device), VIOL_RATIO (fraction of identity-names that have violations)
(function () {
  const other = db.getSiblingDB(String(OTHER));
  const DAY = 86400;
  const PMIN = (typeof PER_MIN !== "undefined") ? PER_MIN : 8;
  const PMAX = (typeof PER_MAX !== "undefined") ? PER_MAX : 20;
  const VR = (typeof VIOL_RATIO !== "undefined") ? VIOL_RATIO : 0.45;

  let _s = (((SEED >>> 0) ^ (Math.imul(NOW >>> 0, 2654435761))) >>> 0) || 1;
  function rnd() { _s = (Math.imul(_s, 1664525) + 1013904223) >>> 0; return _s / 4294967296; }
  function pick(a) { return a[Math.floor(rnd() * a.length)]; }
  function ri(lo, hi) { return lo + Math.floor(rnd() * (hi - lo + 1)); }
  function hex(n) { let s = ""; for (let i = 0; i < n; i++) s += Math.floor(rnd() * 16).toString(16); return s; }
  function wpick(pairs) { // [[val,weight]...]
    let t = 0; pairs.forEach(p => t += p[1]); let r = rnd() * t;
    for (const p of pairs) { if ((r -= p[1]) <= 0) return p[0]; } return pairs[0][0];
  }

  // ---- pools drawn from both accounts ----
  function distinctBoth(field) {
    const s = new Set();
    db.nhi_identities.distinct(field).forEach(v => { if (v !== null && v !== undefined && v !== "") s.add(v); });
    try { other.nhi_identities.distinct(field).forEach(v => { if (v !== null && v !== undefined && v !== "") s.add(v); }); } catch (e) {}
    return Array.from(s);
  }
  let NAME_POOL = distinctBoth("identityName");
  if (NAME_POOL.length < 10) NAME_POOL = NAME_POOL.concat(
    ["github.Authorization","notion-mcp.NOTION_TOKEN","openai-token","anthropic-api-key","aws-access-key",
     "stripe-secret","slack-bot-token","datadog-api-key","postgres-token","docker-token"]);
  const TR_POOL = [];
  [db, other].forEach(dbx => { try {
    dbx.nhi_identities.find({ targetResource: { $exists: true, $ne: [] } }, { targetResource: 1 }).limit(80)
      .forEach(d => { if (d.targetResource && d.targetResource.length) TR_POOL.push(d.targetResource); });
  } catch (e) {} });
  const VIO_TPL = [];
  [db, other].forEach(dbx => { try { dbx.nhi_violations.find({}).limit(60).forEach(v => VIO_TPL.push(v)); } catch (e) {} });

  // agent-name pools chosen to hit getAgentType() -> AI Agent / MCP Server / LLM node types
  const AI_AGENTS = ["Claude CLI","Cursor","VS Code","Codex","Antigravity","Copilot","Kiro CLI",
    "Claude Desktop","Kiro IDE","Codex CLI","Windsurf","Amazon Q / Kiro","Claude Code"];
  const MCP_AGENTS = ["Filesystem","Postgres","Docker","Atlassian","Playwright","Stripe","AWS","Azure","Universal"];
  const LLM_AGENTS = ["Claude","OpenAI","Anthropic","Gemini","Perplexity","Cohere","Langchain","Grok"];
  function pickAgent() { const r = rnd(); return r < 0.6 ? pick(AI_AGENTS) : (r < 0.8 ? pick(MCP_AGENTS) : pick(LLM_AGENTS)); }

  const ITYPE = ["OAuth Token","API Key","Bearer Token","Basic Auth"];
  const RISK = ["LOW","MEDIUM","HIGH","CRITICAL"];
  const ACCESS = ["READ","READ_WRITE","ADMIN","NONE"];
  const SRCTYPE = ["aws_sso_cache","mcp_config","env_file","keychain","dotfile"];
  const VIO_SEV = [["Critical", 2], ["High", 3], ["Medium", 4], ["Low", 1]]; // Low won't bucket (intended)

  // identity-names that carry violations (controlled subset => varied "has violations" + graph colors)
  const shuffled = NAME_POOL.slice(); for (let i = shuffled.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); const t = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = t; }
  const violatedNames = new Set(shuffled.slice(0, Math.max(1, Math.round(NAME_POOL.length * VR))));

  function discDate() { const r = rnd();
    if (r < 0.55) return NOW - ri(0, 7) * DAY - ri(0, 86399);   // last week (most)
    if (r < 0.85) return NOW - ri(8, 30) * DAY;                 // last month
    return NOW - ri(31, 120) * DAY; }                           // older tail
  function expiryFor() { const r = rnd();
    if (r < 0.33) return NOW + ri(5, 120) * DAY;   // "Nd left"
    if (r < 0.50) return NOW + ri(0, 4) * DAY;     // rotation due soon
    if (r < 0.78) return NOW - ri(1, 25) * DAY;    // "Expired Nd ago"
    return null; }                                 // "No expiry"
  function statusFor() { return wpick([["ACTIVE", 72], ["INACTIVE", 14], ["EXPIRED", 14]]); }

  // ---- wipe existing NHI (throwaway account) ----
  const delI = db.nhi_identities.deleteMany({}).deletedCount;
  const delV = db.nhi_violations.deleteMany({}).deletedCount;
  print("Wiped nhi_identities=" + delI + " nhi_violations=" + delV);

  const devices = db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }).toArray();
  print("Devices: " + devices.length + " | per-device " + PMIN + "-" + PMAX +
        " | violatedNames " + violatedNames.size + "/" + NAME_POOL.length);

  function makeViolation(idObj, iname, agent, dname) {
    const base = VIO_TPL.length ? Object.assign({}, pick(VIO_TPL)) : {};
    const disc = NOW - ri(0, 20) * DAY;
    base._id = new ObjectId();
    base.identities = [{ id: idObj, identityName: iname }];
    base.agentName = agent;
    base.contextSource = "ENDPOINT";
    base.severity = wpick(VIO_SEV);
    base.status = pick(["Investigating", "Open", "Resolved", "Acknowledged"]);
    base.discoveredAt = disc;
    base.createdAt = disc;
    base.updatedAt = NOW - ri(0, 3) * DAY;
    base.updatedBy = "scale-fab";
    base.deviceLabel = dname;
    delete base.accountId;
    return base;
  }

  let idBuf = [], vBuf = [], nId = 0, nV = 0;
  function flush() {
    if (idBuf.length) { try { db.nhi_identities.insertMany(idBuf, { ordered: false }); nId += idBuf.length; } catch (e) { nId += (e.result && e.result.nInserted) || 0; } idBuf = []; }
    if (vBuf.length) { try { db.nhi_violations.insertMany(vBuf, { ordered: false }); nV += vBuf.length; } catch (e) { nV += (e.result && e.result.nInserted) || 0; } vBuf = []; }
  }

  devices.forEach(function (dev, di) {
    const Dname = dev.name;
    const hw = (dev.additionalData && dev.additionalData.deviceId) || hex(32);
    const username = (dev.additionalData && dev.additionalData.username) || Dname.split("-")[0];
    const k = ri(PMIN, PMAX);
    for (let j = 0; j < k; j++) {
      const iname = pick(NAME_POOL);
      const itype = pick(ITYPE);
      const agent = pickAgent();
      const created = discDate();
      const _id = new ObjectId();
      const exp = expiryFor();
      const doc = {
        _id: _id,
        identityName: iname,
        identityType: itype,
        contextSource: "ENDPOINT",
        agentName: agent,
        agentType: (LLM_AGENTS.indexOf(agent) >= 0 || MCP_AGENTS.indexOf(agent) >= 0) ? "AI_AGENT" : pick(["AI_AGENT", "CODE_EDITOR"]),
        owner: { name: Dname },
        deviceId: hw,
        deviceLabel: Dname,
        accessLevel: pick(ACCESS),
        source: "/Users/" + username + "/" + pick([".aws/sso/cache/", ".config/", ".mcp/", ".ssh/"]) + hex(12) + ".json",
        sourceType: pick(SRCTYPE),
        status: statusFor(),
        riskLevel: pick(RISK),
        createdAt: created,
        firstSeenAt: created,
        lastSeenAt: NOW - ri(0, 3) * DAY,
        lastUsedAt: NOW - ri(0, 5) * DAY,
        lastRotatedAt: created,
        createdBy: "scale-fab",
        updatedBy: "scale-fab",
        updatedAt: NOW,
        hash: hex(64),
        prefix: hex(4),
        suffix: hex(4),
        metadata: { field: String(iname).split(".").pop() },
        relatedViolationIds: []
      };
      if (exp !== null) doc.expiryDate = exp;
      if (TR_POOL.length && rnd() < 0.5) doc.targetResource = pick(TR_POOL);
      idBuf.push(doc);

      if (violatedNames.has(iname) && rnd() < 0.6) {
        const nv = ri(1, 3);
        const vids = [];
        for (let m = 0; m < nv; m++) { const v = makeViolation(_id, iname, agent, Dname); vBuf.push(v); vids.push(v._id.toString()); }
        doc.relatedViolationIds = vids;
      }
    }
    if (idBuf.length >= 2000 || vBuf.length >= 2000) flush();
    if ((di + 1) % 50 === 0 || di + 1 === devices.length) print("  ... " + (di + 1) + "/" + devices.length + " devices");
  });
  flush();

  print("\n==== NHI regenerated ====");
  print("  nhi_identities: " + nId);
  print("  nhi_violations: " + nV);
  print("  device-linked identities: " + db.nhi_identities.countDocuments({ deviceId: { $exists: true } }));
  print("  distinct identityName: " + db.nhi_identities.distinct("identityName").length);
  print("  distinct agentName: " + db.nhi_identities.distinct("agentName").length);
  print("  INACTIVE (Disabled tab): " + db.nhi_identities.countDocuments({ status: "INACTIVE" }));
  print("  createdAt within 7d: " + db.nhi_identities.countDocuments({ createdAt: { $gte: NOW - 7 * DAY } }));
})();
