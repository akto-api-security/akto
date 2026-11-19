package validator

import (
	"testing"
)

func TestRejectedLLMSites(t *testing.T) {
	sites := rejectedAuditDecisions([]byte(`{"mcpAuditInfoList":[
		{"type":"browser-llm","resourceName":"Gemini.google.com","remarks":"Rejected"},
		{"type":"browser-llm","resourceName":"chatgpt.com","remarks":"Approved"},
		{"type":"mcp-server","resourceName":"claude.ai","remarks":"Rejected"}
	]}`)).llmSites
	if len(sites) != 1 {
		t.Fatalf("expected only gemini.google.com, got %v", sites)
	}

	sites["chatgpt.com"] = auditScope{}
	sites["janedoe.chrome.co.uk"] = auditScope{} // row discovery kept whole (third label is a TLD)
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
