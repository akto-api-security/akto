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
