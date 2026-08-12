// Scales endpoint-shield devices in the NEW account up to TARGET by cloning + fabricating
// distinct device bundles from the copied source data (and NHI pool from OTHER account).
// Connected to the NEW_ACCT database. Config injected by run.sh header:
//   TARGET (number of total devices desired), OTHER (source acct db name for NHI enrichment),
//   NOW (epoch seconds), SEED (int for deterministic PRNG)
(function () {
  const other = db.getSiblingDB(String(OTHER));
  const DAY = 86400;

  // ---- deterministic-per-run PRNG (LCG); mixes NOW so incremental top-ups don't repeat ----
  let _s = (((SEED >>> 0) ^ (Math.imul(NOW >>> 0, 2654435761)) ^ (db.module_info.countDocuments({ moduleType: "MCP_ENDPOINT_SHIELD" }) * 40503)) >>> 0) || 1;
  function rnd() { _s = (Math.imul(_s, 1664525) + 1013904223) >>> 0; return _s / 4294967296; }
  function pick(a) { return a[Math.floor(rnd() * a.length)]; }
  function ri(lo, hi) { return lo + Math.floor(rnd() * (hi - lo + 1)); }
  function hex(n) { let s = ""; for (let i = 0; i < n; i++) s += Math.floor(rnd() * 16).toString(16); return s; }
  function uuid() { return hex(8) + "-" + hex(4) + "-" + hex(4) + "-" + hex(4) + "-" + hex(12); }
  function esc(x) { return String(x).replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }
  function rewritePrefix(s, from, to) { return (s && s.indexOf(from) === 0) ? to + s.substring(from.length) : s; }

  // ---- fabrication pools ----
  const FIRST = ["oliver","emma","liam","ava","noah","sophia","ethan","isabella","mason","mia","lucas","amelia",
    "logan","harper","james","evelyn","ben","abigail","henry","emily","alex","ella","daniel","scarlett","matthew",
    "grace","david","chloe","joseph","victoria","samuel","riley","john","aria","owen","lily","gabriel","zoe",
    "carter","nora","priya","arjun","meera","rohan","ananya","kabir","diya","vivaan","aisha","reyansh"];
  const LAST = ["smith","johnson","williams","brown","jones","garcia","miller","davis","rodriguez","martinez",
    "hernandez","lopez","gonzalez","wilson","anderson","thomas","taylor","moore","jackson","martin","lee","perez",
    "thompson","white","harris","clark","lewis","robinson","walker","young","allen","king","wright","scott",
    "green","baker","adams","nelson","patel","sharma","kumar","reddy","nair","iyer","mehta","gupta","shah"];
  const OS_POOL = ["macOS 15.6","macOS 26.5.1","macOS 26.5.2","macOS 15.5","macOS 14.7.1","macOS 26.4"];
  const VER_POOL = ["v1.1.155-1-g2e603b7","v1.1.155-1-g2e603b72","v1.1.150-3-ga1b2c3d","v1.1.148-2-gf9e8d7c"];
  const TEAMS = ["Engineering","IT Support","Security","Data Science","Platform","Product","DevOps","Finance","Sales Engineering","Research"];
  const ROLES = ["Member","Lead","Admin","Contractor","Manager"];
  const AGENTS = ["Claude CLI","Cursor","VS Code","Codex","Antigravity","Copilot","Kiro CLI","Claude Desktop",
    "Kiro IDE","Codex CLI","Windsurf","Codex Desktop","Amazon Q / Kiro","Claude Code"];
  const NHI_TYPES = ["OAuth Token","API Key","Bearer Token","Basic Auth"];
  const NHI_STATUS = ["ACTIVE","ACTIVE","ACTIVE","EXPIRED","DISABLED"];
  const NHI_RISK = ["LOW","MEDIUM","MEDIUM","HIGH","CRITICAL"];

  function ip() { return ri(10,220) + "." + ri(0,255) + "." + ri(0,255) + "." + ri(1,254); }

  // ---- collection-id allocator (int32, verified free) ----
  const existingCollIds = new Set();
  db.api_collections.find({}, { _id: 1 }).forEach(d => existingCollIds.add(d._id));
  let _cid = 1000000000;
  function newCollId() { while (existingCollIds.has(_cid)) _cid++; existingCollIds.add(_cid); return _cid++; }

  // ---- chunked insert helper ----
  function insertChunked(coll, docs) {
    if (!docs.length) return 0;
    let n = 0;
    for (let i = 0; i < docs.length; i += 2000) {
      const batch = docs.slice(i, i + 2000);
      try { db.getCollection(coll).insertMany(batch, { ordered: false }); n += batch.length; }
      catch (e) { n += (e.result && e.result.nInserted) || 0; }
    }
    return n;
  }

  // ---- dedupe duplicate-hostname devices (the source copy registers 2 devices twice) so that
  //      "devices" always means DISTINCT hostnames — otherwise pages counting module_info docs vs
  //      distinct hostnames disagree (e.g. 1000 endpoints vs 998 endpoint-shield). Extra registrations
  //      share the same api_collections, so dropping them loses no asset data.
  (function dedupeDevices() {
    // for each hostname, KEEP the richest registration (one with a hardware deviceId), drop the rest
    const groups = {};
    db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { name: 1, "additionalData.deviceId": 1 }).forEach(function (d) {
      (groups[d.name] = groups[d.name] || []).push(d);
    });
    let dropped = 0;
    Object.keys(groups).forEach(function (name) {
      const docs = groups[name];
      if (docs.length < 2) return;
      docs.sort((a, b) => (a.additionalData && a.additionalData.deviceId ? -1 : 1) - (b.additionalData && b.additionalData.deviceId ? -1 : 1));
      docs.slice(1).forEach(d => { db.module_info.deleteOne({ _id: d._id }); dropped++; });
    });
    if (dropped) print("deduped duplicate-hostname devices: dropped " + dropped + " (kept the one with a hardware deviceId)");
  })();

  // ---- device templates (the existing endpoint-shield devices) ----
  const templates = db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }).toArray()
    .filter(t => t.name && db.api_collections.countDocuments({ hostName: new RegExp("^" + esc(t.name) + "\\.") }) > 0);
  if (!templates.length) { print("ERROR: no device templates with api_collections found"); quit(1); }

  const existingDeviceCount = db.module_info.countDocuments({ moduleType: "MCP_ENDPOINT_SHIELD" });
  const need = Math.max(0, TARGET - existingDeviceCount);
  print("Templates: " + templates.length + " | existing devices: " + existingDeviceCount +
        " | target: " + TARGET + " | to create: " + need);
  if (need === 0) { print("Nothing to do."); return; }

  // NOTE: NHI (nhi_identities / nhi_violations) is generated separately and richly by
  // 04_enrich_nhi.js (global, controlled variety). This script no longer creates NHI.

  const totals = {};
  function bump(k, v) { totals[k] = (totals[k] || 0) + v; }

  // guard against hostname collisions with existing devices (and across this run)
  const usedNames = new Set(db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { name: 1 }).toArray().map(d => d.name));
  // guard against username collisions (the name pool is small; the UI dedupes users by userName)
  const usedUsers = new Set(db.agent_users.find({}, { userName: 1 }).toArray().map(d => d.userName));

  for (let i = 0; i < need; i++) {
    const T = templates[i % templates.length];
    const Tname = T.name;
    const Tcolls = db.api_collections.find({ hostName: new RegExp("^" + esc(Tname) + "\\.") }).toArray();
    const ToldIds = Tcolls.map(c => c._id);
    const ThwId = (T.additionalData && T.additionalData.deviceId) || null;

    // new device identity (regenerate until hostname is unique)
    let hw, first, last, mtype, Dname;
    do {
      hw = hex(32); first = pick(FIRST); last = pick(LAST); mtype = pick(["pro", "air"]);
      Dname = first + "s-macbook-" + mtype + "-" + hw.substring(0, 8);
    } while (usedNames.has(Dname));
    usedNames.add(Dname);
    // unique username (append a counter only on collision, so most stay clean like "michaelphilips")
    let username = (first + last).toLowerCase();
    if (usedUsers.has(username)) { let k = 2; while (usedUsers.has(username + k)) k++; username = username + k; }
    usedUsers.add(username);
    const fullName = first.charAt(0).toUpperCase() + first.slice(1) + " " + last.charAt(0).toUpperCase() + last.slice(1);
    const email = username + "@acme-scale.io";
    const hb = NOW - ri(0, 3 * DAY);

    // collection id map for this device
    const collMap = {};
    const newColls = Tcolls.map(c => {
      const nid = newCollId(); collMap[c._id] = nid;
      const nc = Object.assign({}, c);
      nc._id = nid;
      nc.hostName = rewritePrefix(c.hostName, Tname, Dname);
      nc.startTs = NOW - ri(0, 5 * DAY);
      return nc;
    });
    bump("api_collections", insertChunked("api_collections", newColls));
    const remap = a => (a || []).map(x => (collMap[x] !== undefined ? collMap[x] : x));

    // single_type_info (ObjectId _id -> regenerate)
    let buf = [];
    db.single_type_info.find({ $or: [{ apiCollectionId: { $in: ToldIds } }, { collectionIds: { $in: ToldIds } }] }).forEach(d => {
      const n = Object.assign({}, d); delete n._id;
      if (n.apiCollectionId !== undefined && collMap[n.apiCollectionId] !== undefined) n.apiCollectionId = collMap[n.apiCollectionId];
      if (n.collectionIds) n.collectionIds = remap(n.collectionIds);
      buf.push(n);
    });
    bump("single_type_info", insertChunked("single_type_info", buf));

    // api_info (composite _id -> rebuild apiCollectionId)
    buf = [];
    db.api_info.find({ $or: [{ "_id.apiCollectionId": { $in: ToldIds } }, { collectionIds: { $in: ToldIds } }] }).forEach(d => {
      const n = Object.assign({}, d);
      const id = Object.assign({}, d._id);
      if (id.apiCollectionId !== undefined && collMap[id.apiCollectionId] !== undefined) id.apiCollectionId = collMap[id.apiCollectionId];
      n._id = id;
      if (n.collectionIds) n.collectionIds = remap(n.collectionIds);
      buf.push(n);
    });
    bump("api_info", insertChunked("api_info", buf));

    // sample_data (composite _id -> rebuild)
    buf = [];
    db.sample_data.find({ $or: [{ "_id.apiCollectionId": { $in: ToldIds } }, { collectionIds: { $in: ToldIds } }] }).forEach(d => {
      const n = Object.assign({}, d);
      const id = Object.assign({}, d._id);
      if (id.apiCollectionId !== undefined && collMap[id.apiCollectionId] !== undefined) id.apiCollectionId = collMap[id.apiCollectionId];
      n._id = id;
      if (n.collectionIds) n.collectionIds = remap(n.collectionIds);
      buf.push(n);
    });
    bump("sample_data", insertChunked("sample_data", buf));

    // sensitive_sample_data + traffic_info (usually empty for device colls, handle anyway)
    ["sensitive_sample_data", "traffic_info"].forEach(col => {
      const b = [];
      db.getCollection(col).find({ "_id.apiCollectionId": { $in: ToldIds } }).forEach(d => {
        const n = Object.assign({}, d);
        const id = Object.assign({}, d._id);
        if (id.apiCollectionId !== undefined && collMap[id.apiCollectionId] !== undefined) id.apiCollectionId = collMap[id.apiCollectionId];
        n._id = id;
        b.push(n);
      });
      bump(col, insertChunked(col, b));
    });

    // endpoint_mcp_config (ObjectId _id)
    buf = [];
    db.endpoint_mcp_config.find({ collectionName: new RegExp("^" + esc(Tname) + "\\.") }).forEach(d => {
      const n = Object.assign({}, d); delete n._id;
      n.collectionName = rewritePrefix(d.collectionName, Tname, Dname);
      if (n.tempCollectionName) n.tempCollectionName = rewritePrefix(d.tempCollectionName, Tname, Dname);
      buf.push(n);
    });
    bump("endpoint_mcp_config", insertChunked("endpoint_mcp_config", buf));

    // mcp_audit_info (ObjectId _id) -> remap hostCollectionId
    buf = [];
    db.mcp_audit_info.find({ hostCollectionId: { $in: ToldIds } }).forEach(d => {
      const n = Object.assign({}, d); delete n._id;
      if (collMap[d.hostCollectionId] !== undefined) n.hostCollectionId = collMap[d.hostCollectionId];
      if (typeof n.mcpHost === "string") n.mcpHost = rewritePrefix(n.mcpHost, Tname, Dname);
      if (typeof n.collectionName === "string") n.collectionName = rewritePrefix(n.collectionName, Tname, Dname);
      buf.push(n);
    });
    bump("mcp_audit_info", insertChunked("mcp_audit_info", buf));

    // module_info (device row)
    const M = Object.assign({}, T);
    M._id = uuid();
    M.name = Dname;
    M.currentVersion = pick(VER_POOL);
    M.startedTs = NOW - ri(1, 14) * DAY;
    M.lastHeartbeatReceived = hb;
    M.expiresAt = new Date((NOW + 30 * DAY) * 1000);
    if (T.additionalData) {
      const ad = Object.assign({}, T.additionalData);
      ad.deviceId = hw;
      ad.username = username;
      ad.userFullName = fullName;
      ad.localHostname = fullName.replace(/\s+/g, "-") + "s-MacBook";
      ad.localIP = ip(); ad.publicIP = ip();
      ad.osDisplayName = pick(OS_POOL);
      ad.currentVersion = M.currentVersion; ad.version = M.currentVersion;
      if (T.additionalData.mcpServers) {
        const ms = {};
        for (const k in T.additionalData.mcpServers) {
          const e = Object.assign({}, T.additionalData.mcpServers[k]);
          if (typeof e.collectionName === "string") e.collectionName = rewritePrefix(e.collectionName, Tname, Dname);
          ms[k] = e;
        }
        ad.mcpServers = ms;
      }
      M.additionalData = ad;
    }
    db.module_info.insertOne(M); bump("module_info", 1);

    // agent_users (one fabricated user owning this device)
    db.agent_users.insertOne({
      userName: username, userEmail: email, devices: [Dname],
      lastUpdatedAt: NOW, teamName: pick(TEAMS), userRole: pick(ROLES),
      lastUpdatedBy: "scale-test", roleSource: "manual", teamSource: "manual"
    });
    bump("agent_users", 1);

    // user_analysis_data (per-device analytics)
    const uaTpl = (ThwId && db.user_analysis_data.findOne({ "_id.deviceId": ThwId })) || db.user_analysis_data.findOne({});
    if (uaTpl) {
      const n = Object.assign({}, uaTpl);
      n._id = { serviceId: String(NOW) + "-" + i, deviceId: hw };
      n.userName = username;
      n.lastUpdatedAt = NOW;
      n.totalInputTokens = ri(2000, 60000);
      n.totalOutputTokens = ri(50000, 6000000);
      db.user_analysis_data.insertOne(n); bump("user_analysis_data", 1);
    }

    // NHI is generated globally by 04_enrich_nhi.js (not here).

    if ((i + 1) % 25 === 0 || i + 1 === need) print("  ... created " + (i + 1) + "/" + need + " devices");
  }

  print("\n==== inserted doc totals ====");
  Object.keys(totals).sort().forEach(k => print("  " + k + ": " + totals[k]));
  print("Total devices now: " + db.module_info.countDocuments({ moduleType: "MCP_ENDPOINT_SHIELD" }));
})();
