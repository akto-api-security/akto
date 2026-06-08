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

// AccountIDKey is the gin context key under which the authenticated accountId is stored.
// Handlers downstream of the auth middleware can read it via c.GetInt(auth.AccountIDKey).
const AccountIDKey = "accountId"

// NewMiddleware builds a gin middleware that verifies an inbound RS256 JWT in the
// Authorization header against the supplied RSA public key (X.509/SPKI, base64 with or
// without PEM armor). On success it stores the token's accountId claim in the gin
// context; otherwise it aborts the request with 401.
//
// This mirrors the inbound authentication done by data-ingestion-service's AuthFilter
// (RSA_PUBLIC_KEY mode). The key is parsed once, up front, so an invalid key fails fast.
func NewMiddleware(rsaPublicKeyPEM string, logger *zap.Logger) (gin.HandlerFunc, error) {
	publicKey, err := parseRSAPublicKey(rsaPublicKeyPEM)
	if err != nil {
		return nil, fmt.Errorf("failed to parse RSA_PUBLIC_KEY: %w", err)
	}

	return func(c *gin.Context) {
		// Akto tokens are sent as the raw JWT in the Authorization header (no "Bearer " prefix),
		// matching how data-ingestion-service and the db-abstractor client exchange them.
		tokenString := c.GetHeader("Authorization")
		if tokenString == "" {
			logger.Warn("Authentication failed: missing Authorization header")
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
			if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok {
				return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
			}
			return publicKey, nil
		})
		if err != nil || !token.Valid {
			logger.Error("Authentication failed", zap.Error(err))
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		claims, ok := token.Claims.(jwt.MapClaims)
		if !ok {
			logger.Error("Authentication failed: unexpected claims type")
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		accountId, err := extractAccountID(claims)
		if err != nil {
			logger.Error("Authentication failed", zap.Error(err))
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		c.Set(AccountIDKey, accountId)
		c.Next()
	}, nil
}

// parseRSAPublicKey accepts an RSA public key in X.509/SPKI form, either PEM-armored or as
// bare base64, and returns the parsed key. It strips the PEM header/footer and all
// whitespace before decoding, matching data-ingestion-service's RSA_PUBLIC_KEY handling.
func parseRSAPublicKey(raw string) (*rsa.PublicKey, error) {
	cleaned := raw
	cleaned = strings.ReplaceAll(cleaned, "-----BEGIN PUBLIC KEY-----", "")
	cleaned = strings.ReplaceAll(cleaned, "-----END PUBLIC KEY-----", "")
	// Remove all surrounding/embedded whitespace (newlines, spaces, tabs, CR).
	cleaned = strings.Join(strings.Fields(cleaned), "")
	if cleaned == "" {
		return nil, fmt.Errorf("key is empty")
	}

	decoded, err := base64.StdEncoding.DecodeString(cleaned)
	if err != nil {
		return nil, fmt.Errorf("base64 decode failed: %w", err)
	}

	pub, err := x509.ParsePKIXPublicKey(decoded)
	if err != nil {
		return nil, fmt.Errorf("parse PKIX public key failed: %w", err)
	}

	rsaPub, ok := pub.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("key is not an RSA public key (got %T)", pub)
	}
	return rsaPub, nil
}

// extractAccountID reads the integer "accountId" claim, tolerating the numeric/string
// encodings JSON deserialization can yield.
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
