package auth

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"
)

const AccountIDKey = "accountId"

// AccountIDFromContext returns the accountId the auth middleware verified from
// the JWT, or (0, false) when authentication is disabled or no valid account was
// set. Callers use this to prefer the authenticated identity over any
// caller-supplied account field in the request body.
func AccountIDFromContext(c *gin.Context) (int, bool) {
	v, exists := c.Get(AccountIDKey)
	if !exists {
		return 0, false
	}
	id, ok := v.(int)
	if !ok || id <= 0 {
		return 0, false
	}
	return id, true
}

func NewMiddleware(rsaPublicKey string, logger *zap.Logger) (gin.HandlerFunc, error) {
	publicKey, err := parseRSAPublicKey(rsaPublicKey)
	if err != nil {
		return nil, fmt.Errorf("failed to parse RSA_PUBLIC_KEY: %w", err)
	}

	return func(c *gin.Context) {
		accountID, err := verifyToken(c.GetHeader("Authorization"), publicKey)
		if err != nil {
			logger.Warn("Authentication failed", zap.Error(err))
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		c.Set(AccountIDKey, accountID)
		c.Next()
	}, nil
}

func verifyToken(authHeader string, publicKey *rsa.PublicKey) (int, error) {
	if authHeader == "" {
		return 0, fmt.Errorf("missing Authorization header")
	}

	// Accept both a raw token and the "Bearer <token>" scheme (case-insensitive),
	// since some clients send the standard Authorization: Bearer <token> form.
	if fields := strings.Fields(authHeader); len(fields) == 2 && strings.EqualFold(fields[0], "Bearer") {
		authHeader = fields[1]
	}

	token, err := jwt.Parse(authHeader, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return publicKey, nil
	})
	if err != nil {
		return 0, err
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return 0, fmt.Errorf("unexpected claims type %T", token.Claims)
	}

	return extractAccountID(claims)
}

func parseRSAPublicKey(raw string) (*rsa.PublicKey, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, fmt.Errorf("key is empty")
	}

	body := raw
	body = strings.ReplaceAll(body, "-----BEGIN PUBLIC KEY-----", "")
	body = strings.ReplaceAll(body, "-----END PUBLIC KEY-----", "")
	body = strings.ReplaceAll(body, "\n", "")

	der, err := base64.StdEncoding.DecodeString(body)
	if err != nil {
		return nil, fmt.Errorf("base64 decode failed: %w", err)
	}

	key, err := x509.ParsePKIXPublicKey(der)
	if err != nil {
		return nil, fmt.Errorf("invalid PKIX public key: %w", err)
	}

	rsaKey, ok := key.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("not an RSA public key (got %T)", key)
	}
	return rsaKey, nil
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
