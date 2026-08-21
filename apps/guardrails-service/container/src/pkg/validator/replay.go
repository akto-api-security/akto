package validator

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
)

// Replay re-evaluates already-recorded violations so the dashboard can compare how many a policy
// catches now against how many an edited version would catch.
//
// It reports two counts over the same events rather than judging each violation individually,
// and that choice is what makes the numbers trustworthy. Stored payloads are anonymized before
// they are persisted, so a single verdict ("would this still be caught?") is often unanswerable:
// the text that fired the rule may be gone, and a rule with a high minMatchCount can be
// arithmetically unable to fire on what survives. Measured on production data, only ~9% of
// violations were individually re-detectable for that reason.
//
// Running both policies over the identical payloads makes that suppression common-mode: it
// lowers both counts by the same amount and cancels out of the difference. So "current catches
// 6, your edit catches 40" stays meaningful even where "was this one still caught?" is not.
//
// Replay never reports threats and never touches session state — see ReplayWithPolicy.

// Skip reasons. An item is skipped only when it cannot be compared at all; anything comparable
// is counted for both policies even if neither detects it.
const (
	// SkipReasonBadEnvelope — the stored latestApiOrig was not parseable.
	SkipReasonBadEnvelope = "bad_envelope"
	// SkipReasonNoPayload — nothing to evaluate on any side the policy applies to.
	SkipReasonNoPayload = "no_payload"
	// SkipReasonNoRealResponse — the request was blocked before reaching the origin, so the
	// stored responsePayload is only our own synthetic block body.
	SkipReasonNoRealResponse = "no_real_response"
	// SkipReasonNotRequestTraffic — raised by the skill/config scanner, not request validation.
	SkipReasonNotRequestTraffic = "not_request_traffic"
	// SkipReasonScannerUnavailable — the scanners did not answer, so this event's verdict would
	// not be comparable between the two policies. Excluded from both counts rather than
	// silently recorded as "not detected" (the enforcement library fails open).
	SkipReasonScannerUnavailable = "scanner_unavailable"
)

// blockedResponseSeparator mirrors the unexported constant of the same name in
// mcp-endpoint-shield's processor: when a *response* is blocked, the reported payload is
// rawResponse + separator + blockJSON.
const blockedResponseSeparator = "\n\n"

// replayConcurrency bounds in-flight evaluations per batch. Each one can reach the LLM/model
// scanners, so this is a cost and back-pressure limit as much as a latency one.
//
// Lowered from 8 after measurement: at 8-wide this saturated the scanner API and produced 1408
// timeouts against 1014 successes in one run. Because the enforcement library fails open, each
// timeout became a silent non-detection — over-parallelising did not just slow the run, it
// corrupted the result.
var replayConcurrency = envInt("GUARDRAILS_REPLAY_CONCURRENCY", 4)

// replayValidationTimeoutMs bounds one replayed evaluation.
//
// Deliberately NOT config.ValidationTimeoutMs. That budget exists so live traffic fails open
// before the caller's client timeout — a latency guarantee that only makes sense inline. Replay
// is offline analysis with nobody waiting on it, so inheriting it turned slow scanners into
// fabricated verdicts.
var replayValidationTimeoutMs = envInt("GUARDRAILS_REPLAY_TIMEOUT_MS", 60000)

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			return n
		}
	}
	return def
}

// ReplayItem is one recorded violation to re-evaluate.
type ReplayItem struct {
	// ID is opaque here — echoed back so the caller can join verdicts to its own rows.
	ID string `json:"id"`
	// Envelope is the stored Akto payload envelope (malicious_events.latestApiOrig).
	Envelope string `json:"envelope"`
}

// ReplayVerdict is the outcome for one ReplayItem. When SkipReason is set the item was not
// comparable and both Detected fields are meaningless.
type ReplayVerdict struct {
	ID string `json:"id"`
	// Detected is whether the policy under test matched.
	Detected bool `json:"detected"`
	// BaselineDetected is whether the currently-saved policy matched. Only populated when the
	// caller supplied a baseline.
	BaselineDetected bool   `json:"baselineDetected"`
	Behaviour        string `json:"behaviour,omitempty"`
	Reason           string `json:"reason,omitempty"`
	// Side is which side matched ("request" or "response"), set only when Detected.
	Side       string `json:"side,omitempty"`
	SkipReason string `json:"skipReason,omitempty"`
}

