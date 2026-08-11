package promptguard

import "net/http"

// Provider adapts one AI provider's prompt-guard wire format to the neutral
// core. Each provider owns three concerns: authenticating the request, parsing
// its body into a GuardInput, and rendering a Decision back into the provider's
// expected response shape. Everything between parse and verdict — the guardrail
// validation — is provider-independent and lives in the handler.
//
// Implementations must be safe for concurrent use; one instance serves every
// request for its provider.
type Provider interface {
	// Name is the provider's route key (e.g. "claude"), matching the :provider
	// path segment.
	Name() string

	// Verify checks that the request genuinely came from the provider, over the
	// raw body bytes. See SigStatus for the outcomes.
	Verify(header http.Header, body []byte) SigStatus

	// Parse converts a raw request body into a GuardInput.
	//
	// allowShortCircuit signals a request that needs a verdict but that the
	// provider's protocol says to allow without inspection — e.g. an unrecognized
	// event type under forward-compatibility rules. When true, the handler
	// returns an allow verdict without validating and input may be nil.
	//
	// A non-nil err means the body could not be understood; the handler fails
	// open (allow) rather than returning an error status, which the provider
	// would treat as a delivery failure.
	Parse(body []byte) (input *GuardInput, allowShortCircuit bool, err error)

	// Verdict renders a Decision into the provider's response object, ready to be
	// serialized as the HTTP 200 JSON body.
	Verdict(d Decision) any
}

// Registry maps a provider route key to its Provider. It is built once at
// startup and only read afterwards, so it needs no locking.
type Registry struct {
	providers map[string]Provider
}

// NewRegistry builds a Registry from the given providers, keyed by Name().
func NewRegistry(providers ...Provider) *Registry {
	m := make(map[string]Provider, len(providers))
	for _, p := range providers {
		if p != nil {
			m[p.Name()] = p
		}
	}
	return &Registry{providers: m}
}

// Get returns the provider for name and whether one is registered.
func (r *Registry) Get(name string) (Provider, bool) {
	p, ok := r.providers[name]
	return p, ok
}
