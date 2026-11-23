package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"go.uber.org/zap/zaptest/observer"
)

func TestSkillNameFromPayload(t *testing.T) {
	cases := []struct {
		name    string
		payload string
		want    string
	}{
		{"skill call", `{"body":{"skill":"aws-credential-helper"},"toolName":"Skill"}`, "aws-credential-helper"},
		{"tool name any case", `{"body":{"skill":"docx"},"toolName":"skill"}`, "docx"},
		{"trims whitespace", `{"body":{"skill":"  docx "},"toolName":"Skill"}`, "docx"},
		{"skill with args", `{"body":{"skill":"loop","args":"5m /foo"},"toolName":"Skill"}`, "loop"},
		{"other tool", `{"body":{"command":"ls"},"toolName":"Bash"}`, ""},
		{"other tool with skill-like field", `{"body":{"skill":"x"},"toolName":"Bash"}`, ""},
		{"no tool name", `{"body":{"skill":"x"}}`, ""},
		{"mcp jsonrpc", `{"jsonrpc":"2.0","method":"tools/call","params":{"name":"x"}}`, ""},
		{"empty skill", `{"body":{},"toolName":"Skill"}`, ""},
		{"not json", `not json`, ""},
		{"empty", ``, ""},
	}
	for _, c := range cases {
		if got := skillNameFromPayload(c.payload); got != c.want {
			t.Errorf("%s: skillNameFromPayload = %q, want %q", c.name, got, c.want)
		}
	}
}

func TestSkillAgentKeyAndHost(t *testing.T) {
	for in, want := range map[string]string{
		"claude": "claude", "claudecli": "claude", "claude-cli-user": "claude", "CLAUDE_DESKTOP": "claude",
		"cursor": "cursor", "github_cli": "github-cli", "": "",
	} {
		if got := skillAgentKey(in); got != want {
			t.Errorf("skillAgentKey(%q) = %q, want %q", in, got, want)
		}
	}
	for host, want := range map[string]string{
		"janedoe-mac.ai-agent.claudecli": "claudecli",
		"JANEDOE-MAC.AI-AGENT.CLAUDECLI": "claudecli",
		"janedoe-mac.cursor.some-server": "cursor",
		"claudecli":                      "",
		"":                               "",
	} {
		if got := agentFromHost(host); got != want {
			t.Errorf("agentFromHost(%q) = %q, want %q", host, got, want)
		}
	}
}