// preparedPolicy is a policy converted once and reused across every item in the batch.
//
// It carries its OWN applyOnRequest/applyOnResponse rather than letting the caller supply them:
// the baseline and the draft can target different sides, and evaluating the baseline with the
// draft's flags would score it on sides its rules were never meant to see.
type preparedPolicy struct {
	policies        []types.Policy
	compiledRules   map[string]*regexp.Regexp
	applyOnRequest  bool
	applyOnResponse bool
}

func preparePolicy(p *mcp.GuardrailsPolicy, logger *zap.Logger) preparedPolicy {
	// A disabled policy would allow everything and make the comparison meaningless.
	p.Active = true
	converted := mcp.ConvertGuardrailsToPolicy(p)
	return preparedPolicy{
		policies: []types.Policy{converted},
		// The provided policy is not in the cache, so its regexes were never compiled there;
		// compile them here or every regex rule silently fails to match.
		compiledRules:   compileReplayRegexRules([]types.Policy{converted}, logger),
		applyOnRequest:  p.ApplyOnRequest,
		applyOnResponse: p.ApplyOnResponse,
	}
}

// ReplayWithPolicy evaluates each item against policy, and against baseline when supplied,
// returning one verdict per item in input order.
//
// Deliberate departures from the live validate path, all of which keep replay side-effect-free
// and the two counts comparable:
//
//   - skipThreat is forced on, so replays never write malicious_events.
//   - sessionID is left empty, which makes CheckAndHandleMaliciousSession,
//     TrackRequestAndGenerateSummary and GetModifiedPayloadWithSummary no-ops. Do not "improve
//     fidelity" by forwarding the original event's session headers: the first item that tripped
//     the malicious-session check would short-circuit every later item to blocked.
//   - server/device/approved-server filtering is skipped, because the question is whether the
//     policy's rules match this payload, not whether it targets this server.
func (s *Service) ReplayWithPolicy(
	ctx context.Context,
	items []ReplayItem,
	policy *mcp.GuardrailsPolicy,
	baseline *mcp.GuardrailsPolicy,
	contextSource string,
) ([]ReplayVerdict, error) {
	if policy == nil {
		return nil, fmt.Errorf("policy is required")
	}
	if len(items) == 0 {
		return []ReplayVerdict{}, nil
	}

	if s.schemaFetcher != nil {
		s.schemaFetcher.RefreshIfNeeded()
	}

	prepared := preparePolicy(policy, s.logger)
	var preparedBaseline *preparedPolicy
	if baseline != nil {
		b := preparePolicy(baseline, s.logger)
		preparedBaseline = &b
	}

	// The allow list only feeds blocked-host rules. Failing the whole comparison because a
	// side-channel fetch was unavailable would be worse than evaluating without it, and it
	// affects both policies identically either way.
	var mcpAllowedHostList []types.McpAllowedList
	if s.config != nil {
		list, err := s.getMcpAllowedHostList()
		if err != nil {
			s.logger.Warn("ReplayWithPolicy - MCP allow list unavailable; continuing without it",
				zap.Error(err))
		} else {
			mcpAllowedHostList = list
		}
	}

	s.logger.Info("ReplayWithPolicy - starting",
		zap.String("policyName", policy.Name),
		zap.Bool("withBaseline", baseline != nil),
		zap.String("contextSource", contextSource),
		zap.Int("items", len(items)),
		zap.Bool("applyOnRequest", policy.ApplyOnRequest),
		zap.Bool("applyOnResponse", policy.ApplyOnResponse),
		zap.Int("compiledRules", len(prepared.compiledRules)))

	verdicts := make([]ReplayVerdict, len(items))
	g, gctx := errgroup.WithContext(ctx)
	g.SetLimit(replayConcurrency)

	for i, item := range items {
		i, item := i, item
		g.Go(func() error {
			verdicts[i] = s.compareOne(gctx, item, prepared, preparedBaseline, mcpAllowedHostList, contextSource)
			return nil
		})
	}
	// compareOne never returns an error (per-item failures become skip reasons), so this only
	// surfaces context cancellation.
	if err := g.Wait(); err != nil {
		return nil, err
	}
	return verdicts, nil
}

