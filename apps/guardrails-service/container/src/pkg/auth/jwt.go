package auth

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"
)

var (
	exchangedTokenMu   sync.RWMutex
	exchangedAuthToken string
)

// GetDatabaseAbstractorServiceToken returns the exchanged (scoped) token if InitModuleType
// obtained one, otherwise falls back to the raw DATABASE_ABSTRACTOR_SERVICE_TOKEN env
// variable — a legacy/unscoped token keeps working exactly as before.
func GetDatabaseAbstractorServiceToken() string {
	exchangedTokenMu.RLock()
	token := exchangedAuthToken
	exchangedTokenMu.RUnlock()
	if token != "" {
		return token
	}
	return os.Getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN")
}

// InitModuleType trades the raw provisioned token for one scoped to moduleType, if the raw
// token supports it. rawToken/baseHost are the same DATABASE_ABSTRACTOR_SERVICE_TOKEN /
// DATABASE_ABSTRACTOR_SERVICE_URL values config.LoadConfig() already resolved (cfg.DatabaseAbstractorToken
// / cfg.DatabaseAbstractorURL) — passed in rather than re-read here, so there's one place that
// owns the env var + default. Any failure (legacy/unscoped token, network error, non-200
// response) is logged and swallowed — the raw token keeps being used as-is. Single attempt, no
// retries: a legacy token's rejection is deterministic, so retrying would only add startup
// latency for no benefit.
func InitModuleType(logger *zap.Logger, moduleType string, rawToken string, baseHost string) {
	if rawToken == "" {
		return
	}

	baseURL := strings.TrimSuffix(baseHost, "/") + "/api"

	body, err := json.Marshal(map[string]string{"moduleType": moduleType})
	if err != nil {
		logger.Warn("Failed to marshal exchangeToken request", zap.Error(err))
		return
	}

	req, err := http.NewRequest("POST", baseURL+"/exchangeToken", bytes.NewBuffer(body))
	if err != nil {
		logger.Warn("Failed to build exchangeToken request", zap.Error(err))
		return
	}
	req.Header.Set("Authorization", rawToken)
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		logger.Warn("Token exchange request failed; continuing with raw token", zap.Error(err))
		return
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		logger.Warn("Failed to read exchangeToken response; continuing with raw token", zap.Error(err))
		return
	}

	if resp.StatusCode != http.StatusOK {
		logger.Info("Token exchange not applied (likely a legacy/unscoped token); continuing with raw token",
			zap.Int("status", resp.StatusCode), zap.String("moduleType", moduleType))
		return
	}

	var parsed struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(respBody, &parsed); err != nil || parsed.Token == "" {
		logger.Warn("Failed to parse exchangeToken response; continuing with raw token", zap.Error(err))
		return
	}

	exchangedTokenMu.Lock()
	exchangedAuthToken = parsed.Token
	exchangedTokenMu.Unlock()
	logger.Info("Database-abstractor token exchanged for scoped token", zap.String("moduleType", moduleType))
}

var (
	tokenAccountOnce sync.Once
	tokenAccountID   string
)

// AccountIDFromServiceToken returns the accountId claim of the
// DATABASE_ABSTRACTOR_SERVICE_TOKEN JWT, or "" when the token is absent or
// unparseable. The signature is deliberately not verified: the token is this
// deployment's own credential — already trusted to fetch the tenant's policies
// — and the claim only labels cache/threat partitions with that same tenant;
// it never authorizes a caller.
func AccountIDFromServiceToken() string {
	tokenAccountOnce.Do(func() {
		parts := strings.Split(GetDatabaseAbstractorServiceToken(), ".")
		if len(parts) != 3 {
			return
		}
		payload, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(parts[1], "="))
		if err != nil {
			return
		}
		var claims map[string]interface{}
		if err := json.Unmarshal(payload, &claims); err != nil {
			return
		}
		switch v := claims["accountId"].(type) {
		case float64:
			tokenAccountID = strconv.Itoa(int(v))
		case string:
			tokenAccountID = v
		}
	})
	return tokenAccountID
}
