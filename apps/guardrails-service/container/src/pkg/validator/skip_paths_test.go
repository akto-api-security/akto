package validator

import (
	"testing"

	"go.uber.org/zap"
)

func TestPathSkipper(t *testing.T) {
	logger := zap.NewNop()

	type hp struct {
		host string
		path string
	}

	cases := []struct {
		name        string
		spec        string
		wantEnabled bool
		skip        []hp // host/path that should be skipped
		keep        []hp // host/path that should NOT be skipped
	}{
		{
			name:        "empty spec never skips",
			spec:        "",
			wantEnabled: false,
			keep:        []hp{{"agent1.example.com", "/tool/Agent"}, {"", "/anything"}, {"", ""}},
		},
		{
			name:        "path only matches any host",
			spec:        "/tool/Agent",
			wantEnabled: true,
			skip:        []hp{{"agent1.example.com", "/tool/Agent"}, {"agent2.example.com", "/tool/Agent"}, {"", "/tool/Agent/sub"}},
			keep:        []hp{{"agent1.example.com", "/tool/Other"}, {"", "/chat"}, {"", "/tool/agent"}},
		},
		{
			name:        "host+path scopes to one agent",
			spec:        "agent1.example.com/tool/Agent",
			wantEnabled: true,
			skip:        []hp{{"agent1.example.com", "/tool/Agent"}, {"agent1.example.com", "/tool/Agent/sub"}},
			keep: []hp{
				{"agent2.example.com", "/tool/Agent"}, // same path, different host — NOT skipped
				{"", "/tool/Agent"},                   // no host — NOT skipped
			},
		},
		{
			name:        "comma-separated host+path rules with spaces",
			spec:        "agent1.example.com/tool/Agent, agent2.example.com/tool/Bot ",
			wantEnabled: true,
			skip:        []hp{{"agent1.example.com", "/tool/Agent"}, {"agent2.example.com", "/tool/Bot"}},
			keep:        []hp{{"agent1.example.com", "/tool/Bot"}, {"agent2.example.com", "/tool/Agent"}},
		},
		{
			name:        "regex against host+path",
			spec:        `regex:^agent1\.example\.com/tool/Agent$`,
			wantEnabled: true,
			skip:        []hp{{"agent1.example.com", "/tool/Agent"}},
			keep: []hp{
				{"agent1.example.com", "/tool/Agent/sub"},
				{"agent2.example.com", "/tool/Agent"},
			},
		},
		{
			name:        "invalid regex disables skipping",
			spec:        `regex:[unclosed`,
			wantEnabled: false,
			keep:        []hp{{"agent1.example.com", "/tool/Agent"}},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			ps := newPathSkipper(tc.spec, logger)
			if ps.enabled() != tc.wantEnabled {
				t.Fatalf("enabled() = %v, want %v", ps.enabled(), tc.wantEnabled)
			}
			for _, c := range tc.skip {
				if !ps.shouldSkip(c.host, c.path) {
					t.Errorf("shouldSkip(%q, %q) = false, want true", c.host, c.path)
				}
			}
			for _, c := range tc.keep {
				if ps.shouldSkip(c.host, c.path) {
					t.Errorf("shouldSkip(%q, %q) = true, want false", c.host, c.path)
				}
			}
		})
	}
}
