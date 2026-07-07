package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"testing"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/keystore"
	"github.com/golang-jwt/jwt/v5"
)

func TestNormalizeAuthHeader(t *testing.T) {
	if got := normalizeAuthHeader("Bearer token123"); got != "token123" {
		t.Fatalf("expected token123, got %q", got)
	}
	if got := normalizeAuthHeader("raw-token"); got != "raw-token" {
		t.Fatalf("expected raw-token, got %q", got)
	}
}

func TestVerifierAuthenticate(t *testing.T) {
	priv, pubPEM := generateTestKeyPair(t)
	token := signTestToken(t, priv, 42, time.Now().Add(time.Hour))

	keys, err := keystore.NewStaticProvider(pubPEM)
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewVerifier(true, keys, nil)

	accountID, err := verifier.Authenticate("Bearer " + token)
	if err != nil {
		t.Fatal(err)
	}
	if accountID != 42 {
		t.Fatalf("expected account 42, got %d", accountID)
	}

	accountID, err = verifier.Authenticate(token)
	if err != nil {
		t.Fatal(err)
	}
	if accountID != 42 {
		t.Fatalf("expected cached account 42, got %d", accountID)
	}
}

func TestVerifierRevokedToken(t *testing.T) {
	priv, pubPEM := generateTestKeyPair(t)
	token := signTestToken(t, priv, 7, time.Now().Add(time.Hour))

	keys, err := keystore.NewStaticProvider(pubPEM)
	if err != nil {
		t.Fatal(err)
	}
	verifier := NewVerifier(true, keys, map[string]struct{}{token: {}})
	if _, err := verifier.Authenticate(token); err == nil {
		t.Fatal("expected revoked token error")
	}
}

func generateTestKeyPair(t *testing.T) (*rsa.PrivateKey, string) {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	der, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	body := base64.StdEncoding.EncodeToString(der)
	return priv, body
}

func signTestToken(t *testing.T, priv *rsa.PrivateKey, accountID int, exp time.Time) string {
	t.Helper()
	claims := jwt.MapClaims{
		"accountId": accountID,
		"exp":       exp.Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, err := token.SignedString(priv)
	if err != nil {
		t.Fatal(err)
	}
	return signed
}
