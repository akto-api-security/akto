// Package akto is a small client for Akto's database abstractor (cyborg),
// used to send events and heartbeats.
//
// It authenticates the way the mini-testing module does: a token carrying a
// scope must first be exchanged at /api/exchangeToken for one bound to a module
// type, or cyborg refuses it with 403. The orchestrator ships with the
// mini-testing module's token, so it exchanges for MINI_TESTING. An unscoped
// (legacy) token is rejected by the exchange and then used as-is.
package akto

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"
)

const (
	requestTimeout = 5 * time.Second
	// After a network failure, the exchange is retried no sooner than this.
	exchangeRetryEvery = time.Minute
)

type Client struct {
	base         string
	rawToken     string
	exchangeType string
	http         *http.Client
	log          *slog.Logger

	mu         sync.Mutex
	token      string
	settled    bool // the exchange succeeded or was refused; no more attempts
	lastTry    time.Time
	lastStatus string
}

// New returns nil when url or token is empty, so callers can treat a nil
// client as "not configured".
func New(url, token, exchangeModuleType string, log *slog.Logger) *Client {
	if url == "" || token == "" {
		return nil
	}
	return &Client{
		base:         strings.TrimSuffix(url, "/"),
		rawToken:     token,
		exchangeType: exchangeModuleType,
		http:         &http.Client{Timeout: requestTimeout},
		log:          log,
	}
}

// Post sends body as JSON to path (e.g. "/api/insertTestingLog").
func (c *Client) Post(ctx context.Context, path string, body any) error {
	_, err := c.post(ctx, path, c.authToken(ctx), body)
	return err
}

// EnsureAuth runs the token exchange now if it is still due.
func (c *Client) EnsureAuth(ctx context.Context) { c.authToken(ctx) }

// TokenStatus says how the client authenticates, for heartbeats and logs.
func (c *Client) TokenStatus() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.lastStatus
}

func (c *Client) post(ctx context.Context, path, token string, body any) ([]byte, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(ctx, requestTimeout)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.base+path, bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode != http.StatusOK {
		return respBody, &StatusError{Path: path, Code: resp.StatusCode}
	}
	return respBody, nil
}

// StatusError is a non-200 reply from cyborg.
type StatusError struct {
	Path string
	Code int
}

func (e *StatusError) Error() string { return fmt.Sprintf("%s: HTTP %d", e.Path, e.Code) }

// authToken exchanges the raw token once, lazily, and returns the token to use.
func (c *Client) authToken(ctx context.Context) string {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.settled || c.exchangeType == "" || time.Since(c.lastTry) < exchangeRetryEvery {
		if c.token != "" {
			return c.token
		}
		return c.rawToken
	}
	c.lastTry = time.Now()

	resp, err := c.post(ctx, "/api/exchangeToken", c.rawToken, map[string]string{"moduleType": c.exchangeType})
	var statusErr *StatusError
	switch {
	case err == nil:
		var out struct {
			Token string `json:"token"`
		}
		if json.Unmarshal(resp, &out) == nil && out.Token != "" {
			c.token, c.settled = out.Token, true
			c.lastStatus = "exchanged for " + c.exchangeType
			c.log.Info("Akto token exchanged", "module_type", c.exchangeType)
			return c.token
		}
		c.settled = true
		c.lastStatus = "raw (exchange returned no token)"
	case errors.As(err, &statusErr):
		// An unscoped token can't be exchanged; it works as-is, as in mini-testing.
		c.settled = true
		c.lastStatus = fmt.Sprintf("raw (exchange not applied, HTTP %d)", statusErr.Code)
		c.log.Info("Akto token exchange not applied; using the token as-is", "status", statusErr.Code)
	default:
		c.lastStatus = "raw (exchange failed, will retry)"
		c.log.Warn("Akto token exchange failed; retrying later", "err", err)
	}
	return c.rawToken
}
