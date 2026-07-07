package sink

import (
	"context"

	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
)

type EventSink interface {
	Emit(ctx context.Context, events []model.OtelIngestEvent) error
}
