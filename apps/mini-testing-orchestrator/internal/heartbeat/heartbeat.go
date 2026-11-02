// Package heartbeat reports the orchestrator to Akto as a module, the way
// other modules do (ModuleInfoWorker in libs/utils): every interval it
// upserts a ModuleInfo of type MINI_TESTING_ORCHESTRATOR, so it shows up under
// Settings > Module Info with its last heartbeat and current scaling state.
//
// It needs a cyborg and dashboard that know that module type. An older cyborg
// rejects the heartbeat, which is logged and otherwise harmless.
package heartbeat

import (
	"context"
	"crypto/rand"
	"fmt"
	"log/slog"
	"time"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/akto"
)

const (
	ModuleType   = "MINI_TESTING_ORCHESTRATOR"
	path         = "/api/updateModuleInfoForHeartbeat"
	failLogEvery = 5 * time.Minute
)

type Sender struct {
	client   *akto.Client
	log      *slog.Logger
	interval time.Duration
	state    func() map[string]any

	id      string
	name    string
	version string
	started int64

	sentOnce    bool
	lastFailLog time.Time
}

// New builds a sender. name is the mini-testing module it scales; state is
// called for each heartbeat and becomes its additionalData.
func New(client *akto.Client, log *slog.Logger, interval time.Duration, name, version string, state func() map[string]any) *Sender {
	return &Sender{
		client: client, log: log, interval: interval, state: state,
		id: newUUID(), name: name, version: version, started: time.Now().Unix(),
	}
}

// Run sends a heartbeat now and then every interval until ctx is cancelled.
func (s *Sender) Run(ctx context.Context) {
	ticker := time.NewTicker(s.interval)
	defer ticker.Stop()
	for {
		s.send(ctx)
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (s *Sender) send(ctx context.Context) {
	data := s.state()
	s.client.EnsureAuth(ctx)
	data["tokenAuth"] = s.client.TokenStatus()
	body := map[string]any{"moduleInfo": map[string]any{
		"id":                    s.id,
		"moduleType":            ModuleType,
		"name":                  s.name,
		"currentVersion":        s.version,
		"startedTs":             s.started,
		"lastHeartbeatReceived": time.Now().Unix(),
		"additionalData":        data,
	}}
	if err := s.client.Post(ctx, path, body); err != nil {
		if time.Since(s.lastFailLog) >= failLogEvery {
			s.lastFailLog = time.Now()
			s.log.Warn("heartbeat to Akto failed; cyborg may not know module type "+ModuleType+" yet", "err", err)
		}
		return
	}
	if !s.sentOnce {
		s.sentOnce = true
		s.log.Info("heartbeat to Akto sent", "id", s.id, "name", s.name, "every", s.interval.String())
	}
}

// newUUID returns a random (version 4) UUID, a fresh module id per process
// as ModuleInfoWorker uses.
func newUUID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	b[6] = b[6]&0x0f | 0x40
	b[8] = b[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
