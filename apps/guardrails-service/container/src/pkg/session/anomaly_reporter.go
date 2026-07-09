package session

import (
	"context"
	"encoding/json"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

func anomalySeverity(anomalyType string) string {
	switch anomalyType {
	case AnomalyTypeErrorRate:
		return "HIGH"
	default:
		return "MEDIUM"
	}
}

// ReportAnomaly reports an anomaly event to guardrail activity via ReportThreat.
// Designed to be called in a goroutine (fire-and-forget).
func ReportAnomaly(event *AnomalyEvent, params *models.ValidateRequestParams, policyName string, behaviour string, logger *zap.Logger) {
	if event == nil || params == nil {
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	reqHeaders := make(map[string]string)
	if params.RequestHeaders != "" {
		json.Unmarshal([]byte(params.RequestHeaders), &reqHeaders)
	}

	host := reqHeaders["Host"]
	if host == "" {
		host = reqHeaders["host"]
	}

	severity := anomalySeverity(event.AnomalyType)

	contextSource := types.ContextSource(params.ContextSource)
	if contextSource == "" {
		contextSource = types.ContextSourceAgentic
	}

	if err := mcp.ReportThreat(
		ctx,
		params.RequestPayload,
		"",
		types.ThreatMetadata{
			PolicyName:   policyName,
			RuleViolated: event.AnomalyType,
			Severity:     severity,
			Reason:       event.Details,
		},
		params.IP,
		params.Path,
		params.Method,
		reqHeaders,
		nil,
		0,
		contextSource,
		host,
		event.SessionID,
		behaviour,
	); err != nil {
		logger.Warn("Failed to report anomaly",
			zap.String("anomalyType", event.AnomalyType),
			zap.String("sessionID", event.SessionID),
			zap.Error(err))
	} else {
		logger.Info("Anomaly reported to guardrail activity",
			zap.String("anomalyType", event.AnomalyType),
			zap.String("sessionID", event.SessionID),
			zap.String("details", event.Details))
	}
}
