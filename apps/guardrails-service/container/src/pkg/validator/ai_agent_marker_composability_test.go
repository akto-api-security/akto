package validator

import "testing"
import "github.com/akto-api-security/akto-endpoint-shield/mcp/types"

// Confirms the ai-agent-marker host shape composes correctly with every other negation feature:
// the Include wildcard, and the cross-bucket veto — not just plain Exclude on its own.
func TestAiAgentMarkerShape_ComposesWithWildcardAndVeto(t *testing.T) {
	s := &Service{}
	set := func(vals ...string) map[string]struct{} {
		m := make(map[string]struct{}, len(vals))
		for _, v := range vals {
			m[v] = struct{}{}
		}
		return m
	}

	t.Run("Include wildcard matches the marker-shape host too, present+future", func(t *testing.T) {
		p := types.Policy{SelectedAgentServers: set(wildcardAllServers)}
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "kingsgambit-bf066fa2.ai-agent.brandnewagent")
		if len(got) != 1 {
			t.Fatalf("expected wildcard to match a never-seen agent in marker shape, got %d results", len(got))
		}
	})

	t.Run("veto: agent excluded via marker shape can't leak through another bucket's elimination grant", func(t *testing.T) {
		p := types.Policy{
			SelectedAgentServers: set("claudecli"),
			SelectedMcpServers:   set("filesystem"),
			NegatedAgentServers:  true,
			NegatedMcpServers:    true,
		}
		// "claudecli" is explicitly excluded via the Agent bucket (marker shape) — MCP bucket's
		// elimination grant must not let it back in, even though "filesystem" isn't the mcp host here.
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "kingsgambit-bf066fa2.ai-agent.claudecli")
		if len(got) != 0 {
			t.Fatalf("expected marker-shape-excluded agent to stay excluded, got %d results", len(got))
		}
	})

	t.Run("veto: excluded via a different bucket vetoes the marker-shape agent's own elimination grant", func(t *testing.T) {
		p := types.Policy{
			SelectedMcpServers:  set("filesystem"),
			NegatedMcpServers:   true,
			NegatedAgentServers: true, // empty Agent bucket, negated -> would normally grant everyone
		}
		// Host segment "filesystem" is explicitly MCP-excluded; the empty-negated Agent bucket's
		// elimination grant must be vetoed even though the agent segment ("someagent") isn't itself excluded.
		got := s.filterPoliciesByMcpServer([]types.Policy{p}, "device1.ai-agent.someagent.filesystem")
		if len(got) != 0 {
			t.Fatalf("expected MCP-excluded host to veto the marker-shape agent bucket's grant, got %d results", len(got))
		}
	})
}