func TestRejectedSkills(t *testing.T) {
	raw := []byte(`{"mcpAuditInfoList":[
		{"type":"AGENT_SKILL","resourceName":"aws-credential-helper","mcpHost":"claude","remarks":"Rejected"},
		{"type":"AGENT_SKILL","resourceName":"Docx","mcpHost":"claude","remarks":"Approved"},
		{"type":"AGENT_SKILL","resourceName":"only-cursor","mcpHost":"cursor","remarks":"Rejected"},
		{"type":"AGENT_SKILL","resourceName":"scoped","mcpHost":"claude","remarks":"Rejected","applyToDeviceIds":["dev-1"]},
		{"type":"AGENT_SKILL","resourceName":"by-email","mcpHost":"claude","remarks":"Rejected","applyToUserEmails":["a@x.io"]},
		{"type":"AGENT_SKILL","resourceName":"any-agent","mcpHost":"","remarks":"Rejected"},
		{"type":"AGENT_SKILL","resourceName":"pdf","mcpHost":"claude","remarks":"Conditionally Approved"},
		{"type":"ai-agent","resourceName":"claude2","remarks":"Rejected","rawValues":["claude"]},
		{"type":"browser-llm","resourceName":"chatgpt.com","remarks":"Rejected"}
	]}`)
	skills := rejectedAuditDecisions(raw).skills
	if len(skills) != 5 {
		t.Fatalf("expected 5 rejected skill names, got %v", skills)
	}

	cases := []struct {
		name   string
		skill  string
		agent  string
		device string
		email  string
		want   bool
	}{
		{"rejected for claude cli host", "aws-credential-helper", "claudecli", "d", "", true},
		{"skill name case-insensitive", "AWS-Credential-Helper", "claudecli", "d", "", true},
		{"agent segment spelling variants", "aws-credential-helper", "claude-cli-user", "d", "", true},
		{"approved row is not a block", "docx", "claudecli", "d", "", false},
		{"conditionally approved is not a block", "pdf", "claudecli", "d", "", false},
		{"unknown skill", "something-else", "claudecli", "d", "", false},
		{"row recorded for another agent", "only-cursor", "claudecli", "d", "", false},
		{"same skill on its own agent", "only-cursor", "cursor", "d", "", true},
		{"row with no agent applies to all", "any-agent", "claudecli", "d", "", true},
		{"plugin prefix falls back to bare name", "anthropic-skills:aws-credential-helper", "claudecli", "d", "", true},
		{"device-scoped, matching device", "scoped", "claudecli", "dev-1", "", true},
		{"device-scoped, other device", "scoped", "claudecli", "dev-2", "", false},
		{"email-scoped, matching email", "by-email", "claudecli", "d", "A@x.io", true},
		{"email-scoped, other email", "by-email", "claudecli", "d", "b@x.io", false},
		{"email-scoped, no email", "by-email", "claudecli", "d", "", false},
		{"empty skill name", "", "claudecli", "d", "", false},
	}
	for _, c := range cases {
		if _, got := matchRejectedSkill(skills, c.skill, c.agent, c.device, c.email); got != c.want {
			t.Errorf("%s: matchRejectedSkill(%q, agent=%q, device=%q, email=%q) = %v, want %v",
				c.name, c.skill, c.agent, c.device, c.email, got, c.want)
		}
	}

	if got := rejectedAuditDecisions([]byte("not json")).skills; len(got) != 0 {
		t.Errorf("expected empty map on bad json, got %v", got)
	}
	if _, ok := matchRejectedSkill(nil, "aws-credential-helper", "claudecli", "d", ""); ok {
		t.Error("nil rejected-skill map must never match")
	}
}

// TestCheckRejectedSkill runs the enforcement path with the exact request the Claude hook sends.
func TestCheckRejectedSkill(t *testing.T) {
	skipThreat := true // keep the test off the network: reportAndBlock would otherwise report a threat
	svc := &Service{
		logger: zap.NewNop(),
		cache: &policyCache{rejectedSkills: rejectedAuditDecisions([]byte(`{"mcpAuditInfoList":[
			{"type":"AGENT_SKILL","resourceName":"aws-credential-helper","mcpHost":"claude","remarks":"Rejected"},
			{"type":"AGENT_SKILL","resourceName":"by-email","mcpHost":"claude","remarks":"Rejected","applyToUserEmails":["a@x.io"]}
		]}`)).skills},
	}
	const host = "rahul-s-macbook-pro--2--be4f9b91.ai-agent.claudecli"
	call := func(payload, email string) *mcp.ValidationResult {
		hdr := map[string]string{"host": host}
		if email != "" {
			hdr["x-akto-installer-user_email"] = email
		}
		params := &models.ValidateRequestParams{Path: "/tool/Skill", Method: "POST", RequestPayload: payload, SkipThreat: &skipThreat}
		return svc.checkRejectedSkill(params, &mcp.ValidationContext{McpServerName: host, RequestHeaders: hdr}, payload, "sess", "req")
	}

	blocked := call(`{"body":{"skill":"aws-credential-helper"},"toolName":"Skill"}`, "rahul@akto.io")
	if blocked == nil {
		t.Fatal("rejected skill must be blocked")
	}
	if blocked.Allowed || blocked.Behaviour != "block" || blocked.Reason == "" {
		t.Errorf("unexpected block result: %+v", blocked)
	}
	if blocked.Metadata.RuleViolated != "SkillAccessRejected" {
		t.Errorf("RuleViolated = %q", blocked.Metadata.RuleViolated)
	}

	for name, c := range map[string]struct{ payload, email string }{
		"skill not rejected":       {`{"body":{"skill":"docx"},"toolName":"Skill"}`, ""},
		"non-skill tool":           {`{"body":{"command":"aws s3 ls"},"toolName":"Bash"}`, ""},
		"rejected name in Bash":    {`{"body":{"skill":"aws-credential-helper"},"toolName":"Bash"}`, ""},
		"email-scoped, other user": {`{"body":{"skill":"by-email"},"toolName":"Skill"}`, "b@x.io"},
	} {
		if got := call(c.payload, c.email); got != nil {
			t.Errorf("%s: expected no block, got %+v", name, got)
		}
	}
	if call(`{"body":{"skill":"by-email"},"toolName":"Skill"}`, "a@x.io") == nil {
		t.Error("email-scoped skill must be blocked for the targeted user")
	}

	svc.cache.rejectedSkills = nil
	if call(`{"body":{"skill":"aws-credential-helper"},"toolName":"Skill"}`, "") != nil {
		t.Error("empty rejected-skill cache must never block")
	}
}

