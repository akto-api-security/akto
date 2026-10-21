// Package probe talks to the control endpoints a mini-testing pod serves on
// its metrics port (OrchestratorControl in apps/mini-testing).
package probe

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"strconv"
	"time"
)

const TokenHeader = "X-Akto-Orchestrator-Token"

// ErrUnauthorized means the pod rejected the control token.
var ErrUnauthorized = errors.New("pod rejected the orchestrator token (HTTP 401)")

// Status is what a pod reports. Busy means the pod holds a test run; from
// /drain it also covers a claim still in flight, so a drained pod reporting
// Busy=false will not pick up work. A drain lapses on the pod unless renewed.
type Status struct {
	Busy     bool `json:"busy"`
	Draining bool `json:"draining"`
}

// Prober is the pod-facing side of the controller, an interface so tests can fake it.
type Prober interface {
	Status(ctx context.Context, podIP string) (Status, error)
	// Drain stops the pod from claiming new runs. The returned status is read
	// after draining is set: Busy=false means the pod will not pick up work.
	Drain(ctx context.Context, podIP string) (Status, error)
	Undrain(ctx context.Context, podIP string) (Status, error)
}

type HTTPProber struct {
	port   int
	token  string
	client *http.Client
}

func NewHTTPProber(port int, token string, timeout time.Duration) *HTTPProber {
	return &HTTPProber{port: port, token: token, client: &http.Client{Timeout: timeout}}
}

func (p *HTTPProber) Status(ctx context.Context, podIP string) (Status, error) {
	return p.call(ctx, http.MethodGet, podIP, "/status")
}

func (p *HTTPProber) Drain(ctx context.Context, podIP string) (Status, error) {
	return p.call(ctx, http.MethodPost, podIP, "/drain")
}

func (p *HTTPProber) Undrain(ctx context.Context, podIP string) (Status, error) {
	return p.call(ctx, http.MethodPost, podIP, "/undrain")
}

func (p *HTTPProber) call(ctx context.Context, method, podIP, path string) (Status, error) {
	url := "http://" + net.JoinHostPort(podIP, strconv.Itoa(p.port)) + path
	req, err := http.NewRequestWithContext(ctx, method, url, nil)
	if err != nil {
		return Status{}, err
	}
	if p.token != "" {
		req.Header.Set(TokenHeader, p.token)
	}
	resp, err := p.client.Do(req)
	if err != nil {
		return Status{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusUnauthorized {
		return Status{}, fmt.Errorf("%s %s: %w", method, url, ErrUnauthorized)
	}
	if resp.StatusCode != http.StatusOK {
		return Status{}, fmt.Errorf("%s %s: HTTP %d", method, url, resp.StatusCode)
	}
	var s Status
	if err := json.NewDecoder(resp.Body).Decode(&s); err != nil {
		return Status{}, fmt.Errorf("%s %s: decode: %w", method, url, err)
	}
	return s, nil
}
