// Rebuild the "Users and devices" data from the current device roster, idempotently:
//   * exactly ONE agent_user per endpoint-shield device (so UI user count == device count)
//   * guaranteed-unique userName (the name pool is small; the UI dedupes by userName)
//   * drops base/orphan users copied from the source account (the devices:null team accounts)
//   * aligns module_info.additionalData.username + user_analysis_data.userName to the same unique name
// Re-runnable: it wipes agent_users and regenerates from module_info each time.
// After running, re-run ./traces.sh load and ./threat.sh scale so the usernames propagate to ES + threat.
(function () {
  const now = Math.floor(Date.now() / 1000);
  const TEAMS = ["Engineering", "IT Support", "Security", "Data Science", "Platform", "Product",
    "DevOps", "Finance", "Sales Engineering", "Research"];
  const ROLES = ["Member", "Member", "Lead", "Admin", "Contractor", "Manager"];

  const wiped = db.agent_users.deleteMany({}).deletedCount;   // removes fabricated + base/orphan users
  print("wiped agent_users: " + wiped);

  const used = new Set();
  let docs = [], n = 0, renamed = 0, inserted = 0;
  function flush() { if (docs.length) { db.agent_users.insertMany(docs, { ordered: false }); inserted += docs.length; docs = []; } }

  db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }).forEach(function (dev) {
    const host = dev.name;
    const hw = dev.additionalData && dev.additionalData.deviceId;
    let uname = (dev.additionalData && dev.additionalData.username) || host.split("-")[0];
    const orig = uname;
    if (used.has(uname)) { let k = 2; while (used.has(uname + k)) k++; uname = uname + k; }
    used.add(uname);
    if (uname !== orig) {
      renamed++;
      db.module_info.updateOne({ _id: dev._id }, { $set: { "additionalData.username": uname } });
    }
    if (hw) db.user_analysis_data.updateMany({ "_id.deviceId": hw }, { $set: { userName: uname } });
    docs.push({
      userName: uname, userEmail: uname + "@acme-scale.io", devices: [host], lastUpdatedAt: now,
      teamName: TEAMS[n % TEAMS.length], userRole: ROLES[n % ROLES.length],
      lastUpdatedBy: "scale-test", roleSource: "manual", teamSource: "manual"
    });
    n++;
    if (docs.length >= 2000) flush();
  });
  flush();

  // prune orphan user_analysis_data (base entries whose deviceId belongs to no current device)
  const liveHw = new Set(db.module_info.find({ moduleType: "MCP_ENDPOINT_SHIELD" }, { "additionalData.deviceId": 1 })
    .toArray().map(d => d.additionalData && d.additionalData.deviceId).filter(Boolean));
  let orphan = [];
  db.user_analysis_data.find({}, { _id: 1 }).forEach(function (d) {
    if (!liveHw.has(d._id && d._id.deviceId)) orphan.push(d._id);
  });
  let delUa = 0;
  for (let i = 0; i < orphan.length; i += 2000) delUa += db.user_analysis_data.deleteMany({ _id: { $in: orphan.slice(i, i + 2000) } }).deletedCount;

  const total = db.agent_users.countDocuments({});
  const distinct = db.agent_users.distinct("userName").length;
  print("rebuilt agent_users: " + inserted + " (one per device) | renamed-for-uniqueness: " + renamed);
  print("agent_users total=" + total + " distinctUserName=" + distinct + (total === distinct ? "  OK (unique)" : "  DUP!"));
  print("null-device users: " + db.agent_users.countDocuments({ $or: [{ devices: null }, { devices: { $size: 0 } }] }));
  print("pruned orphan user_analysis_data: " + delUa + " | remaining: " + db.user_analysis_data.countDocuments({}));
})();
