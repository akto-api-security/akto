package auth

import (
	"context"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/keystore"
	"github.com/golang-jwt/jwt/v5"
)

type Verifier struct {
	keys        keystore.Provider
	revoked     map[string]struct{}
	cache       *tokenCache
	authEnabled bool
}

func NewVerifier(authEnabled bool, keys keystore.Provider, revoked map[string]struct{}) *Verifier {
	return &Verifier{
		keys:        keys,
		revoked:     revoked,
		cache:       newTokenCache(5 * time.Minute),
		authEnabled: authEnabled,
	}
}

func (v *Verifier) Authenticate(authHeader string) (int, error) {
	if !v.authEnabled {
		return 0, nil
	}

	token := normalizeAuthHeader(authHeader)
	if token == "" {
		return 0, fmt.Errorf("missing Authorization header")
	}
	if _, revoked := v.revoked[token]; revoked {
		return 0, fmt.Errorf("token revoked")
	}
	if accountID, ok := v.cache.get(token); ok {
		return accountID, nil
	}

	publicKey, err := v.keys.PublicKey(context.Background())
	if err != nil {
		return 0, err
	}

	accountID, exp, err := verifyToken(token, publicKey)
	if err != nil {
		return 0, err
	}
	v.cache.set(token, accountID, exp)
	return accountID, nil
}

func normalizeAuthHeader(header string) string {
	header = strings.TrimSpace(header)
	if len(header) >= 7 && strings.EqualFold(header[:7], "bearer ") {
		return strings.TrimSpace(header[7:])
	}
	return header
}

func verifyToken(tokenString string, publicKey *rsa.PublicKey) (int, time.Time, error) {
	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return publicKey, nil
	})
	if err != nil {
		return 0, time.Time{}, err
	}
	if !token.Valid {
		return 0, time.Time{}, fmt.Errorf("invalid token")
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return 0, time.Time{}, fmt.Errorf("unexpected claims type %T", token.Claims)
	}

	accountID, err := extractAccountID(claims)
	if err != nil {
		return 0, time.Time{}, err
	}

	var exp time.Time
	if raw, ok := claims["exp"]; ok {
		switch n := raw.(type) {
		case float64:
			exp = time.Unix(int64(n), 0)
		case json.Number:
			if i, err := n.Int64(); err == nil {
				exp = time.Unix(i, 0)
			}
		}
	}
	return accountID, exp, nil
}

func extractAccountID(claims jwt.MapClaims) (int, error) {
	v, ok := claims["accountId"]
	if !ok {
		return 0, fmt.Errorf("accountId claim missing")
	}

	switch n := v.(type) {
	case float64:
		return int(n), nil
	case int:
		return n, nil
	case json.Number:
		i, err := n.Int64()
		if err != nil {
			return 0, fmt.Errorf("accountId claim is not an integer: %w", err)
		}
		return int(i), nil
	case string:
		i, err := strconv.Atoi(n)
		if err != nil {
			return 0, fmt.Errorf("accountId claim is not an integer: %w", err)
		}
		return i, nil
	default:
		return 0, fmt.Errorf("accountId claim has unexpected type %T", v)
	}
}
