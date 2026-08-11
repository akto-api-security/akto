package promptguard

import (
	"encoding/json"
	"net/http"
	"strings"
)

// claudeProviderName is the route key for Anthropic's Claude prompt guard
// (Anthropic's "inference hooks").
const claudeProviderName = "claude"

// denyReasonMaxLen is the spec's cap on deny_reason; longer values are truncated
// by Anthropic, but we truncate ourselves so what we log matches what the user sees.
const denyReasonMaxLen = 500

// ClaudeProvider implements Provider for Anthropic's Claude inference hooks.
// Requests are the "prompt frame" documented at
// platform.claude.com/docs/en/manage-claude/inference-hooks-endpoint and are
// signed per Standard Webhooks.
type ClaudeProvider struct {
	// secrets are the accepted signing secrets ("whsec_..."). More than one lets
	// a rotation's overlap window verify stragglers signed with the old secret.
	// Empty means signatures cannot be checked (local/dev or pre-first-save test).
	secrets []string
}

// NewClaudeProvider builds a ClaudeProvider. Blank secrets are ignored; pass the
// current secret and, during a rotation, the previous one as well.
func NewClaudeProvider(secrets ...string) *ClaudeProvider {
	kept := make([]string, 0, len(secrets))
	for _, s := range secrets {
		if strings.TrimSpace(s) != "" {
			kept = append(kept, s)
		}
	}
	return &ClaudeProvider{secrets: kept}
}

func (p *ClaudeProvider) Name() string { return claudeProviderName }

func (p *ClaudeProvider) Verify(header http.Header, body []byte) SigStatus {
	return verifyStandardWebhook(p.secrets, header, body)
}

// claudePromptFrame is the subset of the prompt-frame body we read. Unknown
// top-level fields are ignored by encoding/json, satisfying the spec's
// forward-compatibility requirement.
type claudePromptFrame struct {
	Type      string          `json:"type"`
	RequestID string          `json:"request_id"`
	TenantID  string          `json:"tenant_id"`
	SessionID string          `json:"session_id"`
	Model     string          `json:"model"`
	Actor     claudeActor     `json:"actor"`
	Source    claudeSource    `json:"source"`
	Messages  []claudeMessage `json:"messages"`
}

type claudeActor struct {
	ID           string `json:"id"`
	EmailAddress string `json:"email_address"`
}

type claudeSource struct {
	Application string `json:"application"`
}

type claudeMessage struct {
	Role    string        `json:"role"`
	Content []claudeBlock `json:"content"`
}

// claudeBlock captures the content-block fields we scan across the block types
// (text / tool_use / tool_result / attachment). Absent fields stay zero, and an
// unrecognized "type" simply leaves the known fields empty — we never reject it.
// Input is kept as raw JSON so tool arguments are scanned as-is.
type claudeBlock struct {
	Type     string          `json:"type"`
	Text     string          `json:"text"`
	ToolName string          `json:"tool_name"`
	Input    json.RawMessage `json:"input"`
	Content  string          `json:"content"`
	IsError  bool            `json:"is_error"`
	FileName string          `json:"file_name"`
}

func (p *ClaudeProvider) Parse(body []byte) (*GuardInput, bool, error) {
	var frame claudePromptFrame
	if err := json.Unmarshal(body, &frame); err != nil {
		return nil, false, err
	}

	// Only the "prompt" event is defined today. Any other (future) event type
	// still needs a verdict, and the spec says to allow it rather than error.
	if frame.Type != "" && frame.Type != "prompt" {
		return nil, true, nil
	}

	input := &GuardInput{
		FlatText:   flattenMessages(frame.Messages),
		RequestID:  frame.RequestID,
		SessionID:  frame.SessionID,
		TenantID:   frame.TenantID,
		Model:      frame.Model,
		SourceApp:  frame.Source.Application,
		ActorID:    frame.Actor.ID,
		ActorEmail: frame.Actor.EmailAddress,
	}
	return input, false, nil
}

// flattenMessages joins every content block across every message into one
// scannable string. Each block contributes the text a policy should inspect;
// unknown block types contribute whatever text field they happen to carry and
// are never skipped for being unrecognized.
func flattenMessages(messages []claudeMessage) string {
	var b strings.Builder
	for _, msg := range messages {
		for _, block := range msg.Content {
			writeBlock(&b, msg.Role, block)
		}
	}
	return strings.TrimSpace(b.String())
}

func writeBlock(b *strings.Builder, role string, block claudeBlock) {
	appendLine := func(s string) {
		if s == "" {
			return
		}
		if b.Len() > 0 {
			b.WriteByte('\n')
		}
		b.WriteString(s)
	}

	switch block.Type {
	case "tool_use":
		line := role + " tool_use " + block.ToolName
		if len(block.Input) > 0 {
			line += " " + string(block.Input)
		}
		appendLine(line)
	case "tool_result":
		prefix := role + " tool_result " + block.ToolName
		if block.IsError {
			prefix += " (error)"
		}
		appendLine(strings.TrimSpace(prefix + " " + block.Content))
	case "attachment":
		appendLine(strings.TrimSpace(role + " attachment " + block.FileName + " " + block.Text))
	default:
		// "text" and any unrecognized block type: emit whatever text is present.
		appendLine(block.Text)
	}
}

func (p *ClaudeProvider) Verdict(d Decision) any {
	if d.Allow {
		return claudeAllowVerdict{Action: "allow"}
	}
	v := claudeDenyVerdict{Action: "deny", DenyReason: truncate(d.Reason, denyReasonMaxLen)}
	if d.ReferenceID != "" {
		v.ReferenceID = d.ReferenceID
	}
	return v
}

type claudeAllowVerdict struct {
	Action string `json:"action"`
}

type claudeDenyVerdict struct {
	Action      string `json:"action"`
	DenyReason  string `json:"deny_reason,omitempty"`
	ReferenceID string `json:"reference_id,omitempty"`
}

// truncate caps s at max runes, so a multi-byte reason is never cut mid-rune.
func truncate(s string, max int) string {
	if max <= 0 {
		return ""
	}
	r := []rune(s)
	if len(r) <= max {
		return s
	}
	return string(r[:max])
}
