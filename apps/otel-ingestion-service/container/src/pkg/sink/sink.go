package sink

import "context"

type EventSink interface {
	Emit(ctx context.Context, batch Batch) error
}
