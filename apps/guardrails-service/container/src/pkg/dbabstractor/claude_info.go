package dbabstractor

import (
	"encoding/json"
	"fmt"
)

// ClaudeDesktopInfo is one Claude surface's resolved login on one device, as returned by
// cyborg's /fetchDeviceClaudeDesktopInfoMap. Mirrors com.akto.dto.claude_identity.ClaudeDesktopInfo.
//
// Only OrganizationUUID is read during policy matching; the rest is carried for logging and for
// consumers that want to explain a match.
type ClaudeDesktopInfo struct {
	AgentType        string `json:"agentType,omitempty"`
	Email            string `json:"email,omitempty"`
	EmailCategory    string `json:"emailCategory,omitempty"`
	AccountType      string `json:"accountType,omitempty"`
	AccountUUID      string `json:"accountUuid,omitempty"`
	OrganizationUUID string `json:"organizationUuid,omitempty"`
	LoggedIn         bool   `json:"loggedIn,omitempty"`
	Source           string `json:"source,omitempty"`
}

// FetchDeviceClaudeInfoMap returns deviceLabel -> agentType -> that surface's login.
//
// Nested because one device runs several Claude surfaces that authenticate separately: Desktop
// and the CLI hold their own tokens and can sit in different orgs at the same moment, so a flat
// device -> org map would have to pick one arbitrarily.
func (c *Client) FetchDeviceClaudeInfoMap() (map[string]map[string]ClaudeDesktopInfo, error) {
	raw, err := c.SendRequest("POST", "/fetchDeviceClaudeDesktopInfoMap", map[string]any{})
	if err != nil {
		return nil, fmt.Errorf("fetchDeviceClaudeDesktopInfoMap: %w", err)
	}
	// Cyborg may answer with the bare map or a Struts-wrapped object; dispatch on the shape
	// rather than assuming, the same way FetchFileInspectionResults does.
	var wrapped struct {
		DeviceClaudeDesktopInfoMap map[string]map[string]ClaudeDesktopInfo `json:"deviceClaudeDesktopInfoMap"`
	}
	if err := json.Unmarshal(raw, &wrapped); err == nil && wrapped.DeviceClaudeDesktopInfoMap != nil {
		return wrapped.DeviceClaudeDesktopInfoMap, nil
	}
	var bare map[string]map[string]ClaudeDesktopInfo
	if err := json.Unmarshal(raw, &bare); err != nil {
		return nil, fmt.Errorf("fetchDeviceClaudeDesktopInfoMap: parse: %w (body=%s)", err, truncate(raw, 200))
	}
	return bare, nil
}
