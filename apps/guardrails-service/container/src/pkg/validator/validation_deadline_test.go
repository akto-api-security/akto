package validator

import (
	"context"
	"testing"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/config"
)

// withValidationDeadline bounds every ProcessRequest/ProcessResponse call, batch items included.
func TestWithValidationDeadline(t *testing.T) {
	t.Run("nil config is a no-op", func(t *testing.T) {
		s := &Service{config: nil}
		ctx := context.Background()
		out, cancel := s.withValidationDeadline(ctx)
		defer cancel()
		if out != ctx {
			t.Error("expected the original context back when config is nil")
		}
		if _, ok := out.Deadline(); ok {
			t.Error("expected no deadline when config is nil")
		}
	})

	t.Run("non-positive timeout is a no-op", func(t *testing.T) {
		s := &Service{config: &config.Config{ValidationTimeoutMs: 0}}
		ctx := context.Background()
		out, cancel := s.withValidationDeadline(ctx)
		defer cancel()
		if out != ctx {
			t.Error("expected the original context back when ValidationTimeoutMs<=0")
		}
	})

	t.Run("positive timeout bounds the context", func(t *testing.T) {
		s := &Service{config: &config.Config{ValidationTimeoutMs: 2500}}
		ctx := context.Background()
		out, cancel := s.withValidationDeadline(ctx)
		defer cancel()

		deadline, ok := out.Deadline()
		if !ok {
			t.Fatal("expected a deadline")
		}
		remaining := time.Until(deadline)
		if remaining <= 0 || remaining > 2500*time.Millisecond {
			t.Errorf("expected ~2500ms remaining, got %v", remaining)
		}
	})

	t.Run("cancel releases the deadline immediately, independent of the parent", func(t *testing.T) {
		s := &Service{config: &config.Config{ValidationTimeoutMs: 60_000}}
		out, cancel := s.withValidationDeadline(context.Background())
		cancel()
		select {
		case <-out.Done():
		default:
			t.Fatal("expected the derived context to be done immediately after cancel")
		}
	})

	t.Run("an already-deadlined parent is still tightened, never loosened", func(t *testing.T) {
		s := &Service{config: &config.Config{ValidationTimeoutMs: 60_000}}
		parentCtx, parentCancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
		defer parentCancel()

		out, cancel := s.withValidationDeadline(parentCtx)
		defer cancel()

		select {
		case <-out.Done():
		case <-time.After(1 * time.Second):
			t.Fatal("child context must still respect a tighter parent deadline")
		}
	})
}
