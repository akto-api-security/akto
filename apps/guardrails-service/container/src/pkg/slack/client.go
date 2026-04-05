package slack

import (
	"bytes"
	"encoding/json"
	"net/http"
	"os"

	"go.uber.org/zap"
)

const webhookEnvVar = "AKTO_SLACK_ALERT_WEBHOOK"

// SendAlert sends a Slack alert to the webhook URL configured via AKTO_SLACK_ALERT_WEBHOOK.
// If the env variable is not set, the alert is skipped with a warning log.
func SendAlert(logger *zap.Logger, message string) {
	webhookURL := os.Getenv(webhookEnvVar)
	if webhookURL == "" {
		logger.Warn("No Slack webhook configured (AKTO_SLACK_ALERT_WEBHOOK not set), skipping alert",
			zap.String("message", message))
		return
	}

	payload := map[string]interface{}{
		"text": message,
	}
	body, err := json.Marshal(payload)
	if err != nil {
		logger.Error("Failed to marshal Slack alert payload", zap.Error(err))
		return
	}

	resp, err := http.Post(webhookURL, "application/json", bytes.NewBuffer(body))
	if err != nil {
		logger.Error("Failed to send Slack alert", zap.Error(err))
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		logger.Error("Slack alert returned non-OK status", zap.Int("statusCode", resp.StatusCode))
	}
}