func TestToolCallFromPayload(t *testing.T) {
	for name, c := range map[string]struct{ payload, tool, skill string }{
		"skill":        {`{"body":{"skill":"docx"},"toolName":"Skill"}`, "Skill", "docx"},
		"bash":         {`{"body":{"command":"ls"},"toolName":"Bash"}`, "Bash", ""},
		"mcp tool":     {`{"body":{"q":"x"},"toolName":"mcp__srv__tool"}`, "mcp__srv__tool", ""},
		"no tool name": {`{"prompt":"hi"}`, "", ""},
		"bad json":     {`nope`, "", ""},
		"empty":        {``, "", ""},
	} {
		tool, skill := toolCallFromPayload(c.payload)
		if tool != c.tool || skill != c.skill {
			t.Errorf("%s: toolCallFromPayload = (%q, %q), want (%q, %q)", name, tool, skill, c.tool, c.skill)
		}
	}
}

// TestCheckRejectedSkillLogs asserts the log lines that explain each decision.
func TestCheckRejectedSkillLogs(t *testing.T) {
	core, logs := observer.New(zapcore.InfoLevel)
	skipThreat := true
	svc := &Service{
		logger: zap.New(core),
		cache: &policyCache{rejectedSkills: rejectedAuditDecisions([]byte(`{"mcpAuditInfoList":[
			{"type":"AGENT_SKILL","resourceName":"aws-credential-helper","mcpHost":"claude","remarks":"Rejected"}
		]}`)).skills},
	}
	const host = "dev.ai-agent.claudecli"
	run := func(tool, body string) {
		payload := `{"body":` + body + `,"toolName":"` + tool + `"}`
		params := &models.ValidateRequestParams{Path: "/tool/" + tool, Method: "POST", RequestPayload: payload, SkipThreat: &skipThreat}
		svc.checkRejectedSkill(params, &mcp.ValidationContext{McpServerName: host, RequestHeaders: map[string]string{"host": host}}, payload, "sess", "req")
	}
	msgs := func() []string {
		var out []string
		for _, e := range logs.TakeAll() {
			out = append(out, e.Message)
		}
		return out
	}
	has := func(got []string, want string) bool {
		for _, m := range got {
			if m == want {
				return true
			}
		}
		return false
	}

	run("Bash", `{"command":"ls"}`) // any tool call is logged, but only Skill is evaluated
	got := msgs()
	if !has(got, "[SkillCheck] tool call received") || has(got, "[SkillCheck] skill evaluated") {
		t.Errorf("Bash call: unexpected logs %v", got)
	}

	run("Skill", `{"skill":"docx"}`) // evaluated, allowed
	got = msgs()
	if !has(got, "[SkillCheck] skill evaluated") || has(got, "ValidateRequest - blocking skill rejected in audit data") {
		t.Errorf("allowed skill: unexpected logs %v", got)
	}

	run("Skill", `{"skill":"aws-credential-helper"}`) // evaluated, blocked
	got = msgs()
	if !has(got, "[SkillCheck] skill evaluated") || !has(got, "ValidateRequest - blocking skill rejected in audit data") {
		t.Errorf("blocked skill: unexpected logs %v", got)
	}

	svc.checkRejectedSkill(&models.ValidateRequestParams{RequestPayload: `{"prompt":"hi"}`}, &mcp.ValidationContext{}, "", "s", "r") // not a tool call
	if got = msgs(); len(got) != 0 {
		t.Errorf("non-tool payload must log nothing, got %v", got)
	}
}
