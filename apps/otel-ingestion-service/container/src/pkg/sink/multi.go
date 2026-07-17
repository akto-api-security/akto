package sink

import "context"

type MultiSink struct {
	sinks []EventSink
}

func NewMultiSink(sinks ...EventSink) EventSink {
	nonNil := make([]EventSink, 0, len(sinks))
	for _, s := range sinks {
		if s != nil {
			nonNil = append(nonNil, s)
		}
	}
	return &MultiSink{sinks: nonNil}
}

func (m *MultiSink) Emit(ctx context.Context, batch Batch) error {
	for _, s := range m.sinks {
		if err := s.Emit(ctx, batch); err != nil {
			return err
		}
	}
	return nil
}
