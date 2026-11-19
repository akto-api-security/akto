package validator

import "github.com/akto-api-security/akto-endpoint-shield/mcp"

// IsPassiveBehaviour reports whether a not-allowed verdict only asks to be recorded rather
// than enforced. The engine reports the threat itself during validation, so honouring a
// passive behaviour drops the block, never the alert.
//
// Listed explicitly rather than derived from mcp.Behaviour.Enforces(): that predicate also
// ranks "warn" and the approval behaviours below blocking, and neither is safe to wave
// through. Everything unlisted — including an empty or unknown behaviour — keeps blocking.
//
// Shared by every enforcement point that has to tell "stop this" from "write it down":
// /api/validate/file's chunkStopsFile and the browser-attachment verdict upgrade.
func IsPassiveBehaviour(behaviour string) bool {
	switch mcp.ParseBehaviour(behaviour) {
	case mcp.BehaviourAlert, mcp.BehaviourMask:
		return true
	default:
		return false
	}
}
