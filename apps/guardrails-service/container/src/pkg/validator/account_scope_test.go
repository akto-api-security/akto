package validator

import (
	"encoding/base64"
	"encoding/json"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

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

// Only an enterprise email drops SkipEnterpriseAccounts policies.
func TestFilterPoliciesByAccountType(t *testing.T) {
	s := &Service{logger: zap.NewNop()}
	scoped := types.Policy{Info: types.PolicyInfo{Name: "pii-personal"}, SkipEnterpriseAccounts: true}
	everyone := types.Policy{Info: types.PolicyInfo{Name: "everyone"}}
	skipped := []string{"everyone"}
	kept := []string{"pii-personal", "everyone"}

	cases := []struct {
		name        string
		tag         string
		headers     map[string]string
		want        []string
		fullRequest string
	}{
		// Browser extension: browser-user-email tag.
		{"enterprise email skips scoped policy", `{"browser-user-email":"rahul@akto.io"}`, nil, skipped, ""},
		{"enterprise email case and space normalised", `{"browser-user-email":"  Rahul@AKTO.io "}`, nil, skipped, ""},
		{"personal email keeps scoped policy", `{"browser-user-email":"someone@gmail.com"}`, nil, kept, ""},
		{"empty email is unknown", `{"browser-user-email":""}`, nil, kept, ""},
		{"missing email is unknown", `{"browser-llm":"Browser LLM"}`, nil, kept, ""},
		{"malformed email is unknown", `{"browser-user-email":"not-an-email"}`, nil, kept, ""},
		{"no tag is unknown", "", nil, kept, ""},
		{"unparseable tag is unknown", "{bad json", nil, kept, ""},
		// Only the email is used, not browser-llm-account-type.
		{"account-type tag alone is ignored", `{"browser-llm-account-type":"enterprise"}`, nil, kept, ""},

		// Endpoint shield: OpenAI token in fullRequest.
		{"fullRequest enterprise token skips", "", nil, skipped, fullRequestWithAuth(openAIToken("dev@akto.io"))},
		{"fullRequest personal token keeps", "", nil, kept, fullRequestWithAuth(openAIToken("shubhamgoyal2259@gmail.com"))},
		{"fullRequest header name is case-insensitive", "", nil, skipped, `{"headers":[["Authorization",` + mustJSON(openAIToken("dev@akto.io")) + `]]}`},
		{"fullRequest token without profile email is unknown", "", nil, kept, fullRequestWithAuth(openAIToken(""))},
		{"fullRequest non-bearer auth is unknown", "", nil, kept, fullRequestWithAuth("Basic dXNlcjpwYXNz")},
		{"fullRequest truncated token is unknown", "", nil, kept, fullRequestWithAuth("Bearer eyJ...")},
		{"fullRequest wins over requestHeaders", "", map[string]string{"authorization": openAIToken("dev@akto.io")}, kept, fullRequestWithAuth(openAIToken("someone@gmail.com"))},
		{"fullRequest without auth is unknown", "", map[string]string{"authorization": openAIToken("dev@akto.io")}, kept, fullRequestWithAuth("")},
		{"unparseable fullRequest is unknown", "", map[string]string{"authorization": openAIToken("dev@akto.io")}, kept, "{bad json"},
		{"requestHeaders token alone is ignored", `{"ai-agent":"chatgpt"}`, map[string]string{"authorization": openAIToken("dev@akto.io")}, kept, ""},
		{"browser tag email wins over token", `{"browser-user-email":"someone@gmail.com"}`, nil, kept, fullRequestWithAuth(openAIToken("dev@akto.io"))},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			ctx := &mcp.ValidationContext{Tag: c.tag, RequestHeaders: c.headers, FullRequest: c.fullRequest}
			got := policyNames(s.applicablePolicies([]types.Policy{scoped, everyone}, ctx))
			if len(got) != len(c.want) {
				t.Fatalf("got %v, want %v", got, c.want)
			}
			for i := range got {
				if got[i] != c.want[i] {
					t.Fatalf("got %v, want %v", got, c.want)
				}
			}
		})
	}
}
