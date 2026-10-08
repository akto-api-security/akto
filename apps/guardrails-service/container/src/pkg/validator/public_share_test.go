package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

func TestPublicShareVerdict(t *testing.T) {
	const chatShare = "/api/organizations/e68d326b-0acb-4573-85a6-4ed867827c96/chat_conversations/11111111-2222-3333-4444-555555555555/share"
	const artifactPerm = "/api/frame/perm/11111111-2222-3333-4444-555555555555"
	const artifactInvite = "/api/frame/invite/11111111-2222-3333-4444-555555555555"

	cases := []struct {
		name    string
		path    string
		method  string
		payload string
		wantApp string
		want    bool
	}{
		{"public chat share blocked", chatShare, "POST", `{"visibility":"public"}`, "claude", true},
		{"public artifact perm blocked", artifactPerm, "PATCH", `{"read":{"mode":"public"}}`, "claude", true},
		{"org chat share allowed", chatShare, "POST", `{"visibility":"org"}`, "claude", false},
		{"invite-only chat share with emails allowed", chatShare, "POST", `{"visibility":"invite_only","emails":["x@example.com"]}`, "claude", false},
		{"artifact perm owner allowed", artifactPerm, "PATCH", `{"read":{"mode":"owner"}}`, "claude", false},
		{"artifact perm users allowed", artifactPerm, "PATCH", `{"read":{"mode":"users"},"orgReadLevel":0}`, "claude", false},
		{"artifact invite never matched by this check", artifactInvite, "POST", `{"email":"x@example.com"}`, "", false},
		{"query string stripped before match", chatShare + "?foo=bar", "POST", `{"visibility":"public"}`, "claude", true},
		{"GET is never a share request", chatShare, "GET", `{"visibility":"public"}`, "", false},
		{"unrelated path never matched", "/api/organizations/o/chat_conversations/x/completion", "POST", `{"visibility":"public"}`, "", false},
		{"empty body allowed", chatShare, "POST", ``, "claude", false},
		{"malformed json allowed", chatShare, "POST", `not json`, "claude", false},
		// Real ENDPOINT-sourced traffic carries a synthetic device host, not "claude.ai" — this
		// check is path/method-only on purpose, see the comment on shareEndpoint.
		{"host is not part of the match at all", chatShare, "POST", `{"visibility":"public"}`, "claude", true},
	}

	for _, c := range cases {
		gotApp, got := publicShareVerdict(c.path, c.method, c.payload)
		if got != c.want || gotApp != c.wantApp {
			t.Errorf("%s: publicShareVerdict(%q,%q,%q) = (%q,%v), want (%q,%v)", c.name, c.path, c.method, c.payload, gotApp, got, c.wantApp, c.want)
		}
	}
}

func TestPublicSharePolicyName(t *testing.T) {
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "no-toggle"}},
		{Info: types.PolicyInfo{Name: "share-guard"}, BlockPublicShare: true},
	}
	name, ok := publicSharePolicyName(policies)
	if !ok || name != "share-guard" {
		t.Errorf("got (%q, %v), want (\"share-guard\", true)", name, ok)
	}

	if name, ok := publicSharePolicyName([]types.Policy{{Info: types.PolicyInfo{Name: "no-toggle"}}}); ok {
		t.Errorf("expected no policy name with toggle off, got (%q, %v)", name, ok)
	}
}
