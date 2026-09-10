package models

// PendingApprovalResponse replaces the raw blocked mcp.ValidationResult for a
// "human_approval" verdict. Callers re-call the same endpoint with only ActivityID set to
// check the decision.
type PendingApprovalResponse struct {
	Allowed    bool   `json:"allowed"`
	Behaviour  string `json:"behaviour"`
	Status     string `json:"status"` // "pending"
	ActivityID string `json:"activityId"`
	Reason     string `json:"reason,omitempty"`
	Modified   bool   `json:"modified"`
}

// ApprovalPollResponse is the response to a poll-by-activityId request. Never re-runs
// policy evaluation — a pure lookup of the pending activity's current decision.
type ApprovalPollResponse struct {
	Allowed    bool   `json:"allowed"`
	Behaviour  string `json:"behaviour"`
	Status     string `json:"status"` // "pending" | "approved" | "blocked"
	ActivityID string `json:"activityId"`
	Reason     string `json:"reason,omitempty"`
}
