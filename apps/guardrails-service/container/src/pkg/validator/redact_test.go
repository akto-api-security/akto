package validator

import (
	"context"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"go.uber.org/zap"
)

// fakeRedactor stands in for the Anonymize scanner: it masks "secret-<n>" and counts calls, so a
// test can tell a cache hit (no call) from a miss.
type fakeRedactor struct {
	calls atomic.Int32
	basic bool
}

func (f *fakeRedactor) redact(_ context.Context, payload string) mcp.DisplayRedaction {
	f.calls.Add(1)
	masked := payload
	for _, w := range strings.Fields(strings.NewReplacer(`"`, " ", ",", " ").Replace(payload)) {
		if strings.HasPrefix(w, "secret-") {
			masked = strings.ReplaceAll(masked, w, "[SECRET]")
		}
	}
	return mcp.DisplayRedaction{Text: masked, Changed: masked != payload, Basic: f.basic}
}

func newRedactService() (*Service, *fakeRedactor) {
	f := &fakeRedactor{}
	return &Service{
		config:          &config.Config{RedactMaxPayloadBytes: 1024, RedactTimeoutMs: 5000, RedactConcurrency: 4},
		logger:          zap.NewNop(),
		redactionCache:  mcp.NewRedactionCache(100, time.Minute, 4096),
		displayRedactor: f.redact,
	}, f
}

func item(id, req, resp string) models.RedactPayloadItem {
	return models.RedactPayloadItem{ID: id, Method: "POST", Host: "api.example.com", Path: "/v1/chat", RequestPayload: req, ResponsePayload: resp}
}

func redactOne(s *Service, it models.RedactPayloadItem) models.RedactPayloadResult {
	return s.RedactPayloads(context.Background(), &models.RedactPayloadsRequest{Items: []models.RedactPayloadItem{it}})[0]
}

func TestRedactPayloads_RedactsThenServesFromCache(t *testing.T) {
	s, f := newRedactService()
	in := item("a", `{"note":"secret-42"}`, "")

	first := redactOne(s, in)
	if first.Request == nil || first.Request.Status != models.RedactionRedacted || first.Request.Payload != `{"note":"[SECRET]"}` {
		t.Fatalf("first call = %+v, want REDACTED with the masked payload", first.Request)
	}
	second := redactOne(s, in)
	if second.Request.Status != models.RedactionRedacted || second.Request.Payload != first.Request.Payload {
		t.Errorf("cached answer differs: %+v", second.Request)
	}
	if f.calls.Load() != 1 {
		t.Errorf("a cache hit must not redact again, calls = %d", f.calls.Load())
	}
	if hits, _ := s.redactionCache.Stats(); hits != 1 {
		t.Errorf("cache hits = %d, want 1", hits)
	}
}

func TestRedactPayloads_CleanPayloadIsUnchangedAndCached(t *testing.T) {
	s, f := newRedactService()
	in := item("a", `{"note":"all clear"}`, "")
	for i := 0; i < 2; i++ {
		got := redactOne(s, in)
		if got.Request == nil || got.Request.Status != models.RedactionUnchanged || got.Request.Payload != "" {
			t.Fatalf("call %d = %+v, want UNCHANGED with no payload", i, got.Request)
		}
	}
	if f.calls.Load() != 1 {
		t.Errorf("calls = %d, want 1", f.calls.Load())
	}
}

func TestRedactPayloads_SameResultForEveryCaller(t *testing.T) {
	// The result depends on the payload alone: the request carries nothing else that could change it.
	s, _ := newRedactService()
	a := redactOne(s, item("a", `{"note":"secret-1"}`, ""))
	b := redactOne(s, item("zzz", `{"note":"secret-1"}`, ""))
	if a.Request.Payload != b.Request.Payload || a.Request.Status != b.Request.Status {
		t.Errorf("same payload gave different results: %+v vs %+v", a.Request, b.Request)
	}
}

