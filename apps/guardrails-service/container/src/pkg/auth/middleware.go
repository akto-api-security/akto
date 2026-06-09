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

func NewMiddleware(rsaPublicKeyPEM string, logger *zap.Logger) (gin.HandlerFunc, error) {
	publicKey, err := parseRSAPublicKey(rsaPublicKeyPEM)
	if err != nil {
		return nil, fmt.Errorf("failed to parse RSA_PUBLIC_KEY: %w", err)
	}

	return func(c *gin.Context) {
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

func parseRSAPublicKey(raw string) (*rsa.PublicKey, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, fmt.Errorf("key is empty")
	}

	cleaned := raw
	cleaned = strings.ReplaceAll(cleaned, "-----BEGIN PUBLIC KEY-----", "")
	cleaned = strings.ReplaceAll(cleaned, "-----END PUBLIC KEY-----", "")
	cleaned = strings.ReplaceAll(cleaned, "\n", "")

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
