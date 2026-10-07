package validator

import (
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
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

// reportRecorder is a RequestProcessor that only records ReportPendingRequestThreats calls.
type reportRecorder struct {
	mcp.RequestProcessor
	calls []reportCall
}

type reportCall struct {
	ctx     mcp.ValidationContext
	reports []*mcp.ValidationResult
}

func (r *reportRecorder) ReportPendingRequestThreats(vc *mcp.ValidationContext, pending []*mcp.ValidationResult) {
	r.calls = append(r.calls, reportCall{ctx: *vc, reports: pending})
}

func reportingService() (*Service, *reportRecorder) {
	rec := &reportRecorder{}
	s := sessionPassService(true)
	s.processor = rec
	return s, rec
}

func withSummary() (string, bool) { return testSummaryPayload, true }
func noSummary() (string, bool)   { return "", false }

// stubValidate returns per-payload verdicts and records the context each pass received.
type stubValidate struct {
	bareBlocked, sessBlocked bool
	bareErr, sessErr         error
	sessResult               *mcp.ProcessResult // overrides the session verdict when set
	bareModified             string
	sessDelay                time.Duration
	sessGate                 chan struct{} // when set, the session pass waits on it
	bareDelay                time.Duration
	bareCtx, sessCtx         atomic.Pointer[mcp.ValidationContext]
	sessMuted                atomic.Bool // session ctx had SkipThreat and DeferRequestThreatReport at call time
}

func (st *stubValidate) fn(vc *mcp.ValidationContext, payload string) (*mcp.ProcessResult, string, string, error) {
	vc.RequestPayload = payload
	if payload == testBarePayload {
		st.bareCtx.Store(vc)
		time.Sleep(st.bareDelay)
		return &mcp.ProcessResult{IsBlocked: st.bareBlocked, ModifiedPayload: st.bareModified}, payload, payload, st.bareErr
	}
	st.sessCtx.Store(vc)
	st.sessMuted.Store(vc.SkipThreat && vc.DeferRequestThreatReport)
	if st.sessGate != nil {
		<-st.sessGate
	}
	time.Sleep(st.sessDelay)
	if st.sessResult != nil {
		return st.sessResult, payload, payload, st.sessErr
	}
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

func TestValidateWithSessionPass_SessionPassIsMuted(t *testing.T) {
	s, rec := reportingService()
	st := &stubValidate{}
	valCtx := &mcp.ValidationContext{SkipThreat: false}
	s.validateWithSessionPass(valCtx, testSessionID, testBarePayload, withSummary, st.fn)

	bareCtx := st.bareCtx.Load()
	if bareCtx.SkipThreat || bareCtx.DeferRequestThreatReport {
		t.Fatal("bare pass must report normally")
	}
	if !st.sessMuted.Load() {
		t.Fatal("session pass must run muted with request redactions deferred")
	}
	if valCtx.SkipThreat || valCtx.DeferRequestThreatReport {
		t.Fatal("reporting flags must be restored on the written-back context")
	}
	if len(rec.calls) != 0 {
		t.Fatalf("clean session verdict must not report, got %d calls", len(rec.calls))
	}
}

func TestValidateWithSessionPass_BareBlockReportsNothingExtra(t *testing.T) {
	s, rec := reportingService()
	st := &stubValidate{bareBlocked: true, sessBlocked: true}
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	time.Sleep(20 * time.Millisecond) // let the abandoned session pass finish
	if len(rec.calls) != 0 {
		t.Fatalf("bare block must be the only report, got %d session reports", len(rec.calls))
	}
}

func TestValidateWithSessionPass_SessionBlockReported(t *testing.T) {
	s, rec := reportingService()
	meta := types.ThreatMetadata{PolicyName: "p1"}
	st := &stubValidate{sessResult: &mcp.ProcessResult{IsBlocked: true, Behaviour: "block", Metadata: meta}}
	valCtx := &mcp.ValidationContext{}
	s.validateWithSessionPass(valCtx, testSessionID, testBarePayload, withSummary, st.fn)

	if len(rec.calls) != 1 || len(rec.calls[0].reports) != 1 {
		t.Fatalf("want one block report, got %+v", rec.calls)
	}
	call := rec.calls[0]
	vr := call.reports[0]
	if vr.Allowed || vr.Behaviour != "block" || vr.Metadata.PolicyName != "p1" {
		t.Fatalf("unexpected block report: %+v", vr)
	}
	if call.ctx.SkipThreat || call.ctx.SessionID != testSessionID || call.ctx.RequestPayload != testSummaryPayload {
		t.Fatalf("report context: skip=%v sessionID=%q payload=%q", call.ctx.SkipThreat, call.ctx.SessionID, call.ctx.RequestPayload)
	}
}

func TestValidateWithSessionPass_SessionBlockRespectsSkipThreat(t *testing.T) {
	s, rec := reportingService()
	st := &stubValidate{sessBlocked: true}
	s.validateWithSessionPass(&mcp.ValidationContext{SkipThreat: true}, testSessionID, testBarePayload, withSummary, st.fn)
	if len(rec.calls) != 1 || !rec.calls[0].ctx.SkipThreat {
		t.Fatal("report must carry the caller's SkipThreat so the processor still honours it")
	}
}

func TestValidateWithSessionPass_SessionRedactionsReported(t *testing.T) {
	pending := []*mcp.ValidationResult{{Allowed: true, Modified: true, ModifiedPayload: "masked-1"}, {Allowed: true, Modified: true, ModifiedPayload: "masked-2"}}

	s, rec := reportingService()
	st := &stubValidate{sessResult: &mcp.ProcessResult{ModifiedPayload: "masked", PendingThreatReports: pending}}
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if len(rec.calls) != 1 || len(rec.calls[0].reports) != 2 || rec.calls[0].reports[0] != pending[0] {
		t.Fatalf("want deferred request redactions reported as-is, got %+v", rec.calls)
	}

	// Bare pass already redacted (and reported) — the session redaction is not reported again.
	s, rec = reportingService()
	st = &stubValidate{bareModified: "bare-masked", sessResult: &mcp.ProcessResult{ModifiedPayload: "masked", PendingThreatReports: pending}}
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if len(rec.calls) != 0 {
		t.Fatalf("redaction already reported by bare pass, got %d extra reports", len(rec.calls))
	}
}

func TestValidateWithSessionPass_ResponseRedactionRebuilt(t *testing.T) {
	s, rec := reportingService()
	meta := types.ThreatMetadata{PolicyName: "p1"}
	st := &stubValidate{sessResult: &mcp.ProcessResult{ModifiedPayload: "masked", Behaviour: "alert", Metadata: meta}}
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if len(rec.calls) != 1 || len(rec.calls[0].reports) != 1 {
		t.Fatalf("want one rebuilt redaction report, got %+v", rec.calls)
	}
	vr := rec.calls[0].reports[0]
	if !vr.Allowed || !vr.Modified || vr.ModifiedPayload != "" || vr.Metadata.PolicyName != "p1" {
		t.Fatalf("unexpected rebuilt redaction report: %+v", vr)
	}

	// No metadata to attribute it to — nothing is reported rather than an empty threat.
	s, rec = reportingService()
	st = &stubValidate{sessResult: &mcp.ProcessResult{ModifiedPayload: "masked"}}
	s.validateWithSessionPass(&mcp.ValidationContext{}, testSessionID, testBarePayload, withSummary, st.fn)
	if len(rec.calls) != 0 {
		t.Fatalf("unattributed redaction must not be reported, got %+v", rec.calls)
	}
}
