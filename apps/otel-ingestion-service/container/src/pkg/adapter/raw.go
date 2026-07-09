package adapter

import (
	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"github.com/akto-api-security/otel-ingestion-service/pkg/strutil"
	"go.opentelemetry.io/collector/pdata/plog"
)

type RawPassthroughAdapter struct{}

func (a *RawPassthroughAdapter) Name() string { return "raw" }

func (a *RawPassthroughAdapter) Match(_ map[string]string, _ string, _ string) bool {
	return true
}

func (a *RawPassthroughAdapter) Adapt(record plog.LogRecord, resourceAttrs map[string]string, scopeName string, accountID int) []model.OtelIngestEvent {
	recordAttrs := recordAttributesToMap(record)
	attrs := mergeAttrs(resourceAttrs, recordAttrs)
	eventName := eventNameFromRecord(record)
	if eventName == "" {
		eventName = "unknown"
	}

	return []model.OtelIngestEvent{{
		AccountID:     accountID,
		Source:        a.Name(),
		SignalType:    "logs",
		EventName:     eventName,
		Timestamp:     recordTimestamp(record),
		ResourceAttrs: copyMap(resourceAttrs),
		Attributes:    attrs,
		CorrelationID: strutil.FirstNonEmpty(attrs, "prompt.id", "trace_id", "session.id"),
	}}
}
