package models

// RedactionStatus is the outcome for one payload side. The values keep apart what a bare string
// cannot: fully redacted, redacted at reduced strength, nothing to mask, and could not be determined.
type RedactionStatus string

const (
	// RedactionRedacted: complete redaction, payload changed. Payload is set.
	RedactionRedacted RedactionStatus = "REDACTED"
	// RedactionRedactedBasic: the payload was masked by the regex and secret scrub only, because
	// the Anonymize scanner did not answer. Payload is set (it may equal the input) and is safer
	// than the raw text, but names and locations that only the scanner finds may remain.
	RedactionRedactedBasic RedactionStatus = "REDACTED_BASIC"
	// RedactionUnchanged: complete redaction found nothing to mask. Payload is omitted; the caller
	// keeps its own copy.
	RedactionUnchanged RedactionStatus = "UNCHANGED"
	// RedactionUnavailable: oversize payload, deadline passed before it was processed, or an error.
	// The caller must not treat its payload as redacted.
	RedactionUnavailable RedactionStatus = "UNAVAILABLE"
)

// RedactPayloadsRequest is the body of POST /api/redact/payloads.
type RedactPayloadsRequest struct {
	Items []RedactPayloadItem `json:"items"`
}

// RedactPayloadItem is one request/response pair of a single endpoint call. An empty or
// absent payload means "not sent": that side is not processed and is omitted from the result.
// Method, host and path identify the endpoint for caching; they never change the masking.
type RedactPayloadItem struct {
	// ID is chosen by the caller and echoed back so results can be matched to inputs.
	ID              string `json:"id"`
	Method          string `json:"method"`
	Host            string `json:"host"`
	Path            string `json:"path"`
	RequestPayload  string `json:"requestPayload,omitempty"`
	ResponsePayload string `json:"responsePayload,omitempty"`
}

// RedactPayloadsResponse is the body returned by POST /api/redact/payloads.
type RedactPayloadsResponse struct {
	Items []RedactPayloadResult `json:"items"`
}

// RedactPayloadResult answers one RedactPayloadItem. A side that was not sent is nil.
type RedactPayloadResult struct {
	ID       string               `json:"id"`
	Request  *RedactedPayloadSide `json:"request,omitempty"`
	Response *RedactedPayloadSide `json:"response,omitempty"`
}

// RedactedPayloadSide is the outcome for one side of an item.
type RedactedPayloadSide struct {
	Status RedactionStatus `json:"status"`
	// Payload is set only when Status is REDACTED or REDACTED_BASIC.
	Payload string `json:"payload,omitempty"`
}