// compareOne evaluates a single item against the policy under test and, when given, the
// baseline. It degrades to a skip verdict rather than an error so one malformed row cannot fail
// the whole batch.
//
// An item is skipped if EITHER evaluation is inconclusive: comparing a conclusive verdict
// against an unknown one would attribute a scanner failure to the policy edit.
func (s *Service) compareOne(
	ctx context.Context,
	item ReplayItem,
	prepared preparedPolicy,
	baseline *preparedPolicy,
	mcpAllowedHostList []types.McpAllowedList,
	contextSource string,
) ReplayVerdict {
	verdict := ReplayVerdict{ID: item.ID}

	var envelope replayEnvelope
	if err := json.Unmarshal([]byte(item.Envelope), &envelope); err != nil {
		s.logger.Warn("ReplayWithPolicy - unparseable envelope",
			zap.String("id", item.ID), zap.Error(err))
		verdict.SkipReason = SkipReasonBadEnvelope
		return verdict
	}
	if isNonReplayableScan(envelope) {
		verdict.SkipReason = SkipReasonNotRequestTraffic
		return verdict
	}
	params := envelope.toParams(contextSource)

	detected, side, res, skip := s.evaluate(ctx, &params, prepared, mcpAllowedHostList)
	if skip != "" {
		verdict.SkipReason = skip
		return verdict
	}
	verdict.Detected = detected
	if detected {
		verdict.Side = side
		if res != nil {
			verdict.Behaviour = res.Behaviour
			verdict.Reason = extractReasonFromBlockedResponse(res.BlockedResponse)
		}
	}

	if baseline != nil {
		baseDetected, _, _, baseSkip := s.evaluate(ctx, &params, *baseline, mcpAllowedHostList)
		if baseSkip != "" {
			// Without a comparable baseline the diff would be misleading.
			return ReplayVerdict{ID: item.ID, SkipReason: baseSkip}
		}
		verdict.BaselineDetected = baseDetected
	}
	return verdict
}

// evaluate runs the sides the policy applies to and returns the first match. skip is non-empty
// when nothing could be evaluated.
func (s *Service) evaluate(
	ctx context.Context,
	params *models.ValidateRequestParams,
	prepared preparedPolicy,
	mcpAllowedHostList []types.McpAllowedList,
) (detected bool, side string, res *mcp.ProcessResult, skip string) {
	// The response side needs the block suffix our own processor appended at detection time
	// stripped off, or we would feed the block reason — which quotes the offending content —
	// back into the detector and self-trigger.
	responseBody, pureBlock := stripBlockSuffix(params.ResponsePayload)

	evaluated := false
	deferredSkip := ""

	if prepared.applyOnRequest && strings.TrimSpace(params.RequestPayload) != "" {
		d, r, conclusive := s.replaySide(ctx, params, prepared, mcpAllowedHostList, params.RequestPayload, true)
		switch {
		case d:
			return true, "request", r, ""
		case !conclusive:
			deferredSkip = SkipReasonScannerUnavailable
		default:
			evaluated = true
		}
	}

	if prepared.applyOnResponse {
		switch {
		case pureBlock:
			setIfEmpty(&deferredSkip, SkipReasonNoRealResponse)
		case strings.TrimSpace(responseBody) == "":
			setIfEmpty(&deferredSkip, SkipReasonNoPayload)
		default:
			d, r, conclusive := s.replaySide(ctx, params, prepared, mcpAllowedHostList, responseBody, false)
			switch {
			case d:
				return true, "response", r, ""
			case !conclusive:
				setIfEmpty(&deferredSkip, SkipReasonScannerUnavailable)
			default:
				evaluated = true
			}
		}
	}

	// A scanner failure invalidates the comparison even if the other side answered.
	if deferredSkip == SkipReasonScannerUnavailable {
		return false, "", nil, deferredSkip
	}
	if !evaluated {
		if deferredSkip == "" {
			deferredSkip = SkipReasonNoPayload
		}
		return false, "", nil, deferredSkip
	}
	return false, "", nil, ""
}

