package validator

import "testing"

func TestNormalizeAccountType(t *testing.T) {
	cases := []struct {
		raw  string
		want string
	}{
		{"personal", accountTypePersonal},
		{"Personal", accountTypePersonal},
		{"  PERSONAL  ", accountTypePersonal},
		{"enterprise", accountTypeEnterprise},
		{"Enterprise", accountTypeEnterprise},
		{"unknown", accountTypeUnknown},
		{"", ""},
		{"   ", ""},
		{"corporate", "corporate"},
	}
	for _, c := range cases {
		if got := normalizeAccountType(c.raw); got != c.want {
			t.Errorf("normalizeAccountType(%q): got %q, want %q", c.raw, got, c.want)
		}
	}
}

// TestAccountTypeBlockDecision pins the explicit match used by ValidateRequest:
// only "personal" blocks. Enterprise, unknown, a missing tag, and any value this
// build does not recognise must all be allowed.
func TestAccountTypeBlockDecision(t *testing.T) {
	blocks := func(raw string) bool {
		return normalizeAccountType(raw) == accountTypePersonal
	}
	cases := []struct {
		raw  string
		want bool
	}{
		{"personal", true},
		{"Personal", true},
		{"enterprise", false},
		{"unknown", false},
		{"", false},
		{"corporate", false},
	}
	for _, c := range cases {
		if got := blocks(c.raw); got != c.want {
			t.Errorf("accountType=%q: blocks=%v, want %v", c.raw, got, c.want)
		}
	}
}

// TestNoNewBlocksVersusPreviousBehaviour guards against false positives: the explicit
// "personal"-only match must never block a request that the previous
// (accountType != "" && accountType != "enterprise") rule allowed. Blocking strictly
// less is fine; blocking anything new is a regression that would break live traffic.
func TestNoNewBlocksVersusPreviousBehaviour(t *testing.T) {
	values := []string{
		"", "personal", "Personal", " personal ", "enterprise", "Enterprise",
		"unknown", "team", "go", "plus", "pro", "max", "free", "corporate", "garbage",
	}
	// previous behaviour, operating on the raw first-non-empty value
	oldBlocks := func(raw string) bool {
		return raw != "" && raw != accountTypeEnterprise
	}
	newBlocks := func(raw string) bool {
		return normalizeAccountType(raw) == accountTypePersonal
	}

	for _, login := range values {
		for _, browser := range values {
			tags := map[string]string{}
			if login != "" {
				tags[tagKeyLoginUserEmailType] = login
			}
			if browser != "" {
				tags[tagKeyBrowserLLMAccount] = browser
			}
			// Both old and new resolve via the same strict precedence order.
			resolved, _ := resolveAccountType(tags)
			rawResolved := login
			if rawResolved == "" {
				rawResolved = browser
			}
			if newBlocks(resolved) && !oldBlocks(rawResolved) {
				t.Errorf("NEW BLOCK introduced: login=%q browser=%q (resolved=%q) — was allowed before",
					login, browser, resolved)
			}
		}
	}
}

func TestResolveAccountType(t *testing.T) {
	cases := []struct {
		name string
		tags map[string]string
		want string
	}{
		{"no account tags", map[string]string{"gen-ai": "Gen AI"}, ""},
		{"nil tags", nil, ""},
		{
			// vrushabh.chrome.poe.com: browser collection with no login-user-email-type.
			"browser extension unknown",
			map[string]string{"browser-llm-account-type": accountTypeUnknown},
			accountTypeUnknown,
		},
		{
			"browser extension personal",
			map[string]string{"browser-llm-account-type": accountTypePersonal},
			accountTypePersonal,
		},
		{
			"login-user-email-type preferred over browser tag",
			map[string]string{
				"browser-llm-account-type": accountTypeUnknown,
				"login-user-email-type":    accountTypeEnterprise,
			},
			accountTypeEnterprise,
		},
		{
			// Strict precedence: the authoritative key wins even when the browser tag
			// disagrees. Blocking here would be a false positive, since this request
			// was allowed before the explicit-match change.
			"authoritative enterprise beats personal on browser key",
			map[string]string{
				"login-user-email-type":    accountTypeEnterprise,
				"browser-llm-account-type": accountTypePersonal,
			},
			accountTypeEnterprise,
		},
		{
			// CLI plan tier must be ignored entirely: these collections resolve from
			// login-user-email-type alone (ai-agent.claude / ai-agent.codex-cli).
			"cli plan tier team is ignored",
			map[string]string{
				"ai-agent-account-type": "team",
				"login-user-email-type": accountTypeEnterprise,
			},
			accountTypeEnterprise,
		},
		{
			"cli plan tier go is ignored",
			map[string]string{
				"ai-agent-account-type": "go",
				"login-user-email-type": accountTypeEnterprise,
			},
			accountTypeEnterprise,
		},
		{
			// A plan tier alone is not an account-type signal at all.
			"cli plan tier alone yields no signal",
			map[string]string{"ai-agent-account-type": "go"},
			"",
		},
		{
			"case and whitespace normalised",
			map[string]string{"login-user-email-type": "  Personal "},
			accountTypePersonal,
		},
	}
	for _, c := range cases {
		if got, _ := resolveAccountType(c.tags); got != c.want {
			t.Errorf("%s: got %q, want %q", c.name, got, c.want)
		}
	}
}
