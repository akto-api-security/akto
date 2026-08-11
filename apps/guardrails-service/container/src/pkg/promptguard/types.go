// Package promptguard turns guardrails-service into a pre-inference prompt
// guardrail: an HTTPS endpoint an AI provider calls with a conversation
// transcript before it runs inference, expecting an allow/deny verdict back.
// (Anthropic calls this an "inference hook" / "AI security server"; other
// providers use their own names.)
//
// The package is provider-generic. The wire format (Anthropic's Claude prompt
// frame today, OpenAI/others tomorrow) lives behind the Provider interface,
// while the shared core — flatten a transcript into scannable text, run it
// through the guardrail validator, map the result to a verdict — is identical
// across providers. Adding a provider is a single new file implementing Provider.
package promptguard

// GuardInput is the provider-neutral view of one prompt request. Every
// Provider.Parse flattens its own wire format into this shape so the handler and
// validator never depend on a specific provider's schema.
type GuardInput struct {
	// FlatText is the whole transcript flattened into a single scannable string
	// (all message/content blocks joined). This is what the guardrail validator
	// inspects.
	FlatText string

	// RequestID is the provider's opaque per-request id, used for correlation and
	// as an idempotency key (Anthropic sends it as both the body request_id and
	// the webhook-id header).
	RequestID string
	// SessionID is the provider's opaque conversation id, when present.
	SessionID string
	// TenantID is the provider's opaque organization id, when present.
	TenantID string
	// Model is the public model identifier for the request, when present.
	Model string
	// SourceApp is the originating application (e.g. "claude-ai", "claude-code").
	SourceApp string
	// ActorID / ActorEmail identify the principal the request is attributed to,
	// when available.
	ActorID    string
	ActorEmail string
}

// Decision is the provider-neutral verdict the core produces. Provider.Verdict
// renders it into the provider's response shape.
type Decision struct {
	// Allow lets inference proceed; false denies it.
	Allow bool
	// Reason is the user-facing explanation shown on a deny (ignored on allow).
	Reason string
	// ReferenceID is an opaque id for this evaluation, recorded by the provider
	// for later correlation and never shown to the end user.
	ReferenceID string
}

// SigStatus is the outcome of verifying a request's authenticity.
type SigStatus int

const (
	// SigVerified means the request carried a valid signature.
	SigVerified SigStatus = iota
	// SigUnsigned means no signature was present. During initial setup (before a
	// signing secret exists) this is expected and accepted; once a secret is
	// configured the handler treats it as a failure.
	SigUnsigned
	// SigInvalid means a signature was present but did not verify.
	SigInvalid
	// SigNotConfigured means the provider has no signing secret configured, so
	// signatures cannot be checked (local/dev, or a pre-first-save connection test).
	SigNotConfigured
)
