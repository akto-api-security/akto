package tenant

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// DefaultDIURLTemplate is the standard Akto hybrid SaaS guardrails/data-ingestion host.
const DefaultDIURLTemplate = "https://{accountId}-guardrails.akto.io"

type Router struct {
	defaultURL string
	template   string
	overrides  map[int]string
}

func NewRouter(defaultURL, template string, overrides map[int]string) *Router {
	return &Router{
		defaultURL: strings.TrimRight(strings.TrimSpace(defaultURL), "/"),
		template:   strings.TrimSpace(template),
		overrides:  overrides,
	}
}

func ParseURLMap(raw string) (map[int]string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, nil
	}
	var parsed map[string]string
	if err := json.Unmarshal([]byte(raw), &parsed); err != nil {
		return nil, fmt.Errorf("parse TENANT_DI_URL_MAP: %w", err)
	}
	out := make(map[int]string, len(parsed))
	for k, v := range parsed {
		var accountID int
		if _, err := fmt.Sscanf(k, "%d", &accountID); err != nil {
			return nil, fmt.Errorf("invalid account id %q in TENANT_DI_URL_MAP", k)
		}
		url := strings.TrimRight(strings.TrimSpace(v), "/")
		if url == "" {
			return nil, fmt.Errorf("empty URL for account %d in TENANT_DI_URL_MAP", accountID)
		}
		out[accountID] = url
	}
	return out, nil
}

func (r *Router) DataIngestionURL(accountID int) (string, error) {
	if accountID <= 0 {
		return "", fmt.Errorf("invalid account id %d", accountID)
	}
	if url, ok := r.overrides[accountID]; ok {
		return url, nil
	}
	if r.defaultURL != "" {
		return r.defaultURL, nil
	}
	if r.template != "" {
		return expandTemplate(r.template, accountID), nil
	}
	return "", fmt.Errorf("no data-ingestion URL for account %d", accountID)
}

func expandTemplate(template string, accountID int) string {
	url := strings.ReplaceAll(template, "{accountId}", strconv.Itoa(accountID))
	return strings.TrimRight(strings.TrimSpace(url), "/")
}