func TestRedactPayloads_BasicRedactionIsFlaggedAndNeverCached(t *testing.T) {
	s, f := newRedactService()
	f.basic = true
	in := item("a", `{"note":"secret-42"}`, "")
	for i := 0; i < 2; i++ {
		got := redactOne(s, in)
		if got.Request.Status != models.RedactionRedactedBasic || got.Request.Payload != `{"note":"[SECRET]"}` {
			t.Fatalf("call %d = %+v, want REDACTED_BASIC with the masked payload", i, got.Request)
		}
	}
	if f.calls.Load() != 2 || s.redactionCache.Len() != 0 {
		t.Errorf("a reduced-strength result must not be cached: calls=%d len=%d", f.calls.Load(), s.redactionCache.Len())
	}

	// Basic with nothing changed still carries the (identical) payload: it is not "unchanged".
	clean := redactOne(s, item("b", `{"note":"fine"}`, ""))
	if clean.Request.Status != models.RedactionRedactedBasic || clean.Request.Payload != `{"note":"fine"}` {
		t.Errorf("got %+v, want REDACTED_BASIC with the payload", clean.Request)
	}
}

func TestRedactPayloads_OversizePayloadIsUnavailable(t *testing.T) {
	s, f := newRedactService()
	got := redactOne(s, item("a", `{"note":"`+strings.Repeat("x", 2000)+`"}`, ""))
	if got.Request == nil || got.Request.Status != models.RedactionUnavailable {
		t.Fatalf("got %+v, want UNAVAILABLE", got.Request)
	}
	if f.calls.Load() != 0 {
		t.Error("an oversize payload must not be processed")
	}
}

func TestRedactPayloads_ItemsNotStartedBeforeTheDeadlineAreUnavailable(t *testing.T) {
	s, f := newRedactService()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	got := s.RedactPayloads(ctx, &models.RedactPayloadsRequest{Items: []models.RedactPayloadItem{item("a", `{"n":"secret-1"}`, "")}})[0]
	if got.Request.Status != models.RedactionUnavailable {
		t.Errorf("got %+v, want UNAVAILABLE", got.Request)
	}
	if f.calls.Load() != 0 {
		t.Error("nothing should be processed under an expired context")
	}
}

func TestRedactPayloads_UnsentSideHasNoResult(t *testing.T) {
	s, _ := newRedactService()
	got := redactOne(s, item("a", "", ""))
	if got.Request != nil || got.Response != nil || got.ID != "a" {
		t.Errorf("got %+v", got)
	}
}

func TestRedactPayloads_ManyItemsKeepTheirOwnResults(t *testing.T) {
	s, _ := newRedactService()
	items := []models.RedactPayloadItem{
		item("masked", `{"n":"secret-1"}`, `{"r":"secret-2"}`),
		item("clean", `{"n":"fine"}`, ""),
		item("empty", "", ""),
	}
	got := s.RedactPayloads(context.Background(), &models.RedactPayloadsRequest{Items: items})
	if len(got) != 3 || got[0].ID != "masked" || got[1].ID != "clean" || got[2].ID != "empty" {
		t.Fatalf("results out of order: %+v", got)
	}
	if got[0].Request.Status != models.RedactionRedacted || got[0].Response.Status != models.RedactionRedacted ||
		got[1].Request.Status != models.RedactionUnchanged || got[2].Request != nil {
		t.Errorf("statuses wrong: %+v %+v %+v", got[0], got[1], got[2])
	}
}

func TestRedactPayloads_DisabledCacheStillRedacts(t *testing.T) {
	s, f := newRedactService()
	s.redactionCache = nil
	in := item("a", `{"n":"secret-1"}`, "")
	if got := redactOne(s, in); got.Request.Status != models.RedactionRedacted {
		t.Fatalf("got %+v", got.Request)
	}
	redactOne(s, in)
	if f.calls.Load() != 2 {
		t.Errorf("with the cache off every call redacts, calls = %d", f.calls.Load())
	}
}
