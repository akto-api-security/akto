// Coverage report for the NEW_ACCT database. Connected to NEW_ACCT db.
(function () {
  function c(col, q) { try { return db.getCollection(col).countDocuments(q || {}); } catch (e) { return "?"; } }

  print("==================== COVERAGE REPORT (db " + db.getName() + ") ====================");
  const devices = c("module_info", { moduleType: "MCP_ENDPOINT_SHIELD" });
  print("Endpoint-shield devices (module_info): " + devices);

  // distinct device hostnames from api_collections
  const devs = {};
  db.api_collections.find({ hostName: { $exists: true } }, { hostName: 1 }).forEach(d => {
    const p = (d.hostName || "").split(".")[0]; if (p) devs[p] = 1;
  });
  print("Distinct api_collections host prefixes: " + Object.keys(devs).length);

  print("\n-- device-keyed collection counts --");
  ["api_collections","single_type_info","api_info","sample_data","sensitive_sample_data","traffic_info",
   "endpoint_mcp_config","mcp_audit_info","module_info","agent_users","user_analysis_data",
   "nhi_identities","nhi_violations","nhi_policies"].forEach(col => print("  " + col + ": " + c(col)));

  print("\n-- NHI breakdown --");
  print("  nhi_identities device-linked: " + c("nhi_identities", { deviceId: { $exists: true } }));
  print("  nhi_identities with violations: " + c("nhi_identities", { relatedViolationIds: { $exists: true, $ne: [] } }));

  // spot-check one fabricated device end-to-end (acme-scale user => fabricated)
  const fab = db.agent_users.findOne({ userEmail: /@acme-scale\.io$/ });
  if (fab) {
    const dn = fab.devices && fab.devices[0];
    print("\n-- spot-check fabricated device: " + dn + " (user " + fab.userName + ") --");
    const mi = db.module_info.findOne({ name: dn });
    print("  module_info: " + (mi ? "yes (agentId " + mi._id + ", hw " + (mi.additionalData && mi.additionalData.deviceId) + ")" : "MISSING"));
    const colls = db.api_collections.find({ hostName: new RegExp("^" + dn.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\.") }).toArray();
    const ids = colls.map(x => x._id);
    print("  api_collections: " + colls.length);
    print("  single_type_info: " + c("single_type_info", { apiCollectionId: { $in: ids } }));
    print("  api_info: " + c("api_info", { "_id.apiCollectionId": { $in: ids } }));
    print("  sample_data: " + c("sample_data", { "_id.apiCollectionId": { $in: ids } }));
    print("  mcp_audit_info: " + c("mcp_audit_info", { hostCollectionId: { $in: ids } }));
    print("  nhi_identities: " + c("nhi_identities", { deviceLabel: dn }));
    print("  user_analysis_data: " + c("user_analysis_data", { "_id.deviceId": mi && mi.additionalData && mi.additionalData.deviceId }));
  } else {
    print("\n(no fabricated device found yet)");
  }
  print("==============================================================");
})();
