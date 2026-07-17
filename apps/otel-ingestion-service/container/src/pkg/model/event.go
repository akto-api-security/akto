package model

import "time"

type OtelIngestEvent struct {
	AccountID     int
	Source        string
	SignalType    string
	EventName     string
	Timestamp     time.Time
	Attributes    map[string]string
	CorrelationID string
}
