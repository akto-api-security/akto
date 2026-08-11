package promptguard

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// toleranceSeconds bounds how far a request's signing timestamp may drift from
// our clock, in either direction, before we reject it as replayed or skewed.
const toleranceSeconds = 300

// verifyStandardWebhook checks a request signed per the Standard Webhooks spec
// (https://www.standardwebhooks.com/), the scheme Anthropic uses for inference
// hooks. It returns:
//
//   - SigNotConfigured if no secrets are supplied (caller decides fail-open).
//   - SigUnsigned      if the webhook-* headers are absent.
//   - SigInvalid       if a signature is present but matches none of the secrets,
//     or the timestamp is out of tolerance.
//   - SigVerified      if any secret produces a matching signature.
//
// secrets may hold more than one value so a secret rotation's stragglers (signed
// with the previous secret) still verify during the overlap window. Each secret
// is the full "whsec_<base64>" value or the bare base64 after the prefix.
//
// The HMAC is computed over the raw body bytes exactly as received — never a
// re-encoded form — matching the spec's Go reference implementation.
func verifyStandardWebhook(secrets []string, header http.Header, body []byte) SigStatus {
	keyed := decodeSecrets(secrets)
	if len(keyed) == 0 {
		return SigNotConfigured
	}

	// net/http canonicalizes header names on lookup, so proxy re-casing of the
	// lowercase names Anthropic sends still matches.
	messageID := header.Get("webhook-id")
	timestamp := header.Get("webhook-timestamp")
	signatures := header.Get("webhook-signature")
	if messageID == "" || timestamp == "" || signatures == "" {
		return SigUnsigned
	}

	signedAt, err := strconv.ParseInt(timestamp, 10, 64)
	if err != nil {
		return SigInvalid
	}
	age := time.Now().Unix() - signedAt
	if age > toleranceSeconds || age < -toleranceSeconds {
		return SigInvalid // replayed, or the clocks disagree
	}

	prefix := []byte(messageID + "." + timestamp + ".")
	candidates := strings.Fields(signatures)
	for _, key := range keyed {
		mac := hmac.New(sha256.New, key)
		mac.Write(prefix)
		mac.Write(body)
		expected := "v1," + base64.StdEncoding.EncodeToString(mac.Sum(nil))
		for _, candidate := range candidates {
			if hmac.Equal([]byte(candidate), []byte(expected)) { // constant-time
				return SigVerified
			}
		}
	}
	return SigInvalid
}

// decodeSecrets turns the configured "whsec_..." secrets into raw HMAC keys,
// dropping blanks and any that fail to decode with the standard base64 alphabet
// (a URL-safe decoder would derive the wrong bytes whenever the secret contains
// '+' or '/').
func decodeSecrets(secrets []string) [][]byte {
	keys := make([][]byte, 0, len(secrets))
	for _, s := range secrets {
		s = strings.TrimSpace(s)
		if s == "" {
			continue
		}
		key, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(s, "whsec_"))
		if err != nil {
			continue
		}
		keys = append(keys, key)
	}
	return keys
}
