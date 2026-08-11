package promptguard

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"net/http"
	"strconv"
	"strings"
	"testing"
	"time"
)

// signBody produces the Standard Webhooks headers for a body signed with secret,
// mirroring what Anthropic sends, so the verifier can be exercised end to end.
func signBody(secret, messageID string, ts int64, body []byte) http.Header {
	key, _ := base64.StdEncoding.DecodeString(strings.TrimPrefix(secret, "whsec_"))
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte(messageID + "." + strconv.FormatInt(ts, 10) + "."))
	mac.Write(body)
	sig := "v1," + base64.StdEncoding.EncodeToString(mac.Sum(nil))

	h := http.Header{}
	h.Set("webhook-id", messageID)
	h.Set("webhook-timestamp", strconv.FormatInt(ts, 10))
	h.Set("webhook-signature", sig)
	return h
}

// testSecret is "whsec_" + base64("0123456789abcdef0123456789abcdef").
var testSecret = "whsec_" + base64.StdEncoding.EncodeToString([]byte("0123456789abcdef0123456789abcdef"))

func TestVerify_ValidSignature(t *testing.T) {
	p := NewClaudeProvider(testSecret)
	body := []byte(`{"type":"prompt"}`)
	h := signBody(testSecret, "req_1", time.Now().Unix(), body)
	if got := p.Verify(h, body); got != SigVerified {
		t.Fatalf("want SigVerified, got %v", got)
	}
}

func TestVerify_TamperedBodyFails(t *testing.T) {
	p := NewClaudeProvider(testSecret)
	h := signBody(testSecret, "req_1", time.Now().Unix(), []byte(`{"type":"prompt"}`))
	if got := p.Verify(h, []byte(`{"type":"prompt","x":1}`)); got != SigInvalid {
		t.Fatalf("want SigInvalid on tampered body, got %v", got)
	}
}

func TestVerify_MissingHeadersUnsigned(t *testing.T) {
	p := NewClaudeProvider(testSecret)
	if got := p.Verify(http.Header{}, []byte(`{}`)); got != SigUnsigned {
		t.Fatalf("want SigUnsigned, got %v", got)
	}
}

func TestVerify_NoSecretNotConfigured(t *testing.T) {
	p := NewClaudeProvider() // no secrets
	h := signBody(testSecret, "req_1", time.Now().Unix(), []byte(`{}`))
	if got := p.Verify(h, []byte(`{}`)); got != SigNotConfigured {
		t.Fatalf("want SigNotConfigured, got %v", got)
	}
}

func TestVerify_StaleTimestampFails(t *testing.T) {
	p := NewClaudeProvider(testSecret)
	body := []byte(`{}`)
	h := signBody(testSecret, "req_1", time.Now().Unix()-toleranceSeconds-10, body)
	if got := p.Verify(h, body); got != SigInvalid {
		t.Fatalf("want SigInvalid on stale timestamp, got %v", got)
	}
}

func TestVerify_RotationAcceptsPreviousSecret(t *testing.T) {
	prev := testSecret
	current := "whsec_" + base64.StdEncoding.EncodeToString([]byte("ffffffffffffffffffffffffffffffff"))
	p := NewClaudeProvider(current, prev) // current + previous during rotation

	body := []byte(`{"type":"prompt"}`)
	h := signBody(prev, "req_1", time.Now().Unix(), body) // straggler signed with old secret
	if got := p.Verify(h, body); got != SigVerified {
		t.Fatalf("want SigVerified for previous-secret signature, got %v", got)
	}
}

func TestParse_FlattensTranscript(t *testing.T) {
	p := NewClaudeProvider()
	body := []byte(`{
		"type": "prompt",
		"request_id": "req_abc123",
		"session_id": "sess_1",
		"model": "claude-sonnet-4-5",
		"actor": {"id": "user_1", "email_address": "alice@example.com"},
		"source": {"application": "claude-code"},
		"messages": [
			{"role": "user", "content": [
				{"type": "text", "text": "summarize the report"},
				{"type": "attachment", "file_name": "q2.pdf", "text": "Q2 revenue grew 14%"}
			]},
			{"role": "assistant", "content": [
				{"type": "tool_use", "tool_name": "search", "input": {"q": "revenue"}}
			]},
			{"role": "user", "content": [
				{"type": "tool_result", "tool_name": "search", "content": "no rows", "is_error": true}
			]}
		]
	}`)

	in, short, err := p.Parse(body)
	if err != nil || short {
		t.Fatalf("unexpected: err=%v short=%v", err, short)
	}
	if in.RequestID != "req_abc123" || in.SessionID != "sess_1" || in.Model != "claude-sonnet-4-5" {
		t.Fatalf("metadata not parsed: %+v", in)
	}
	if in.SourceApp != "claude-code" || in.ActorEmail != "alice@example.com" {
		t.Fatalf("actor/source not parsed: %+v", in)
	}
	for _, want := range []string{"summarize the report", "q2.pdf", "Q2 revenue grew 14%", "search", "revenue", "no rows", "(error)"} {
		if !strings.Contains(in.FlatText, want) {
			t.Fatalf("flat text missing %q:\n%s", want, in.FlatText)
		}
	}
}

func TestParse_UnknownEventShortCircuits(t *testing.T) {
	p := NewClaudeProvider()
	in, short, err := p.Parse([]byte(`{"type":"response","messages":[]}`))
	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if !short {
		t.Fatalf("want allowShortCircuit for unknown event type")
	}
	if in != nil {
		t.Fatalf("want nil input on short circuit, got %+v", in)
	}
}

func TestParse_UnknownBlockTypeNotRejected(t *testing.T) {
	p := NewClaudeProvider()
	body := []byte(`{"type":"prompt","messages":[{"role":"user","content":[
		{"type":"future_block","text":"still readable"},
		{"type":"text","text":"hello"}
	]}]}`)
	in, short, err := p.Parse(body)
	if err != nil || short {
		t.Fatalf("unexpected: err=%v short=%v", err, short)
	}
	if !strings.Contains(in.FlatText, "hello") || !strings.Contains(in.FlatText, "still readable") {
		t.Fatalf("unknown block not handled gracefully: %q", in.FlatText)
	}
}

func TestVerdict_AllowAndDeny(t *testing.T) {
	p := NewClaudeProvider()

	allow, ok := p.Verdict(Decision{Allow: true}).(claudeAllowVerdict)
	if !ok || allow.Action != "allow" {
		t.Fatalf("bad allow verdict: %#v", allow)
	}

	deny, ok := p.Verdict(Decision{Allow: false, Reason: "contains PII", ReferenceID: "scan_1"}).(claudeDenyVerdict)
	if !ok || deny.Action != "deny" || deny.DenyReason != "contains PII" || deny.ReferenceID != "scan_1" {
		t.Fatalf("bad deny verdict: %#v", deny)
	}
}

func TestVerdict_DenyReasonTruncated(t *testing.T) {
	p := NewClaudeProvider()
	long := strings.Repeat("x", denyReasonMaxLen+50)
	deny := p.Verdict(Decision{Allow: false, Reason: long}).(claudeDenyVerdict)
	if len([]rune(deny.DenyReason)) != denyReasonMaxLen {
		t.Fatalf("want deny_reason truncated to %d runes, got %d", denyReasonMaxLen, len([]rune(deny.DenyReason)))
	}
}
