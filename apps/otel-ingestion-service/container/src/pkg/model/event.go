package model

import "time"

type OtelIngestEvent struct {
	AccountID     int
	Source        string
	SignalType    string
	EventName     string
	Timestamp     time.Time
	ResourceAttrs map[string]string
	Attributes    map[string]string
	CorrelationID string
}
