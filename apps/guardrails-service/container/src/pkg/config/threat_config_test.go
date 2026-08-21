package config

import "testing"

func TestLoadThreatKafkaConfig_Defaults(t *testing.T) {
	cfg := loadThreatKafkaConfig()

	if cfg.Enabled {
		t.Error("threat kafka must be off unless explicitly enabled")
	}
	if cfg.Topic != DefaultThreatTopic {
		t.Errorf("Topic = %q, want %q", cfg.Topic, DefaultThreatTopic)
	}
}

func TestLoadThreatClientConfig_Defaults(t *testing.T) {
	cfg := loadThreatClientConfig()

	if cfg.Enabled {
		t.Error("threat client must be off unless explicitly enabled")
	}
	if cfg.GroupID != DefaultThreatClientGroupID {
		t.Errorf("GroupID = %q, want %q", cfg.GroupID, DefaultThreatClientGroupID)
	}
	if cfg.Topic != DefaultThreatTopic {
		t.Errorf("Topic = %q, want %q", cfg.Topic, DefaultThreatTopic)
	}
	if cfg.MaxBackoffSec != 60 {
		t.Errorf("MaxBackoffSec = %d, want 60", cfg.MaxBackoffSec)
	}
}

// Single-broker installs should not have to set the GUARDRAILS_* broker vars.
func TestThreatBrokerFallsBackToSharedKafkaBroker(t *testing.T) {
	t.Setenv("KAFKA_BROKER_URL", "shared-broker:9092")

	if got := loadThreatKafkaConfig().BrokerURL; got != "shared-broker:9092" {
		t.Errorf("producer BrokerURL = %q, want the shared broker", got)
	}
	if got := loadThreatClientConfig().BrokerURL; got != "shared-broker:9092" {
		t.Errorf("threat client BrokerURL = %q, want the shared broker", got)
	}
}

// A dedicated threat broker overrides the shared one on both sides.
func TestThreatBrokerOverridesSharedBroker(t *testing.T) {
	t.Setenv("KAFKA_BROKER_URL", "shared-broker:9092")
	t.Setenv("GUARDRAILS_THREAT_KAFKA_BROKER_URL", "threat-broker:9092")

	if got := loadThreatKafkaConfig().BrokerURL; got != "threat-broker:9092" {
		t.Errorf("producer BrokerURL = %q, want the dedicated threat broker", got)
	}
	// The client inherits the producer's broker when it has no override of its own.
	if got := loadThreatClientConfig().BrokerURL; got != "threat-broker:9092" {
		t.Errorf("threat client BrokerURL = %q, want the dedicated threat broker", got)
	}

	t.Setenv("GUARDRAILS_THREAT_CLIENT_KAFKA_BROKER_URL", "client-broker:9092")
	if got := loadThreatClientConfig().BrokerURL; got != "client-broker:9092" {
		t.Errorf("threat client BrokerURL = %q, want its own override", got)
	}
}

// SASL falls back to the AKTO_KAFKA_* names the Helm charts already set, but
// deliberately NOT to KAFKA_USERNAME/PASSWORD, which belong to the traffic
// consumer and may target a different cluster.
func TestThreatSaslFallback(t *testing.T) {
	t.Setenv("KAFKA_USERNAME", "traffic-user")
	t.Setenv("KAFKA_PASSWORD", "traffic-pass")

	if got := loadThreatKafkaConfig().Username; got != "" {
		t.Errorf("Username = %q, must not inherit the traffic consumer's credentials", got)
	}

	t.Setenv("AKTO_KAFKA_USERNAME", "akto-user")
	t.Setenv("AKTO_KAFKA_PASSWORD", "akto-pass")

	if got := loadThreatKafkaConfig().Username; got != "akto-user" {
		t.Errorf("producer Username = %q, want akto-user", got)
	}
	if got := loadThreatClientConfig().Password; got != "akto-pass" {
		t.Errorf("client Password = %q, want akto-pass", got)
	}

	t.Setenv("GUARDRAILS_THREAT_CLIENT_KAFKA_USERNAME", "client-user")
	if got := loadThreatClientConfig().Username; got != "client-user" {
		t.Errorf("client Username = %q, want its own override", got)
	}
}