// replaySide runs one side of the validation. conclusive is false when the scanners did not
// answer, in which case a non-detection proves nothing.
func (s *Service) replaySide(
	ctx context.Context,
	params *models.ValidateRequestParams,
	prepared preparedPolicy,
	mcpAllowedHostList []types.McpAllowedList,
	body string,
	isRequest bool,
) (detected bool, res *mcp.ProcessResult, conclusive bool) {
	payload := s.extractPayloadForValidation(body, params.Method, params.Path, isRequest)

	reqCtxPayload, respCtxPayload := params.RequestPayload, params.ResponsePayload
	if isRequest {
		reqCtxPayload = payload
	} else {
		respCtxPayload = payload
	}

	valCtx := s.validationContextFromParams(
		params, "", reqCtxPayload, respCtxPayload, "ReplayWithPolicy", mcpAllowedHostList, prepared.compiledRules)

	procCtx, cancel := s.withReplayDeadline(ctx)
	defer cancel()

	var err error
	if isRequest {
		res, err = s.processor.ProcessRequestParallel(procCtx, payload, valCtx, prepared.policies, nil, false)
	} else {
		res, err = s.processor.ProcessResponseParallel(procCtx, payload, valCtx, prepared.policies)
	}
	if err != nil || res == nil {
		s.logger.Warn("ReplayWithPolicy - validation errored; verdict is inconclusive",
			zap.Bool("isRequest", isRequest), zap.Error(err))
		return false, nil, false
	}
	// A detection stands on its own. A NON-detection only counts if the scanners actually
	// answered: the enforcement library fails open, so a timed-out scanner returns
	// IsBlocked=false indistinguishable from a genuine pass.
	if !res.IsBlocked && procCtx.Err() != nil {
		s.logger.Warn("ReplayWithPolicy - deadline exceeded before scanners answered; inconclusive",
			zap.Bool("isRequest", isRequest), zap.Error(procCtx.Err()))
		return false, res, false
	}
	return res.IsBlocked, res, true
}

// withReplayDeadline bounds a single replayed evaluation, generously — see
// replayValidationTimeoutMs for why this does not reuse the live-traffic budget.
func (s *Service) withReplayDeadline(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, time.Duration(replayValidationTimeoutMs)*time.Millisecond)
}

// setIfEmpty assigns only when unset, so the earliest (most specific) skip reason survives.
func setIfEmpty(dst *string, value string) {
	if *dst == "" {
		*dst = value
	}
}

// flexString decodes a JSON value of any type into a string: a JSON string yields its value,
// anything else yields its raw JSON literal.
//
// The stored envelope is NOT type-stable across producers — statusCode and time are written as
// numbers by the guardrails flow but declared as strings on models.ValidateRequestParams, and
// headers appear both as JSON-encoded strings and as objects. Decoding the envelope straight
// into ValidateRequestParams therefore fails on real data with "cannot unmarshal number into ...
// of type string" and loses every item. Falling back to the raw literal gives exactly what
// downstream wants in both shapes: 200 becomes "200", and a headers object becomes the JSON text
// validationContextFromParams then unmarshals.
type flexString string

func (f *flexString) UnmarshalJSON(b []byte) error {
	trimmed := bytes.TrimSpace(b)
	if len(trimmed) == 0 || string(trimmed) == "null" {
		*f = ""
		return nil
	}
	if trimmed[0] == '"' {
		var s string
		if err := json.Unmarshal(trimmed, &s); err != nil {
			return err
		}
		*f = flexString(s)
		return nil
	}
	*f = flexString(trimmed)
	return nil
}

// replayEnvelope is the subset of the stored envelope replay needs, decoded tolerantly.
type replayEnvelope struct {
	Method          flexString `json:"method"`
	Path            flexString `json:"path"`
	RequestPayload  flexString `json:"requestPayload"`
	ResponsePayload flexString `json:"responsePayload"`
	RequestHeaders  flexString `json:"requestHeaders"`
	ResponseHeaders flexString `json:"responseHeaders"`
	StatusCode      flexString `json:"statusCode"`
	IP              flexString `json:"ip"`
	DestIP          flexString `json:"destIp"`
	AktoAccountID   flexString `json:"akto_account_id"`
	Tag             flexString `json:"tag"`
}

