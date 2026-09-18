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

// An org-scoped row must never match by email. Its UserEmail is the person's ordinary address,
// identical across every org they belong to, so matching it would fire the policy in all of their
// orgs — and on non-Claude hosts, which never reach the org check at all — defeating the scoping
// the author chose by picking that org row.
func TestFindUserMetadataByEmailSkipsOrgScopedRows(t *testing.T) {
	const org = "b1553cda-0a23-414d-b7d0-be9d7657add9"
	const email = "shubham@akto.io"

	orgRow := types.AgenticUsers{UserName: "shubham", UserEmail: email, UserId: email + "_" + org}
	plainRow := types.AgenticUsers{UserName: "shubham", UserEmail: email, UserId: email}

	if got := findUserMetadataByEmail([]types.AgenticUsers{orgRow}, email); got != nil {
		t.Errorf("org-scoped row matched by email: %+v", got)
	}

	// The plain row is what "this person, any org" means, and it must keep matching exactly as
	// it did before org scoping existed.
	if got := findUserMetadataByEmail([]types.AgenticUsers{plainRow}, email); got == nil {
		t.Error("plain row did not match by email")
	}

	// Picking the person AND one of their orgs writes both rows; the plain one still matches.
	if got := findUserMetadataByEmail([]types.AgenticUsers{orgRow, plainRow}, email); got == nil {
		t.Error("plain row alongside an org row did not match by email")
	}

	// A row with no UserId at all (module_info-only identity: browser extension / Claude Desktop)
	// carries no org and must still match.
	bare := types.AgenticUsers{UserName: "aanchal", UserEmail: "aanchal@akto.io"}
	if got := findUserMetadataByEmail([]types.AgenticUsers{bare}, "aanchal@akto.io"); got == nil {
		t.Error("row with no userId did not match by email")
	}
}

// Reproduces the two requests from the production logs verbatim. Same person, same device, same
// surface, one policy — the only thing that differs is the org in the request path. A policy built
// on org f52d9c27 must apply in f52d9c27 and must NOT apply in e68d326b.
func TestPolicyOnOneOrgDoesNotApplyInAnother(t *testing.T) {
	const (
		policyOrg = "f52d9c27-708c-465e-9f81-531864e3df21"
		otherOrg  = "e68d326b-0acb-4573-85a6-4ed867827c96"
	)

	// Exactly what the dashboard saves for a policy scoped to one Claude org: the shared userName
	// and ordinary email, with the org carried only in UserId.
	rows := []types.AgenticUsers{{
		UserName:  "shubham",
		UserEmail: "shubham@akto.io",
		UserId:    "shubham@akto.io_" + policyOrg,
	}}

	samePath := "/api/organizations/" + policyOrg + "/chat_conversations/67e8b2f3-9e78-423e-8ca2-f82c769ef6f0/completion"
	otherPath := "/api/organizations/" + otherOrg + "/chat_conversations/adb8f332-41f0-4d26-97f5-1e4c3bb4aade/completion"

	if got := orgUUIDFromPath(samePath); got != policyOrg {
		t.Fatalf("orgUUIDFromPath(same) = %q, want %q", got, policyOrg)
	}
	if got := orgUUIDFromPath(otherPath); got != otherOrg {
		t.Fatalf("orgUUIDFromPath(other) = %q, want %q", got, otherOrg)
	}

	if !rowsMatchOrg(rows, orgUUIDFromPath(samePath)) {
		t.Error("policy did not apply in its own org")
	}
	if rowsMatchOrg(rows, orgUUIDFromPath(otherPath)) {
		t.Error("policy applied in a different org")
	}

	// The email route must not resurrect it in the other org. This row's email is the person's
	// ordinary address, so before the skip it matched everywhere regardless of org.
	if got := findUserMetadataByEmail(rows, "shubham@akto.io"); got != nil {
		t.Errorf("org-scoped row matched by email, which would apply the policy in every org: %+v", got)
	}
}

// The org in the path is per-request and authoritative; the device map only knows what the machine
// last reported. They agree in the steady state, so this only bites right after an org switch —
// which is exactly when a user is most likely to be testing the policy.
func TestPathOrgIsPreferredOverStaleDeviceMap(t *testing.T) {
	const (
		pathOrg  = "f52d9c27-708c-465e-9f81-531864e3df21"
		staleOrg = "e68d326b-0acb-4573-85a6-4ed867827c96"
		device   = "shubham-s-macbook-pro--3--1aefa0eb"
	)
	rows := []types.AgenticUsers{{UserId: "shubham@akto.io_" + pathOrg}}

	// Device map still reporting the org the user just left.
	stale := map[string]map[string]dbabstractor.ClaudeDesktopInfo{
		device: {"claude-desktop": {OrganizationUUID: staleOrg}},
	}
	if claudeOrgForHost(device+".ai-agent.claude-desktop.akto.io", device, stale) != staleOrg {
		t.Fatal("fixture is not exercising the stale case")
	}

	// The path names the org the request is actually in, and that is what must decide.
	if !rowsMatchOrg(rows, orgUUIDFromPath("/api/organizations/"+pathOrg+"/chat_conversations/x/completion")) {
		t.Error("path org did not match the policy org")
	}
}

// A path segment sitting where an org would be is not automatically an org.
func TestOrgUUIDFromPathRejectsNonUUID(t *testing.T) {
	for _, path := range []string{
		"",
		"/api/organizations/",
		"/api/organizations/not-a-uuid/chat_conversations/x",
		"/api/chat_conversations/x/completion",
		"/v1/messages",
	} {
		if got := orgUUIDFromPath(path); got != "" {
			t.Errorf("orgUUIDFromPath(%q) = %q, want \"\"", path, got)
		}
	}
}
