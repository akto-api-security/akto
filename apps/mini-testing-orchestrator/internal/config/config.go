package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config is read from the environment; the helm chart sets every value.
type Config struct {
	Namespace        string
	TargetDeployment string
	MinReplicas      int
	MaxReplicas      int
	ScaleUpStep      int
	PollInterval     time.Duration
	ScaleDownDelay   time.Duration
	PodStatusPort    int
	PodStatusTimeout time.Duration
	ControlToken     string
	LeaderElection   bool
	LeaseName        string
	PodName          string
	HealthAddr       string
	NotReadyGrace    time.Duration
	StuckAfter       time.Duration
	SummaryEvery     time.Duration

	// Reporting to Akto's database abstractor; same env names as mini-testing.
	ReportToAkto    bool
	CyborgURL       string
	CyborgToken     string
	ModuleName      string
	ReportPerMinute int
	// Heartbeat registers the orchestrator as a module in Akto. It needs a
	// cyborg and dashboard that know module type MINI_TESTING_ORCHESTRATOR.
	Heartbeat      bool
	HeartbeatEvery time.Duration
}

func Load() (Config, error) {
	c := Config{
		Namespace:        os.Getenv("NAMESPACE"),
		TargetDeployment: os.Getenv("TARGET_DEPLOYMENT"),
		ControlToken:     os.Getenv("CONTROL_TOKEN"),
		LeaseName:        envOr("LEASE_NAME", "mini-testing-orchestrator"),
		PodName:          envOr("POD_NAME", hostname()),
		HealthAddr:       envOr("HEALTH_ADDR", ":8080"),
		CyborgURL:        os.Getenv("DATABASE_ABSTRACTOR_SERVICE_URL"),
		CyborgToken:      os.Getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN"),
		ModuleName:       envOr("MINI_TESTING_NAME", "mini-testing"),
	}
	var err error
	if c.MinReplicas, err = intEnv("MIN_REPLICAS", 1); err != nil {
		return c, err
	}
	if c.MaxReplicas, err = intEnv("MAX_REPLICAS", 10); err != nil {
		return c, err
	}
	if c.ScaleUpStep, err = intEnv("SCALE_UP_STEP", 1); err != nil {
		return c, err
	}
	if c.PodStatusPort, err = intEnv("POD_STATUS_PORT", 9400); err != nil {
		return c, err
	}
	if c.PollInterval, err = durationEnv("POLL_INTERVAL", 15*time.Second); err != nil {
		return c, err
	}
	if c.ScaleDownDelay, err = durationEnv("SCALE_DOWN_DELAY", 5*time.Minute); err != nil {
		return c, err
	}
	if c.PodStatusTimeout, err = durationEnv("POD_STATUS_TIMEOUT", 3*time.Second); err != nil {
		return c, err
	}
	if c.LeaderElection, err = boolEnv("LEADER_ELECTION", true); err != nil {
		return c, err
	}
	if c.NotReadyGrace, err = durationEnv("NOT_READY_GRACE", 10*time.Minute); err != nil {
		return c, err
	}
	if c.StuckAfter, err = durationEnv("STUCK_AFTER", 10*time.Minute); err != nil {
		return c, err
	}
	if c.SummaryEvery, err = durationEnv("SUMMARY_EVERY", 5*time.Minute); err != nil {
		return c, err
	}
	if c.ReportToAkto, err = boolEnv("REPORT_TO_AKTO", true); err != nil {
		return c, err
	}
	if c.ReportPerMinute, err = intEnv("REPORT_PER_MINUTE", 30); err != nil {
		return c, err
	}
	if c.Heartbeat, err = boolEnv("HEARTBEAT_ENABLED", false); err != nil {
		return c, err
	}
	if c.HeartbeatEvery, err = durationEnv("HEARTBEAT_INTERVAL", 30*time.Second); err != nil {
		return c, err
	}
	return c, c.validate()
}

func (c Config) validate() error {
	switch {
	case c.Namespace == "":
		return fmt.Errorf("NAMESPACE is required")
	case c.TargetDeployment == "":
		return fmt.Errorf("TARGET_DEPLOYMENT is required")
	case c.MinReplicas < 1:
		return fmt.Errorf("MIN_REPLICAS must be >= 1, got %d", c.MinReplicas)
	case c.MaxReplicas < c.MinReplicas:
		return fmt.Errorf("MAX_REPLICAS (%d) must be >= MIN_REPLICAS (%d)", c.MaxReplicas, c.MinReplicas)
	case c.ScaleUpStep < 1:
		return fmt.Errorf("SCALE_UP_STEP must be >= 1, got %d", c.ScaleUpStep)
	case c.PollInterval <= 0:
		return fmt.Errorf("POLL_INTERVAL must be positive")
	case c.ScaleDownDelay < 0:
		return fmt.Errorf("SCALE_DOWN_DELAY must not be negative")
	case c.PodStatusTimeout <= 0:
		return fmt.Errorf("POD_STATUS_TIMEOUT must be positive")
	case c.NotReadyGrace <= 0:
		return fmt.Errorf("NOT_READY_GRACE must be positive")
	case c.StuckAfter <= 0:
		return fmt.Errorf("STUCK_AFTER must be positive")
	case c.SummaryEvery <= 0:
		return fmt.Errorf("SUMMARY_EVERY must be positive")
	case c.HeartbeatEvery <= 0:
		return fmt.Errorf("HEARTBEAT_INTERVAL must be positive")
	}
	return nil
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func intEnv(key string, def int) (int, error) {
	v := os.Getenv(key)
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", key, err)
	}
	return n, nil
}

func durationEnv(key string, def time.Duration) (time.Duration, error) {
	v := os.Getenv(key)
	if v == "" {
		return def, nil
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", key, err)
	}
	return d, nil
}

func boolEnv(key string, def bool) (bool, error) {
	v := os.Getenv(key)
	if v == "" {
		return def, nil
	}
	b, err := strconv.ParseBool(v)
	if err != nil {
		return false, fmt.Errorf("%s: %w", key, err)
	}
	return b, nil
}

func hostname() string {
	h, _ := os.Hostname()
	return h
}
