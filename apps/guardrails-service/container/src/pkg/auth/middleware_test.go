package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"
)

func mustKeyPair(t *testing.T) (*rsa.PrivateKey, string) {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	der, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		t.Fatalf("marshal public key: %v", err)
	}
	return priv, base64.StdEncoding.EncodeToString(der)
}

func signToken(t *testing.T, priv *rsa.PrivateKey, claims jwt.MapClaims) string {
	t.Helper()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, err := tok.SignedString(priv)
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}
	return signed
}

func runRequest(t *testing.T, pubKey, authHeader string) (status int, accountID int, accountSet bool) {
	t.Helper()
	gin.SetMode(gin.TestMode)

	mw, err := NewMiddleware(pubKey, zap.NewNop())
	if err != nil {
		t.Fatalf("NewMiddleware: %v", err)
	}

	r := gin.New()
	r.GET("/api/x", mw, func(c *gin.Context) {
		if v, ok := c.Get(AccountIDKey); ok {
			accountSet = true
			accountID = v.(int)
		}
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/x", nil)
	if authHeader != "" {
		req.Header.Set("Authorization", authHeader)
	}
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	return w.Code, accountID, accountSet
}

func TestMiddleware_ValidTokenSetsAccountID(t *testing.T) {
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})

	status, accountID, set := runRequest(t, pub, token)
	if status != http.StatusOK {
		t.Fatalf("status = %d, want 200", status)
	}
	if !set {
		t.Fatal("accountId was not set in context")
	}
	if accountID != 1718012345 {
		t.Fatalf("accountId = %d, want 1718012345", accountID)
	}
}

func TestMiddleware_MissingHeader(t *testing.T) {
	_, pub := mustKeyPair(t)
	if status, _, _ := runRequest(t, pub, ""); status != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", status)
	}
}

func TestMiddleware_WrongSigningKey(t *testing.T) {
	priv, _ := mustKeyPair(t)
	_, otherPub := mustKeyPair(t) // verify with a different key than the one that signed
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1})

	if status, _, _ := runRequest(t, otherPub, token); status != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", status)
	}
}

func TestMiddleware_MissingAccountIDClaim(t *testing.T) {
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"sub": "no-account"})

	if status, _, _ := runRequest(t, pub, token); status != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", status)
	}
}

func TestNewMiddleware_InvalidKey(t *testing.T) {
	if _, err := NewMiddleware("not-a-valid-key", zap.NewNop()); err == nil {
		t.Fatal("expected error for invalid RSA_PUBLIC_KEY, got nil")
	}
}

func TestParseRSAPublicKey_PEMArmored(t *testing.T) {
	_, pub := mustKeyPair(t)
	armored := "-----BEGIN PUBLIC KEY-----\n" + pub + "\n-----END PUBLIC KEY-----"
	if _, err := parseRSAPublicKey(armored); err != nil {
		t.Fatalf("parseRSAPublicKey(armored) failed: %v", err)
	}
}
