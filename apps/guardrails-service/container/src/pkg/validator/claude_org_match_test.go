package validator

import (
	"strings"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
)

func TestOrgUUIDFromUserID(t *testing.T) {
	const org = "b1553cda-0a23-414d-b7d0-be9d7657add9"

	cases := map[string]struct {
		userID string
		want   string
	}{
		"plain email":  {"shubhamgoyal2259@gmail.com_" + org, org},
		"no org":       {"shubhamgoyal2259@gmail.com", ""},
		"empty":        {"", ""},
		"trailing sep": {"someone@corp.com_", ""},

		// The reason this splits on the LAST underscore. A first-underscore split would return
		// "last@corp.com_<uuid>", which matches no org and silently disables the policy.
		"underscore in local part": {"first_last@corp.com_" + org, org},

		// An id from another identity source must not be mistaken for an org — without the uuid
		// shape check this yields "12345" and the policy stops matching entirely.
		"non-uuid suffix": {"okta_12345", ""},
		"aad object id":   {"2f1a9c4e-0000-0000-0000-000000000000", ""},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			if got := orgUUIDFromUserID(tc.userID); got != tc.want {
				t.Errorf("orgUUIDFromUserID(%q) = %q, want %q", tc.userID, got, tc.want)
			}
		})
	}
}

func infoMapFixture() map[string]map[string]dbabstractor.ClaudeDesktopInfo {
	return map[string]map[string]dbabstractor.ClaudeDesktopInfo{
		"shubham-s-macbook-pro--3--1aefa0eb": {
			"claude-desktop":  {OrganizationUUID: "e68d326b-1111-1111-1111-111111111111", LoggedIn: true},
			"claude-cli-user": {OrganizationUUID: "aaaa2222-2222-2222-2222-222222222222", LoggedIn: true},
		},
	}
}

func TestClaudeOrgForHost(t *testing.T) {
	const device = "shubham-s-macbook-pro--3--1aefa0eb"
	desktopOrg := "e68d326b-1111-1111-1111-111111111111"
	cliOrg := "aaaa2222-2222-2222-2222-222222222222"
	m := infoMapFixture()

	cases := map[string]struct {
		host   string
		device string
		want   string
	}{
		"desktop":               {device + ".ai-agent.claude-desktop.akto.io", device, desktopOrg},
		"cli":                   {device + ".ai-agent.claude-cli.akto.io", device, cliOrg},
		"cli unhyphenated":      {device + ".ai-agent.claudecli.akto.io", device, cliOrg},
		"cowork shares desktop": {device + ".ai-agent.claude-cowork.akto.io", device, desktopOrg},
		"unknown surface":       {device + ".ai-agent.cursor.akto.io", device, ""},
		"unknown device":        {device + ".ai-agent.claude-desktop.akto.io", "some-other-device", ""},
		"empty host":            {"", device, ""},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			if got := claudeOrgForHost(tc.host, tc.device, m); got != tc.want {
				t.Errorf("claudeOrgForHost(%q, %q) = %q, want %q", tc.host, tc.device, got, tc.want)
			}
		})
	}
}

// Desktop and the CLI authenticate separately and can sit in different orgs at once. This is the
// case the whole feature exists for: a policy scoped to the CLI's org must not fire on Desktop
// traffic from the same machine and the same person.
func TestSurfacesResolveToDifferentOrgs(t *testing.T) {
	const device = "shubham-s-macbook-pro--3--1aefa0eb"
	m := infoMapFixture()

	desktop := claudeOrgForHost(device+".ai-agent.claude-desktop.akto.io", device, m)
	cli := claudeOrgForHost(device+".ai-agent.claude-cli.akto.io", device, m)

	if desktop == cli {
		t.Fatalf("fixture is not exercising the divergent case: both resolved to %q", desktop)
	}
	if desktop == "" || cli == "" {
		t.Fatalf("both surfaces should resolve; desktop=%q cli=%q", desktop, cli)
	}
}

