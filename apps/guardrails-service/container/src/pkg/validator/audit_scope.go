package validator

import (
	"encoding/json"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/session"
)

// auditScope is who an Audit Data block applies to (resolved by cyborg); nothing set means everyone.
type auditScope struct {
	ApplyToDeviceIds       []string             `json:"applyToDeviceIds"`
	ApplyToUserEmails      []string             `json:"applyToUserEmails"`
	UserMetadata           []types.AgenticUsers `json:"userMetadata"`
	NegatedTargetUserNames bool                 `json:"negatedTargetUserNames"`
}

// matches follows filterPoliciesByDevice: a device / tag-resolved email match, or the picked users.
func (sc auditScope) matches(deviceLabel, email string) bool {
	if sc.ApplyToDeviceIds == nil && sc.ApplyToUserEmails == nil && len(sc.UserMetadata) == 0 {
		return true
	}
	if deviceIDsContain(sc.ApplyToDeviceIds, deviceLabel) || emailsContain(sc.ApplyToUserEmails, email) {
		return true
	}
	if len(sc.UserMetadata) == 0 {
		return false
	}
	picked := email != "" && findUserMetadataByEmail(sc.UserMetadata, email) != nil
	return picked != sc.NegatedTargetUserNames
}

// rejectedAgent is an AI agent blocked in Audit Data: its row name and who the block applies to.
type rejectedAgent struct {
	row   string
	scope auditScope
}

// auditDecisions holds the browser LLM sites and AI agents blocked in Audit Data.
type auditDecisions struct {
	llmSites map[string]auditScope    // site -> scope
	agents   map[string]rejectedAgent // agent name (tag value / host segment) -> row
}

// rejectedAuditDecisions reads Rejected browser-llm / ai-agent rows, which types.AuditPolicy can't hold.
func rejectedAuditDecisions(rawAuditPolicies []byte) auditDecisions {
	decisions := auditDecisions{llmSites: make(map[string]auditScope), agents: make(map[string]rejectedAgent)}
	var resp struct {
		McpAuditInfoList []struct {
			Type         string   `json:"type"`
			ResourceName string   `json:"resourceName"`
			Remarks      string   `json:"remarks"`
			RawValues    []string `json:"rawValues"`
			auditScope
		} `json:"mcpAuditInfoList"`
	}
	if err := json.Unmarshal(rawAuditPolicies, &resp); err != nil {
		return decisions
	}
	for _, r := range resp.McpAuditInfoList {
		if !strings.EqualFold(strings.TrimSpace(r.Remarks), "rejected") {
			continue
		}
		switch {
		case strings.EqualFold(r.Type, auditTypeBrowserLLM):
			if site := strings.ToLower(strings.TrimSpace(r.ResourceName)); site != "" {
				decisions.llmSites[site] = r.auditScope
			}
		case strings.EqualFold(r.Type, auditTypeAIAgent):
			for _, v := range r.RawValues {
				if v = canonicalServerToken(strings.TrimSpace(v)); v != "" {
					decisions.agents[v] = rejectedAgent{row: r.ResourceName, scope: r.auditScope}
				}
			}
		}
	}
	return decisions
}

// requestUserEmail is the installer's user email header, else the browser extension's browser-user-email tag.
func requestUserEmail(valCtx *mcp.ValidationContext) string {
	if email := session.ExtractInstallerUserEmail(valCtx.RequestHeaders); email != "" {
		return email
	}
	return browserUserEmail(valCtx.Tag)
}

// emailsContain reports whether emails holds email, case-insensitively; an empty email never matches.
func emailsContain(emails []string, email string) bool {
	if email == "" {
		return false
	}
	for _, e := range emails {
		if strings.EqualFold(e, email) {
			return true
		}
	}
	return false
}
