package config

import "testing"

func TestLoadDefaultsAndRejectsBadDurations(t *testing.T) {
	t.Setenv("NAMESPACE", "akto")
	t.Setenv("TARGET_DEPLOYMENT", "akto-mini-testing")
	c, err := Load()
	if err != nil {
		t.Fatalf("defaults: %v", err)
	}
	if c.MinReplicas != 1 || c.MaxReplicas != 10 || c.Heartbeat || !c.ReportToAkto {
		t.Errorf("unexpected defaults: %+v", c)
	}

	for _, bad := range []struct{ key, value string }{
		{"HEARTBEAT_INTERVAL", "0s"},
		{"SCALE_DOWN_DELAY", "-1m"},
		{"NOT_READY_GRACE", "0s"},
		{"STUCK_AFTER", "0s"},
		{"POLL_INTERVAL", "0s"},
		{"MIN_REPLICAS", "0"},
		{"MAX_REPLICAS", "0"},
	} {
		t.Run(bad.key, func(t *testing.T) {
			t.Setenv(bad.key, bad.value)
			if _, err := Load(); err == nil {
				t.Errorf("%s=%s accepted", bad.key, bad.value)
			}
		})
	}
}

func TestControlTokenDerivedFromAbstractorToken(t *testing.T) {
	// the same vector is asserted in mini-testing's OrchestratorControlTest
	const want = "7292c474db0ac419c9940f3edd7ed8f71c229a3f9dc505ab5a51cf0ae301d2ba"
	for _, key := range []string{"test-database-abstractor-token", "  test-database-abstractor-token\n"} {
		if got, src := resolveControlToken("", key); got != want || src != "derived" {
			t.Errorf("resolveControlToken(%q) = %q, %s; want %q, derived", key, got, src, want)
		}
	}
	if got, src := resolveControlToken("explicit", "test-database-abstractor-token"); got != "explicit" || src != "explicit" {
		t.Errorf("explicit token not preferred: %q, %s", got, src)
	}
	if got, src := resolveControlToken(" ", ""); got != "" || src != "none" {
		t.Errorf("no token expected: %q, %s", got, src)
	}

	t.Setenv("NAMESPACE", "akto")
	t.Setenv("TARGET_DEPLOYMENT", "akto-mini-testing")
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "test-database-abstractor-token")
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if c.ControlToken != want || c.ControlTokenSource != "derived" {
		t.Errorf("Load: token %q source %s", c.ControlToken, c.ControlTokenSource)
	}
}
