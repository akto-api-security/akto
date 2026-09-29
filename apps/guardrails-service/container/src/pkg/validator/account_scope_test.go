package validator

import (
	"encoding/base64"
	"encoding/json"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// newAccountTestService returns a Service with the given org email domains.
func newAccountTestService(domains []string) *Service {
	return &Service{logger: zap.NewNop(), orgEmailDomains: domains}
}

// openAIToken builds an unsigned ChatGPT-style bearer token.
func openAIToken(email string) string {
	payload := `{"https://api.openai.com/auth":{"chatgpt_plan_type":"go"},"https://api.openai.com/profile":{"email":"` + email + `","email_verified":true}}`
	return "Bearer eyJhbGciOiJSUzI1NiJ9." + base64.RawURLEncoding.EncodeToString([]byte(payload)) + ".sig"
}

// fullRequestWithAuth builds a fullRequest with the given Authorization header.
func fullRequestWithAuth(authorization string) string {
	headers := [][]string{{"content-type", "application/json"}, {"cookie", "a=b"}, {"cookie", "c=d"}}
	if authorization != "" {
		headers = append(headers, []string{"authorization", authorization})
	}
	b, _ := json.Marshal(map[string]any{"method": "POST", "host": "chatgpt.com", "headers": headers})
	return string(b)
}

func mustJSON(v string) string {
	b, _ := json.Marshal(v)
	return string(b)
}

// extensionTag builds a browser extension tag; email is raw JSON (a quoted string or null), "" omits the key.
func extensionTag(email string) string {
	if email == "" {
		return `{"gen-ai":"Gen AI","browser-llm":"Browser LLM","browser-llm-account-type":"personal"}`
	}
	return `{"gen-ai":"Gen AI","browser-llm":"Browser LLM","browser-user-email":` + email + `}`
}

// A mapped org-domain email is work, any other email is personal, no or invalid email is work.
func TestClassifyEmail(t *testing.T) {
	cases := []struct {
		domains []string
		email   string
		want    string
	}{
		{[]string{"acme.com"}, "alice@acme.com", accountTypeEnterprise},
		{[]string{"acme.com"}, "Alice@ACME.com", accountTypeEnterprise},
		{[]string{"acme.com"}, "bob@gmail.com", accountTypePersonal},
		{[]string{"acme.com"}, "bob@othercorp.com", accountTypePersonal},
		{[]string{"acme.com"}, "bob@eu.acme.com", accountTypePersonal},
		{[]string{"acme.com"}, "bob@notacme.com", accountTypePersonal},
		{[]string{"acme.com"}, "", accountTypeEnterprise},
		{[]string{"acme.com"}, "not-an-email", accountTypeEnterprise},
		{nil, "bob@gmail.com", accountTypePersonal},
		{nil, "alice@acme.com", accountTypePersonal},
		{nil, "", accountTypeEnterprise},
	}
	for _, c := range cases {
		if got := newAccountTestService(c.domains).classifyEmail(c.email); got != c.want {
			t.Errorf("domains=%v email=%q: got %s, want %s", c.domains, c.email, got, c.want)
		}
	}
}

// Block personal accounts and Personal accounts only make the same decision from the same email,
// for both browser (browser-user-email) and endpoint shield (fullRequest token) traffic.
func TestPersonalAccountFeaturesConsistent(t *testing.T) {
	const proxyTag = `{"gen-ai":"Gen AI","ai-agent":"chatgpt","source":"ENDPOINT"}`
	block := types.Policy{Info: types.PolicyInfo{Name: "block-personal"}, BlockPersonalAccounts: true}
	scoped := types.Policy{Info: types.PolicyInfo{Name: "pii-personal"}, SkipEnterpriseAccounts: true}
	everyone := types.Policy{Info: types.PolicyInfo{Name: "everyone"}}

	cases := []struct {
		name         string
		domains      []string
		tag          string
		fullRequest  string
		wantPersonal bool // true: Block personal accounts blocks and the personal-only policy applies
	}{
		// Browser extension.
		{"browser: personal email", []string{"acme.com"}, extensionTag(`"bob@gmail.com"`), "", true},
		{"browser: org email", []string{"acme.com"}, extensionTag(`"alice@acme.com"`), "", false},
		{"browser: org email any case and spaces", []string{"acme.com"}, extensionTag(`"  Alice@ACME.com "`), "", false},
		{"browser: other company email", []string{"acme.com"}, extensionTag(`"bob@othercorp.com"`), "", true},
		{"browser: empty email", []string{"acme.com"}, extensionTag(`""`), "", false},
		{"browser: null email", []string{"acme.com"}, extensionTag(`null`), "", false},
		{"browser: invalid email", []string{"acme.com"}, extensionTag(`"not-an-email"`), "", false},
		{"browser: no email key, old personal tag ignored", []string{"acme.com"}, extensionTag(""), "", false},
		{"browser: email wins over token", []string{"acme.com"}, extensionTag(`"bob@gmail.com"`), fullRequestWithAuth(openAIToken("alice@acme.com")), true},

		// Endpoint shield (ChatGPT app).
		{"proxy: personal token", []string{"acme.com"}, proxyTag, fullRequestWithAuth(openAIToken("bob@gmail.com")), true},
		{"proxy: org token", []string{"acme.com"}, proxyTag, fullRequestWithAuth(openAIToken("alice@acme.com")), false},
		{"proxy: other company token", []string{"acme.com"}, proxyTag, fullRequestWithAuth(openAIToken("bob@othercorp.com")), true},
		{"proxy: header name is case-insensitive", []string{"acme.com"}, proxyTag, `{"headers":[["Authorization",` + mustJSON(openAIToken("bob@gmail.com")) + `]]}`, true},
		{"proxy: chatgpt without token", []string{"acme.com"}, proxyTag, fullRequestWithAuth(""), true},
		{"proxy: chatgpt without token, unmapped", nil, proxyTag, fullRequestWithAuth(""), true},
		{"proxy: other host without token", []string{"acme.com"}, proxyTag, `{"host":"claude.ai","headers":[["cookie","a=b"]]}`, false},
		{"proxy: token without email", []string{"acme.com"}, proxyTag, fullRequestWithAuth(openAIToken("")), false},
		{"proxy: non-bearer auth", []string{"acme.com"}, proxyTag, fullRequestWithAuth("Basic dXNlcjpwYXNz"), false},
		{"proxy: truncated token", []string{"acme.com"}, proxyTag, fullRequestWithAuth("Bearer eyJ..."), false},
		{"proxy: unparseable fullRequest", []string{"acme.com"}, proxyTag, "{bad json", false},

		// Other traffic with no email at all.
		{"no tag, no fullRequest", []string{"acme.com"}, "", "", false},

		// Account not in the map: every email is personal, no email is work.
		{"unmapped: browser personal email", nil, extensionTag(`"bob@gmail.com"`), "", true},
		{"unmapped: browser company email", nil, extensionTag(`"alice@acme.com"`), "", true},
		{"unmapped: proxy company token", nil, proxyTag, fullRequestWithAuth(openAIToken("alice@acme.com")), true},
		{"unmapped: no email", nil, extensionTag(`""`), "", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			s := newAccountTestService(c.domains)
			if _, blocked := s.resolvePersonalAccountBlock([]types.Policy{block}, c.tag, c.fullRequest, "/p", ""); blocked != c.wantPersonal {
				t.Errorf("block personal accounts: blocked=%v, want %v", blocked, c.wantPersonal)
			}
			got := policyNames(s.applicablePolicies([]types.Policy{scoped, everyone}, &mcp.ValidationContext{Tag: c.tag, FullRequest: c.fullRequest}))
			want := []string{"everyone"}
			if c.wantPersonal {
				want = []string{"pii-personal", "everyone"}
			}
			if len(got) != len(want) || got[0] != want[0] {
				t.Errorf("personal accounts only: got %v, want %v", got, want)
			}
		})
	}
}

// Personal-account blocks use the policy's severity, falling back to MEDIUM.
func TestSeverityForPolicy(t *testing.T) {
	policies := []types.Policy{{Info: types.PolicyInfo{Name: "high"}, Severity: "high"}, {Info: types.PolicyInfo{Name: "unset"}}}
	for name, want := range map[string]string{"high": "HIGH", "unset": "MEDIUM", "missing": "MEDIUM"} {
		if got := severityForPolicy(policies, name); got != want {
			t.Errorf("%s: got %s, want %s", name, got, want)
		}
	}
}