func TestClaudeOrgForHostNilMap(t *testing.T) {
	if got := claudeOrgForHost("d.ai-agent.claude-desktop.akto.io", "d", nil); got != "" {
		t.Errorf("nil map should yield empty, got %q", got)
	}
}

func TestRowsMatchOrg(t *testing.T) {
	const orgA = "b1553cda-0a23-414d-b7d0-be9d7657add9"
	const orgB = "e68d326b-1111-1111-1111-111111111111"

	withOrgA := []types.AgenticUsers{{UserId: "shubhamgoyal2259@gmail.com_" + orgA}}
	noOrg := []types.AgenticUsers{{UserId: "shubhamgoyal2259@gmail.com"}}
	mixed := []types.AgenticUsers{
		{UserId: "someone@corp.com"},
		{UserId: "other@corp.com_" + orgB},
	}

	cases := map[string]struct {
		rows    []types.AgenticUsers
		liveOrg string
		want    bool
	}{
		"same org":                {withOrgA, orgA, true},
		"different org":           {withOrgA, orgB, false},
		"case insensitive":        {withOrgA, strings.ToUpper(orgA), true},
		"row carries no org":      {noOrg, orgA, false},
		"one of several rows":     {mixed, orgB, true},
		"no row carries live org": {mixed, orgA, false},
		"empty rows":              {nil, orgA, false},

		// The polarity that matters: as an OR term an unknown live org must contribute nothing.
		// Returning true here would make every policy match every request whenever the device
		// map is unavailable.
		"unknown live org": {withOrgA, "", false},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			if got := rowsMatchOrg(tc.rows, tc.liveOrg); got != tc.want {
				t.Errorf("rowsMatchOrg(%v, %q) = %v, want %v", tc.rows, tc.liveOrg, got, tc.want)
			}
		})
	}
}

// Org scoping is Claude-only: every other agent has one identity per account with no org
// dimension. A non-Claude host must short-circuit before any id parsing or cache lookup, leaving
// those requests on the plain email match they had before.
func TestClaudeLoginKeyForHostGatesNonClaude(t *testing.T) {
	claude := map[string]string{
		"d.ai-agent.claude-desktop.akto.io": "claude-desktop",
		"d.ai-agent.claude-cowork.akto.io":  "claude-desktop",
		"d.ai-agent.claude-cli.akto.io":     "claude-cli-user",
		"d.ai-agent.claudecli.akto.io":      "claude-cli-user",
	}
	for host, want := range claude {
		if got := claudeLoginKeyForHost(host); got != want {
			t.Errorf("claudeLoginKeyForHost(%q) = %q, want %q", host, got, want)
		}
	}

	for _, host := range []string{
		"d.ai-agent.cursor.akto.io",
		"d.ai-agent.copilot.akto.io",
		"d.ai-agent.codex-cli.akto.io",
		"api.openai.com",
		"",
	} {
		if got := claudeLoginKeyForHost(host); got != "" {
			t.Errorf("claudeLoginKeyForHost(%q) = %q, want \"\" (non-Claude host)", host, got)
		}
	}
}

// The ingestion side (com.akto.utils.elasticsearch.AgentQueryRecord) carries the same table and
// must stay in step — it stamps the org onto serviceId using identical host substrings and login
// keys. Pin the mapping so a change on either side shows up as a failing test rather than as
// traffic attributed to one org and policies matched against another.
func TestClaudeSurfaceMappingMatchesIngestion(t *testing.T) {
	want := map[string]string{
		"claude-desktop": "claude-desktop",
		"claude-cowork":  "claude-desktop",
		"claude-cli":     "claude-cli-user",
		"claudecli":      "claude-cli-user",
	}
	if len(claudeSurfaces) != len(want) {
		t.Fatalf("got %d surfaces, want %d", len(claudeSurfaces), len(want))
	}
	for _, surface := range claudeSurfaces {
		if got, ok := want[surface[0]]; !ok || got != surface[1] {
			t.Errorf("host %q maps to %q, want %q", surface[0], surface[1], want[surface[0]])
		}
	}
}
