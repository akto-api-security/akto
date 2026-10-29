package validator

import "testing"

func TestStripBlockSuffix(t *testing.T) {
	const block = `{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"Blocked","data":{"blocked_by":"policy_validator","reason":"r"}}}`
	cases := []struct {
		name, raw, body string
		pureBlock       bool
	}{
		{"array", `[{"a": 1},` + block + `]`, `{"a": 1}`, false},
		{"array with non-json body", `["event: x\ndata: {}",` + block + `]`, "event: x\ndata: {}", false},
		{"old separator", `{"a":1}` + "\n\n" + block, `{"a":1}`, false},
		{"request blocked", block, "", true},
		{"upstream array", `[{"a":1},{"b":2}]`, `[{"a":1},{"b":2}]`, false},
		{"plain body", `{"a":1}`, `{"a":1}`, false},
	}
	for _, c := range cases {
		body, pure := stripBlockSuffix(c.raw)
		if body != c.body || pure != c.pureBlock {
			t.Errorf("%s: got (%q, %v), want (%q, %v)", c.name, body, pure, c.body, c.pureBlock)
		}
	}
}
