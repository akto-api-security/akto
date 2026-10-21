package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

func TestAuditScopeMatches(t *testing.T) {
	jane := []types.AgenticUsers{{UserName: "janedoe", UserEmail: "jane.doe@example.com"}}
	cases := []struct {
		name   string
		scope  auditScope
		device string
		email  string
		want   bool
	}{
		{"no scope blocks everyone", auditScope{}, "johnsmith-pc", "", true},
		{"device in scope", auditScope{ApplyToDeviceIds: []string{"JaneDoe-Mac"}}, "janedoe-mac", "", true},
		{"device out of scope", auditScope{ApplyToDeviceIds: []string{"janedoe-mac"}}, "johnsmith-pc", "john@example.com", false},
		{"tag resolved to no one", auditScope{ApplyToDeviceIds: []string{}, ApplyToUserEmails: []string{}}, "janedoe-mac", "jane.doe@example.com", false},
		{"team email (browser)", auditScope{ApplyToDeviceIds: []string{}, ApplyToUserEmails: []string{"jane.doe@example.com"}}, "janedoe", "Jane.Doe@example.com", true},
		{"picked user", auditScope{UserMetadata: jane}, "", "jane.doe@example.com", true},
		{"other user", auditScope{UserMetadata: jane}, "", "john@example.com", false},
		{"excluded user", auditScope{UserMetadata: jane, NegatedTargetUserNames: true}, "", "jane.doe@example.com", false},
		{"everyone but the excluded user", auditScope{UserMetadata: jane, NegatedTargetUserNames: true}, "", "john@example.com", true},
		{"unidentified request under an exclude list", auditScope{UserMetadata: jane, NegatedTargetUserNames: true}, "", "", true},
	}
	for _, c := range cases {
		if got := c.scope.matches(c.device, c.email); got != c.want {
			t.Errorf("%s: matches(%q, %q) = %v, want %v", c.name, c.device, c.email, got, c.want)
		}
	}
}

func TestRejectedAuditDecisionsScope(t *testing.T) {
	d := rejectedAuditDecisions([]byte(`{"mcpAuditInfoList":[
		{"type":"ai-agent","resourceName":"claude2","remarks":"Rejected","rawValues":["claudecli"],"applyToDeviceIds":["janedoe-mac"]},
		{"type":"browser-llm","resourceName":"chatgpt.com","remarks":"Rejected","userMetadata":[{"userName":"janedoe","userEmail":"jane.doe@example.com"}]}
	]}`))
	if !d.agents["claudecli"].scope.matches("janedoe-mac", "") || d.agents["claudecli"].scope.matches("johnsmith-pc", "") {
		t.Errorf("agent scope not parsed: %+v", d.agents["claudecli"])
	}
	if !d.llmSites["chatgpt.com"].matches("janedoe", "jane.doe@example.com") || d.llmSites["chatgpt.com"].matches("john", "john@example.com") {
		t.Errorf("llm scope not parsed: %+v", d.llmSites["chatgpt.com"])
	}
}

func TestRequestUserEmail(t *testing.T) {
	header := map[string]string{"x-akto-installer-user_email": "jane.doe@example.com"}
	tag := `{"browser-user-email":"john@example.com"}`
	cases := []struct {
		name    string
		headers map[string]string
		tag     string
		want    string
	}{
		{"installer header wins", header, tag, "jane.doe@example.com"},
		{"browser tag fallback", map[string]string{}, tag, "john@example.com"},
		{"neither", map[string]string{}, `{}`, ""},
	}
	for _, c := range cases {
		if got := requestUserEmail(&mcp.ValidationContext{RequestHeaders: c.headers, Tag: c.tag}); got != c.want {
			t.Errorf("%s: requestUserEmail = %q, want %q", c.name, got, c.want)
		}
	}
}
