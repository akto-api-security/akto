package validator

import (
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"go.uber.org/zap"
)

const (
	testSessionID      = "sess-1"
	testBarePayload    = "bare"
	testSummaryPayload = "with-summary"
)

func sessionPassService(sessionEnabled bool) *Service {
	return &Service{logger: zap.NewNop(), config: &config.Config{SessionEnabled: sessionEnabled}}
}

func withSummary() (string, bool) { return testSummaryPayload, true }
func noSummary() (string, bool)   { return "", false }

// stubValidate returns per-payload verdicts and records the context each pass received.
type stubValidate struct {
	bareBlocked, sessBlocked bool
	bareErr, sessErr         error
	sessDelay                time.Duration
	sessGate                 chan struct{} // when set, the session pass waits on it
	bareDelay                time.Duration
	bareCtx, sessCtx         atomic.Pointer[mcp.ValidationContext]
}

func (st *stubValidate) fn(vc *mcp.ValidationContext, payload string) (*mcp.ProcessResult, string, string, error) {
	vc.RequestPayload = payload
	if payload == testBarePayload {
		st.bareCtx.Store(vc)
		time.Sleep(st.bareDelay)
		return &mcp.ProcessResult{IsBlocked: st.bareBlocked}, payload, payload, st.bareErr
	}
	st.sessCtx.Store(vc)
	if st.sessGate != nil {
		<-st.sessGate
	}
	time.Sleep(st.sessDelay)
	return &mcp.ProcessResult{IsBlocked: st.sessBlocked}, payload, payload, st.sessErr
}

func TestValidateWithSessionPass_BareBlockWinsWithoutWaiting(t *testing.T) {
	s := sessionPassService(true)
	gate := make(chan struct{})
	defer close(gate)
	st := &stubValidate{bareBlocked: true, sessGate: gate}
	valCtx := &mcp.ValidationContext{SessionID: testSessionID}

	done := make(chan struct{})
	var out passOutcome
	var sessionBlock bool
	go func() {
		out, sessionBlock = s.validateWithSessionPass(valCtx, testSessionID, testBarePayload, withSummary, st.fn)
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("blocking bare verdict waited on the session pass")
	}

	if !out.result.IsBlocked || out.fromSession || sessionBlock {
		t.Fatalf("want bare block, got blocked=%v fromSession=%v sessionBlock=%v", out.result.IsBlocked, out.fromSession, sessionBlock)
	}
	if out.evalPayload != testBarePayload || valCtx.SessionID != "" || valCtx.RequestPayload != testBarePayload {
		t.Fatalf("valCtx not the bare pass's: sessionID=%q payload=%q", valCtx.SessionID, valCtx.RequestPayload)
	}
}

func TestValidateWithSessionPass_SessionResultUsedWhenBareClean(t *testing.T) {
	s := sessionPassService(true)
	st := &stubValidate{sessBlocked: true}
	valCtx := &mcp.ValidationContext{}

	out, sessionBlock := s.validateWithSessionPass(valCtx, testSessionID, testBarePayload, withSummary, st.fn)

	if out.err != nil || !out.fromSession || !out.result.IsBlocked || !sessionBlock {
		t.Fatalf("want session block, got err=%v fromSession=%v blocked=%v sessionBlock=%v", out.err, out.fromSession, out.result.IsBlocked, sessionBlock)
	}
	if valCtx.SessionID != testSessionID || valCtx.RequestPayload != testSummaryPayload {
		t.Fatalf("valCtx not the session pass's: sessionID=%q payload=%q", valCtx.SessionID, valCtx.RequestPayload)
	}
	bareCtx, sessCtx := st.bareCtx.Load(), st.sessCtx.Load()
	if bareCtx == sessCtx || bareCtx == valCtx || sessCtx == valCtx {
		t.Fatal("passes must each get their own context copy")
	}
	if bareCtx.SessionID != "" || sessCtx.SessionID != testSessionID {
		t.Fatalf("pass session IDs: bare=%q session=%q", bareCtx.SessionID, sessCtx.SessionID)
	}
}

func TestValidateWithSessionPass_SessionCleanResultUsed(t *testing.T) {
	s := sessionPassService(true)
	st := &stubValidate{}
	out, sessionBlock := s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if !out.fromSession || out.result.IsBlocked || sessionBlock || out.evalPayload != testSummaryPayload {
		t.Fatalf("want clean session result, got fromSession=%v blocked=%v sessionBlock=%v eval=%q", out.fromSession, out.result.IsBlocked, sessionBlock, out.evalPayload)
	}
}

func TestValidateWithSessionPass_NoSummaryUsesBare(t *testing.T) {
	s := sessionPassService(true)
	st := &stubValidate{}
	valCtx := &mcp.ValidationContext{}
	out, sessionBlock := s.validateWithSessionPass(valCtx, testSessionID, testBarePayload, noSummary, st.fn)
	if out.fromSession || sessionBlock || out.evalPayload != testBarePayload || valCtx.SessionID != "" {
		t.Fatalf("want bare result, got fromSession=%v eval=%q sessionID=%q", out.fromSession, out.evalPayload, valCtx.SessionID)
	}
	if st.sessCtx.Load() != nil {
		t.Fatal("session pass ran without a summary")
	}
}

func TestValidateWithSessionPass_SessionDisabledSkipsSummary(t *testing.T) {
	s := sessionPassService(false)
	st := &stubValidate{}
	called := false
	summary := func() (string, bool) { called = true; return testSummaryPayload, true }
	out, _ := s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, summary, st.fn)
	if called || out.fromSession {
		t.Fatalf("session pass ran while disabled: summaryCalled=%v fromSession=%v", called, out.fromSession)
	}
}

func TestValidateWithSessionPass_Errors(t *testing.T) {
	s := sessionPassService(true)

	bareErr := errors.New("bare failed")
	out, _ := s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, (&stubValidate{bareErr: bareErr}).fn)
	if !errors.Is(out.err, bareErr) {
		t.Fatalf("want bare error, got %v", out.err)
	}

	sessErr := errors.New("session failed")
	out, sessionBlock := s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, (&stubValidate{sessErr: sessErr}).fn)
	if !errors.Is(out.err, sessErr) || sessionBlock {
		t.Fatalf("want session error, got %v sessionBlock=%v", out.err, sessionBlock)
	}
}

func TestValidateWithSessionPass_SessionPanicBecomesError(t *testing.T) {
	s := sessionPassService(true)
	st := &stubValidate{}
	panicky := func(vc *mcp.ValidationContext, payload string) (*mcp.ProcessResult, string, string, error) {
		if payload == testSummaryPayload {
			panic("boom")
		}
		return st.fn(vc, payload)
	}
	out, _ := s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, panicky)
	if out.err == nil {
		t.Fatal("want error from panicking session pass")
	}
}

func TestValidateWithSessionPass_PassesOverlap(t *testing.T) {
	s := sessionPassService(true)
	const d = 100 * time.Millisecond
	st := &stubValidate{bareDelay: d, sessDelay: d}
	start := time.Now()
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if elapsed := time.Since(start); elapsed >= d+d/2 {
		t.Fatalf("passes did not run concurrently: took %v", elapsed)
	}
}
