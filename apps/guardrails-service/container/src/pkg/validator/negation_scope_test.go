package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

// ponytail: smallest runnable check for type-aware Include/Exclude matching in filterPoliciesByMcpServer.
func TestFilterPoliciesByMcpServer_Negation(t *testing.T) {
	s := &Service{}
	set := func(vals ...string) map[string]struct{} {
		m := make(map[string]struct{}, len(vals))
		for _, v := range vals {
			m[v] = struct{}{}
		}
		return m
	}

	t.Run("include mode unaffected by name shared across types", func(t *testing.T) {
		p := types.Policy{SelectedAgentServers: set("cursor")}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.cursor.filesystem")
		if len(got) != 1 {
			t.Fatalf("expected agent-scoped policy to match agent traffic, got %d results", len(got))
		}
		got = s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected agent-scoped policy to NOT match a different agent, got %d results", len(got))
		}
	})

	// Rows OR together (pre-existing); what negation must never do is leak across segment positions.
	t.Run("agent bucket only matches the clientType segment position", func(t *testing.T) {
		// "filesystem" stored as an agent key; here it sits in the HOST position — must not match.
		p := types.Policy{SelectedAgentServers: set("filesystem")}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.cursor.filesystem")
		if len(got) != 0 {
			t.Fatalf("agent bucket matched a host-position occurrence, got %d results", len(got))
		}
	})

	t.Run("mcp bucket only matches the host segment position", func(t *testing.T) {
		// "cursor" stored as an MCP key; here it sits in the clientType position — must not match.
		p := types.Policy{SelectedMcpServers: set("cursor")}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.cursor.filesystem")
		if len(got) != 0 {
			t.Fatalf("mcp bucket matched a clientType-position occurrence, got %d results", len(got))
		}
	})

	t.Run("llm bucket matches host segment and is independent of mcp/agent", func(t *testing.T) {
		p := types.Policy{SelectedLlmServers: set("chatgpt.com"), NegatedLlmServers: true}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.chatgpt.com")
		if len(got) != 0 {
			t.Fatalf("expected the specifically-excluded LLM domain to be rejected, got %d results", len(got))
		}
		got = s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.otherllm.com")
		if len(got) != 1 {
			t.Fatalf("expected a non-excluded LLM domain to still match, got %d results", len(got))
		}
		// "chatgpt.com" stored as an LLM key; here it sits in the clientType position — must not match.
		got = s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.chatgpt.com.somemcpserver")
		if len(got) != 1 {
			t.Fatalf("llm bucket matched a clientType-position occurrence, got %d results", len(got))
		}
	})

	t.Run("exclude rejects the specifically-excluded value, still matches others of the same type", func(t *testing.T) {
		p := types.Policy{SelectedAgentServers: set("cursor"), NegatedAgentServers: true}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.cursor.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected the specifically-excluded agent to be rejected, got %d results", len(got))
		}
		got = s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.filesystem")
		if len(got) != 1 {
			t.Fatalf("expected a non-excluded agent to still match (exclude = allow everyone else), got %d results", len(got))
		}
	})

	t.Run("empty-negated bucket falls back to matching everything", func(t *testing.T) {
		p := types.Policy{NegatedAgentServers: true} // "exclude nothing" on Agents, nothing else configured
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.somemcpserver")
		if len(got) != 1 {
			t.Fatalf("expected empty-negated Agent bucket to match an MCP-shaped name too (documented global fallback), got %d results", len(got))
		}
	})

	t.Run("nothing configured at all is skipped", func(t *testing.T) {
		p := types.Policy{}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.claude2.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected unconfigured policy to be skipped, got %d results", len(got))
		}
	})

	t.Run("legacy compound key falls back to loose match", func(t *testing.T) {
		p := types.Policy{SelectedAgentServers: set("cursor.filesystem")}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.cursor.filesystem")
		if len(got) != 1 {
			t.Fatalf("expected legacy compound key to still match via loose fallback, got %d results", len(got))
		}
	})

	// A value excluded by one bucket must not sneak back in via a different bucket's elimination logic.
	t.Run("value excluded by one bucket is vetoed across all buckets", func(t *testing.T) {
		p := types.Policy{
			SelectedMcpServers:   set("filesystem"),
			SelectedAgentServers: set("cursor"),
			NegatedMcpServers:    true,
			NegatedAgentServers:  true,
		}
		// "filesystem" is specifically excluded via the MCP bucket; the Agent-Exclude bucket must not grant it anyway.
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.someunlistedagent.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected MCP-excluded name to stay excluded despite Agent bucket's elimination logic, got %d results", len(got))
		}
		// A name excluded by neither bucket still matches via elimination (the future-proofing behavior).
		got = s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.someunlistedagent.someunlistedhost")
		if len(got) != 1 {
			t.Fatalf("expected a genuinely unlisted name to still match via elimination, got %d results", len(got))
		}
	})

	// Include-mode "select all" (wildcard sentinel): matches present + future, stays Include.
	t.Run("include wildcard matches present and future, vetoed by an explicit exclusion elsewhere", func(t *testing.T) {
		p := types.Policy{SelectedAgentServers: set(wildcardAllServers)}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.brandnewagent.somehost")
		if len(got) != 1 {
			t.Fatalf("expected wildcard Agent bucket to match a never-seen-before agent, got %d results", len(got))
		}
		// Cross-bucket veto still applies: explicitly excluded via MCP must not sneak back in via the Agent wildcard.
		p2 := types.Policy{
			SelectedAgentServers: set(wildcardAllServers),
			SelectedMcpServers:   set("filesystem"),
			NegatedMcpServers:    true,
		}
		got = s.filterPoliciesByMcpServer([]types.Policy{p2}, "device1.brandnewagent.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected MCP-excluded name to stay excluded despite the Agent wildcard, got %d results", len(got))
		}
	})

	// Regression test using the exact policy shape captured from a live local run.
	t.Run("real policy: MCP+Agent exclude specific values, LLM include allow-list", func(t *testing.T) {
		p := types.Policy{
			SelectedMcpServers:   set("127.0.0.1:3845", "127.0.0.1:8686", "127.0.0.1:8687", "127.0.0.1:8688"),
			SelectedAgentServers: set("agent", "agy-customizations"),
			SelectedLlmServers:   set("chatgpt.com", "claude.ai"),
			NegatedAgentServers:  true,
			NegatedMcpServers:    true,
		}
		cases := []struct {
			id      string
			wantHit bool
		}{
			{"127.0.0.1:3845", false},                   // specifically-excluded MCP host
			{"127.0.0.1:9999", true},                    // new/future MCP host, not excluded
			{"somedevice.agent.somehost", false},        // specifically-excluded agent
			{"somedevice.brandnewagent.somehost", true}, // new/future agent, not excluded
			{"somedevice.someagent.chatgpt.com", true},  // in the LLM allow-list
		}
		for _, c := range cases {
			got := s.filterPoliciesByMcpServer([]types.Policy{p}, c.id)
			if hit := len(got) == 1; hit != c.wantHit {
				t.Errorf("id=%q: want hit=%v, got hit=%v", c.id, c.wantHit, hit)
			}
		}
	})
}
