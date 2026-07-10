package sink

import "github.com/akto-api-security/otel-ingestion-service/pkg/model"

type Batch struct {
	AccountID int
	AuthToken string
	Events    []model.OtelIngestEvent
}
