package dbabstractor

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"
)

const (
	guardrailModuleType = "GUARDRAIL"
	HeartbeatInterval   = 60 * time.Second
)

// ModuleHeartbeat is the module_info row cyborg keeps for a running module.
type ModuleHeartbeat struct {
	ID                    string `json:"id"`
	ModuleType            string `json:"moduleType"`
	CurrentVersion        string `json:"currentVersion,omitempty"`
	StartedTs             int    `json:"startedTs"`
	LastHeartbeatReceived int    `json:"lastHeartbeatReceived"`
	Name                  string `json:"name"`
}

// SendHeartbeat tells cyborg this service is alive; cyborg also records which token the call was made with.
func (c *Client) SendHeartbeat(info ModuleHeartbeat) error {
	if _, err := c.SendRequest("POST", "/updateModuleInfoForHeartbeat", map[string]interface{}{"moduleInfo": info}); err != nil {
		return fmt.Errorf("updateModuleInfoForHeartbeat: %w", err)
	}
	return nil
}

// StartHeartbeat reports this service as a GUARDRAIL module every interval until ctx ends; it stays off without a service token.
func (c *Client) StartHeartbeat(ctx context.Context, interval time.Duration) {
	if !HasServiceToken() {
		c.logger.Info("Module heartbeat disabled (no DATABASE_ABSTRACTOR_SERVICE_TOKEN)")
		return
	}
	info := ModuleHeartbeat{
		ID:             uuid.NewString(),
		ModuleType:     guardrailModuleType,
		CurrentVersion: os.Getenv("GUARDRAILS_SERVICE_VERSION"),
		StartedTs:      int(time.Now().Unix()),
		Name:           moduleName(),
	}
	go func() {
		for {
			info.LastHeartbeatReceived = int(time.Now().Unix())
			if err := c.SendHeartbeat(info); err != nil {
				c.logger.Warn("Module heartbeat failed", zap.Error(err))
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(interval):
			}
		}
	}()
}

// moduleName is "guardrails-service:<host>"; unlike the "Default_" names it keeps the dashboard from offering a reboot.
func moduleName() string {
	host, err := os.Hostname()
	if err != nil || host == "" {
		host = uuid.NewString()
	}
	return "guardrails-service:" + host
}
