package auth

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"
)

const (
	bearerPrefix            = "bearer "
	revocationPollInterval  = 15 * time.Minute
	revocationRetryInterval = time.Minute
	revocationFetchTimeout  = 10 * time.Second
	fetchRevokedTokenIDsAPI = "/api/fetchRevokedTokenIds"
)

// SignatureTokenID is the sha256 of the token's signature; it must match AuthTokenIds.idOf in libs/dao.
func SignatureTokenID(rawToken string) string {
	token := strings.TrimSpace(rawToken)
	if len(token) >= len(bearerPrefix) && strings.EqualFold(token[:len(bearerPrefix)], bearerPrefix) {
		token = strings.TrimSpace(token[len(bearerPrefix):])
	}
	sum := sha256.Sum256([]byte(token[strings.LastIndex(token, ".")+1:]))
	return hex.EncodeToString(sum[:])
}

// TokenID is the claim tid (exchanged token's parent id), else the signature hash of the raw token.
func TokenID(claims jwt.MapClaims, rawToken string) string {
	if v, ok := claims["tid"].(string); ok && v != "" {
		return v
	}
	return SignatureTokenID(rawToken)
}

// RevokedSet is the in-memory set of revoked token ids, safe for concurrent use.
type RevokedSet struct {
	mu  sync.RWMutex
	ids map[string]struct{}
}

func NewRevokedSet() *RevokedSet {
	return &RevokedSet{ids: map[string]struct{}{}}
}

func (s *RevokedSet) Contains(id string) bool {
	if s == nil {
		return false
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, ok := s.ids[id]
	return ok
}

func (s *RevokedSet) Replace(ids []string) {
	next := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		next[id] = struct{}{}
	}
	s.mu.Lock()
	s.ids = next
	s.mu.Unlock()
}

// fetchRevokedTokenIDs asks cyborg for the revoked token ids of the token's account.
func fetchRevokedTokenIDs(client *http.Client, baseHost, token string) ([]string, error) {
	url := strings.TrimSuffix(baseHost, "/") + fetchRevokedTokenIDsAPI
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBufferString("{}"))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetchRevokedTokenIds returned status %d", resp.StatusCode)
	}

	var parsed struct {
		RevokedTokenIDs []string `json:"revokedTokenIds"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, err
	}
	return parsed.RevokedTokenIDs, nil
}

// StartRevocationPoller keeps a revoked-id set fresh in the background; it fails open and returns nil when unconfigured.
func StartRevocationPoller(ctx context.Context, logger *zap.Logger, baseHost string, tokenFn func() string) *RevokedSet {
	if baseHost == "" || tokenFn() == "" {
		logger.Info("Token revocation polling disabled (no DATABASE_ABSTRACTOR_SERVICE_URL or token)")
		return nil
	}
	return startPoller(ctx, logger, baseHost, tokenFn, revocationPollInterval, revocationRetryInterval, &http.Client{Timeout: revocationFetchTimeout})
}

func startPoller(ctx context.Context, logger *zap.Logger, baseHost string, tokenFn func() string, interval, retry time.Duration, client *http.Client) *RevokedSet {
	set := NewRevokedSet()
	poll := func() bool {
		ids, err := fetchRevokedTokenIDs(client, baseHost, tokenFn())
		if err != nil {
			logger.Warn("Revoked token poll failed; keeping last known set", zap.Error(err))
			return false
		}
		set.Replace(ids)
		return true
	}
	go func() {
		for {
			wait := interval
			// after a failed poll retry sooner, so a failure at startup does not leave the set empty for a full interval
			if !poll() && retry < interval {
				wait = retry
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(wait):
			}
		}
	}()
	return set
}
