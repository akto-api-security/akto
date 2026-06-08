package validator

// All blocked-host logic (pattern compilation, glob matching, vendor-alias token matching,
// browser-extension / endpoint detection) lives in the mcp library.
// This file is intentionally empty — see:
//   mcp.CompileBlockedHostRules  — build compiled rules from []types.Policy
//   mcp.CheckBlockedHosts        — evaluate a request against compiled rules
//   mcp.BlockedHostPatterns      — extract patterns for logging
