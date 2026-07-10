package adapter

import (
	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/plog"
)

type SourceAdapter interface {
	Name() string
	Match(resourceAttrs map[string]string, scopeName, eventName string) bool
	Adapt(record plog.LogRecord, resourceAttrs map[string]string, scopeName string, accountID int) []model.OtelIngestEvent
}

type Registry struct {
	adapters []SourceAdapter
	fallback SourceAdapter
}

func NewRegistry() *Registry {
	claude := &ClaudeCodeAdapter{}
	return &Registry{
		adapters: []SourceAdapter{claude},
		fallback: &RawPassthroughAdapter{},
	}
}

func (r *Registry) ProcessLogs(logs plog.Logs, accountID int) []model.OtelIngestEvent {
	var out []model.OtelIngestEvent
	for i := 0; i < logs.ResourceLogs().Len(); i++ {
		rl := logs.ResourceLogs().At(i)
		resourceAttrs := attributesToMap(rl.Resource().Attributes())
		for j := 0; j < rl.ScopeLogs().Len(); j++ {
			sl := rl.ScopeLogs().At(j)
			scopeName := sl.Scope().Name()
			for k := 0; k < sl.LogRecords().Len(); k++ {
				record := sl.LogRecords().At(k)
				eventName := eventNameFromRecord(record)
				adapter := r.selectAdapter(resourceAttrs, scopeName, eventName)
				events := adapter.Adapt(record, resourceAttrs, scopeName, accountID)
				out = append(out, events...)
			}
		}
	}
	return out
}

func (r *Registry) selectAdapter(resourceAttrs map[string]string, scopeName, eventName string) SourceAdapter {
	for _, a := range r.adapters {
		if a.Match(resourceAttrs, scopeName, eventName) {
			return a
		}
	}
	return r.fallback
}

func eventNameFromRecord(record plog.LogRecord) string {
	if name := record.EventName(); name != "" {
		return name
	}
	if body := record.Body().AsString(); body != "" {
		return body
	}
	return ""
}

func attributesToMap(attrs pcommon.Map) map[string]string {
	out := make(map[string]string, attrs.Len())
	attrs.Range(func(k string, v pcommon.Value) bool {
		out[k] = valueAsString(v)
		return true
	})
	return out
}

func recordAttributesToMap(record plog.LogRecord) map[string]string {
	return attributesToMap(record.Attributes())
}

func mergeAttrs(resourceAttrs map[string]string, recordAttrs map[string]string) map[string]string {
	out := make(map[string]string, len(resourceAttrs)+len(recordAttrs))
	for k, v := range resourceAttrs {
		out[k] = v
	}
	for k, v := range recordAttrs {
		out[k] = v
	}
	return out
}

func valueAsString(v pcommon.Value) string {
	return v.AsString()
}
