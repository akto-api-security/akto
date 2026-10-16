package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

func TestRejectedLLMSites(t *testing.T) {
	audit := map[string]*types.AuditPolicy{
		"a": {Type: "browser-llm", ResourceName: "Gemini.google.com", Remarks: "Rejected"},
		"b": {Type: "browser-llm", ResourceName: "chatgpt.com", Remarks: "Approved"},
		"c": {Type: "mcp-server", ResourceName: "claude.ai", Remarks: "Rejected"},
		"d": nil,
	}
	sites := rejectedLLMSites(audit)
	if len(sites) != 1 {
		t.Fatalf("expected only gemini.google.com, got %v", sites)
	}

	sites["chatgpt.com"] = struct{}{}
	sites["janedoe.chrome.co.uk"] = struct{}{} // row discovery kept whole (third label is a TLD)
	cases := []struct {
		host string
		want bool
	}{
		{"janedoe.chrome.gemini.google.com", true},
		{"gemini.google.com", true},
		{"JANEDOE.chrome.GEMINI.google.com", true},
		{"janedoe.chrome.chatgpt.com", true},
		{"chatgpt.com", true},
		{"janedoe.chrome.co.uk", true},
		{"janedoe.chrome.chat.chatgpt.com", false}, // subdomain has its own row
		{"janedoe.chrome.google.com", false},       // parent of a blocked site
		{"janedoe.chrome.notchatgpt.com", false},
		{"janedoe.chrome.claude.ai", false},
		{"", false},
	}
	for _, c := range cases {
		if _, got := matchRejectedLLMSite(c.host, sites); got != c.want {
			t.Errorf("matchRejectedLLMSite(%q) = %v, want %v", c.host, got, c.want)
		}
	}
}
