package adapter

// Claude Code / Cowork OTel log adapter.
//
// Cowork exports logs using the Claude Code OTel schema:
//   - Setup / protocols: https://support.claude.com/en/articles/14477985-monitor-claude-cowork-activity-with-opentelemetry
//   - Event names & attributes: https://claude.com/docs/cowork/monitoring
import (
	"strings"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"go.opentelemetry.io/collector/pdata/plog"
)

var claudeServices = map[string]struct{}{
	"cowork":      {},
	"claude-code": {},
	"claude_code": {},
}

// Cowork event names without claude_code. prefix — see https://claude.com/docs/cowork/monitoring#events
var claudeEventNames = map[string]struct{}{
	"user_prompt":        {},
	"assistant_response": {},
	"tool_result":        {},
	"tool_decision":      {},
	"api_request":        {},
	"api_error":          {},
}

type ClaudeCodeAdapter struct{}

func (a *ClaudeCodeAdapter) Name() string { return "claude_code" }

func (a *ClaudeCodeAdapter) Match(resourceAttrs map[string]string, _ string, eventName string) bool {
	if strings.HasPrefix(eventName, "claude_code.") {
		return true
	}
	if svc := resourceAttrs["service.name"]; svc != "" {
		if _, ok := claudeServices[strings.ToLower(svc)]; ok {
			return true
		}
	}
	if _, ok := claudeEventNames[eventName]; ok {
		return true
	}
	return false
}

func (a *ClaudeCodeAdapter) Adapt(record plog.LogRecord, resourceAttrs map[string]string, scopeName string, accountID int) []model.OtelIngestEvent {
	recordAttrs := recordAttributesToMap(record)
	attrs := mergeAttrs(resourceAttrs, recordAttrs)

	eventName := eventNameFromRecord(record)
	if eventName != "" && !strings.HasPrefix(eventName, "claude_code.") {
		eventName = "claude_code." + eventName
	}

	correlationID := firstNonEmpty(attrs, "prompt.id", "prompt_id")
	if correlationID == "" {
		correlationID = attrs["session.id"]
	}

	return []model.OtelIngestEvent{{
		AccountID:     accountID,
		Source:        a.Name(),
		SignalType:    "logs",
		EventName:     eventName,
		Timestamp:     recordTimestamp(record),
		ResourceAttrs: copyMap(resourceAttrs),
		Attributes:    attrs,
		CorrelationID: correlationID,
	}}
}

func recordTimestamp(record plog.LogRecord) time.Time {
	ts := record.Timestamp().AsTime()
	if ts.IsZero() {
		return time.Now().UTC()
	}
	return ts.UTC()
}

func firstNonEmpty(attrs map[string]string, keys ...string) string {
	for _, k := range keys {
		if v := attrs[k]; v != "" {
			return v
		}
	}
	return ""
}

func copyMap(in map[string]string) map[string]string {
	out := make(map[string]string, len(in))
	for k, v := range in {
		out[k] = v
	}
	return out
}
