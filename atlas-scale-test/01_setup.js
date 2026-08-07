// Registers the new scale-test account so it appears in the dashboard switcher.
// Config injected by run.sh header: NEW_ACCT (number), ACCT_NAME (string), SRC_ACCT (number)
// Runs connected to any db; uses getSiblingDB for common/billing.
(function () {
  const common = db.getSiblingDB("common");
  const billing = db.getSiblingDB("billing");

  // Auto-detect the human user + org from existing data (fallback to rakshak/Akto).
  const user = common.users.findOne({ login: "rakshak@akto.io" }) || common.users.findOne({});
  if (!user) { print("ERROR: no user found in common.users"); quit(1); }
  const org = billing.organizations.findOne({ accounts: SRC_ACCT }) ||
              billing.organizations.findOne({ adminEmail: user.login }) ||
              billing.organizations.findOne({});
  if (!org) { print("ERROR: no organization found in billing.organizations"); quit(1); }

  // Clone flags from the source account row so behaviour matches (hybridSaas etc.)
  const srcAcct = common.accounts.findOne({ _id: SRC_ACCT }) || {};

  common.accounts.updateOne(
    { _id: NEW_ACCT },
    { $set: {
        _id: NEW_ACCT,
        name: ACCT_NAME,
        "default": false,
        hybridSaasAccount: srcAcct.hybridSaasAccount === true,
        hybridTestingEnabled: false,
        inactive: false,
        mergingInitiateTs: 0,
        mergingRunning: false,
        statusChangeTimestamp: 0,
        timezone: srcAcct.timezone || "Asia/Kolkata"
    } },
    { upsert: true }
  );

  common.users.updateOne(
    { _id: user._id },
    { $set: { ["accounts." + NEW_ACCT]: { accountId: NEW_ACCT, "default": false, name: ACCT_NAME } } }
  );

  if (common.rbac.countDocuments({ accountId: NEW_ACCT, userId: user._id }) === 0) {
    common.rbac.insertOne({
      _t: "com.akto.dto.RBAC",
      accountId: NEW_ACCT,
      apiCollectionsId: [],
      role: "ADMIN",
      userId: user._id
    });
  }

  billing.organizations.updateOne({ _id: org._id }, { $addToSet: { accounts: NEW_ACCT } });

  print("Registered account " + NEW_ACCT + " ('" + ACCT_NAME + "') for user " + user.login +
        " (userId " + user._id + ") in org " + org._id);
})();
