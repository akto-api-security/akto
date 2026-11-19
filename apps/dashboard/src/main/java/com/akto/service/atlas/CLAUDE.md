# Atlas agentic discovery (Overview)

Overview page for discovered AI tools: tiles, "Needs a decision", adoption, matrices, supply chain, approve / block.

## Request flow

```
AgenticDiscoveryOverview.jsx → discoveryApi → AtlasDiscoveryAction (serve)
    → AgenticObserveAction.fetchAtlasToolRows   # one row per classified group (shared 60s classification cache)
    → AtlasAssetStatusService.loadDecisions     # mcp_audit_info, paged by _id, cached 60s, throws on failure
    → AtlasPeopleSource.load                    # allowlist + user teams, cached 60s, throws on failure
    → device metadata (ModuleInfoDao)           # device -> username, cached 60s
    → AtlasDiscoveryService                     # pure sections; one failing section only blanks its own card
```

## Status rules (match the Audit page and the AI inventory)

- Decisions are `mcp_audit_info` rows. A tool with none is Unapproved; a vendor on the allowlist is Approved, never a flagged one.
- Rows rank like `AuditDataAction.remarksPriority`: rejected, active conditional, approved, expired conditional (shows as blocked), under review, pending. The lowest rank wins.
- "Under review" is `remarks: "Under review"`. It never unblocks, never cascades to tool rows and never stamps `blockAll`.
- Approve / block write global rows only; scoped rows belong to the Audit page.

## Decisions

- **Slim people source, not InsightDataBundle.** The bundle swallows read failures into empty sets (every tool then reads Unapproved), loads ~13 sources and runs I/O under a lock. `AtlasPeopleSource` reads only the allowlist and team tags and throws on failure, so a failed read is never cached as "none". `InsightVendorNames` only exposes `InsightUtil.canonicalVendorName`.
- **Failures stay visible.** A failed section is listed in `unavailableSources`; a failed host lookup for a tool's violations is an error, not "no events"; the flyout shows "unavailable" instead of hiding the section.
- **Collection restriction** (`ArgusCollectionScope`) only applies in Argus contexts, like the Devices & employees endpoints; nobody is restricted in Endpoint security.
- **Classification cache** is the AI inventory's, keyed by account. Atlas passes an empty sensitive map like the other server-side callers, so a build by Atlas has no sensitive types for up to 60s.
- **Device metadata cache** is local because the inventory's is private to `AgenticObserveAction`.
- **No unique index on decision rows.** Two concurrent first-time decisions can insert two rows; both carry the same remarks and the lowest rank wins, so the status is unchanged.

## Tests

`TestAtlasDiscoveryService`, `TestAtlasPeopleSource` and `TestTtlCache` cover the rules, the failure isolation of sections and the cache. There are no JS tests.
