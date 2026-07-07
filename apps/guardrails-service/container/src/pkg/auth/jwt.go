package auth

import (
	"encoding/base64"
	"encoding/json"
	"os"
	"strconv"
	"strings"
	"sync"
)

// GetDatabaseAbstractorServiceToken returns the JWT token from environment variable
// This token is used for authenticating with database-abstractor service
func GetDatabaseAbstractorServiceToken() string {
	return os.Getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN")
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