// toParams rebuilds the traffic params the validate path expects. skipThreat is always on: a
// replay must never write malicious_events.
func (e replayEnvelope) toParams(contextSource string) models.ValidateRequestParams {
	skipThreat := true
	return models.ValidateRequestParams{
		Method:          string(e.Method),
		Path:            string(e.Path),
		RequestPayload:  string(e.RequestPayload),
		ResponsePayload: string(e.ResponsePayload),
		RequestHeaders:  string(e.RequestHeaders),
		ResponseHeaders: string(e.ResponseHeaders),
		StatusCode:      string(e.StatusCode),
		IP:              string(e.IP),
		DestIP:          string(e.DestIP),
		AktoAccountID:   string(e.AktoAccountID),
		Tag:             string(e.Tag),
		ContextSource:   contextSource,
		SkipThreat:      &skipThreat,
	}
}

// scanOnlyPayloadKeys mark a payload produced by the skill/config scanners rather than by
// request validation. The dashboard's violation table keys off the same fields.
var scanOnlyPayloadKeys = []string{"skill_content", "config_content"}

// isNonReplayableScan reports whether this violation came from the skill/config scanner instead
// of the request-validation path.
//
// Those events are raised by mcp-endpoint-shield's skill detector, which runs its own scanning
// flow and stores a payload shaped like {"agent":…, "file_path":…, "skill_content":…} — not an
// HTTP request. Neither policy can match it through ProcessRequestParallel, so it would
// contribute nothing to either count while still costing a scanner call.
func isNonReplayableScan(env replayEnvelope) bool {
	if strings.EqualFold(string(env.Method), "CONFIG_SCAN") {
		return true
	}
	req := strings.TrimSpace(string(env.RequestPayload))
	if req == "" || req[0] != '{' {
		return false
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal([]byte(req), &fields); err != nil {
		return false
	}
	for _, key := range scanOnlyPayloadKeys {
		if _, ok := fields[key]; ok {
			return true
		}
	}
	return false
}

// stripBlockSuffix separates a recorded responsePayload from the policy-block JSON the processor
// attaches when it enforces a violation. Two shapes exist:
//
//	response blocked -> "<real upstream body>" + "\n\n" + <blockJSON>
//	request blocked  -> <blockJSON> only (the origin was never called)
//
// Returns the usable upstream body, and pureBlock=true for the second shape.
func stripBlockSuffix(raw string) (body string, pureBlock bool) {
	if strings.TrimSpace(raw) == "" {
		return raw, false
	}
	if isPolicyBlockResponse(raw) {
		return "", true
	}
	idx := strings.LastIndex(raw, blockedResponseSeparator)
	if idx < 0 {
		return raw, false
	}
	if !isPolicyBlockResponse(raw[idx+len(blockedResponseSeparator):]) {
		return raw, false
	}
	return raw[:idx], false
}

// isPolicyBlockResponse reports whether s is one of our own block bodies, matching the marker
// mcp-endpoint-shield stamps into every blocked response.
func isPolicyBlockResponse(s string) bool {
	var v struct {
		Error struct {
			Data struct {
				BlockedBy string `json:"blocked_by"`
			} `json:"data"`
		} `json:"error"`
	}
	if err := json.Unmarshal([]byte(strings.TrimSpace(s)), &v); err != nil {
		return false
	}
	return v.Error.Data.BlockedBy == "policy_validator"
}

// compileReplayRegexRules pre-compiles every "regex" rule across the given policies, keyed by
// pattern, for mcp.ValidationContext.CompiledRegexRules. Patterns that fail to compile are logged
// and dropped rather than failing the whole set.
//
// Intentionally a replay-local copy of the equivalent loop in fetchAndParsePolicies rather than a
// shared helper: that path runs on every policy refresh for live traffic, and keeping it untouched
// means replay cannot regress it. The duplication is small and the two are free to diverge.
func compileReplayRegexRules(policies []types.Policy, logger *zap.Logger) map[string]*regexp.Regexp {
	compiled := make(map[string]*regexp.Regexp)
	add := func(rules []types.FilterRule) {
		for _, rule := range rules {
			if rule.Type != "regex" || rule.Pattern == "" {
				continue
			}
			if _, done := compiled[rule.Pattern]; done {
				continue
			}
			re, err := regexp.Compile(rule.Pattern)
			if err != nil {
				logger.Warn("Failed to compile regex pattern",
					zap.String("pattern", rule.Pattern), zap.Error(err))
				continue
			}
			compiled[rule.Pattern] = re
		}
	}
	for _, policy := range policies {
		add(policy.Filters.RequestPayload)
		add(policy.Filters.ResponsePayload)
	}
	return compiled
}
