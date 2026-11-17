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

func TestMiddleware_BearerSchemeSetsAccountID(t *testing.T) {
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})

	for _, prefix := range []string{"Bearer ", "bearer ", "BEARER "} {
		status, accountID, set := runRequest(t, pub, prefix+token)
		if status != http.StatusOK {
			t.Fatalf("%q: status = %d, want 200", prefix, status)
		}
		if !set || accountID != 1718012345 {
			t.Fatalf("%q: accountId = %d (set=%v), want 1718012345", prefix, accountID, set)
		}
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

func pinDeploymentAccount(t *testing.T, id string) {
	t.Helper()
	orig := deploymentAccountID
	deploymentAccountID = func() string { return id }
	t.Cleanup(func() { deploymentAccountID = orig })
}

func TestMiddleware_TokenForThisDeploymentPasses(t *testing.T) {
	pinDeploymentAccount(t, "1718012345")
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})

	status, accountID, set := runRequest(t, pub, token)
	if status != http.StatusOK || !set || accountID != 1718012345 {
		t.Fatalf("status=%d set=%v accountId=%d, want 200 true 1718012345", status, set, accountID)
	}
}

func TestMiddleware_TokenForAnotherAccountRejected(t *testing.T) {
	pinDeploymentAccount(t, "1718000000")
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345}) // validly signed, wrong account

	status, _, set := runRequest(t, pub, token)
	if status != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", status)
	}
	if set {
		t.Fatal("accountId must not be set for a rejected token")
	}
}

func TestMiddleware_UnknownDeploymentAccountSkipsCheck(t *testing.T) {
	pinDeploymentAccount(t, "")
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})

	if status, _, _ := runRequest(t, pub, token); status != http.StatusOK {
		t.Fatalf("status = %d, want 200 when the deployment account is unknown", status)
	}
}

func pinReroutes(t *testing.T, m map[string]int) {
	t.Helper()
	orig := tokenAccountReroutes
	tokenAccountReroutes = m
	t.Cleanup(func() { tokenAccountReroutes = orig })
}

func TestMiddleware_ReroutedTokenUsesMappedAccount(t *testing.T) {
	pinDeploymentAccount(t, "1758787662")
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345}) // another account's token
	pinReroutes(t, map[string]int{token[len(token)-31:]: 1758787662})

	for _, header := range []string{token, "Bearer " + token} {
		status, accountID, set := runRequest(t, pub, header)
		if status != http.StatusOK || !set || accountID != 1758787662 {
			t.Fatalf("%q: status=%d set=%v accountId=%d, want 200 true 1758787662", header[:7], status, set, accountID)
		}
	}
}

func TestMiddleware_ReroutedSuffixStillNeedsValidSignature(t *testing.T) {
	pinDeploymentAccount(t, "1758787662")
	priv, _ := mustKeyPair(t)
	_, otherPub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})
	pinReroutes(t, map[string]int{token[len(token)-31:]: 1758787662})

	if status, _, _ := runRequest(t, otherPub, token); status != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401 for a badly signed token", status)
	}
}

func runReroute(t *testing.T, mw gin.HandlerFunc, header string) (int, bool) {
	t.Helper()
	gin.SetMode(gin.TestMode)
	var acct int
	var set bool
	r := gin.New()
	r.GET("/api/x", mw, func(c *gin.Context) {
		acct, set = AccountIDFromContext(c)
		c.Status(http.StatusOK)
	})
	req := httptest.NewRequest(http.MethodGet, "/api/x", nil)
	if header != "" {
		req.Header.Set("Authorization", header)
	}
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("%q: status = %d, want 200 (the reroute middleware never rejects)", header, w.Code)
	}
	return acct, set
}

func TestRerouteMiddleware_VerifiedTokenGetsMappedAccount(t *testing.T) {
	priv, pub := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})
	pinReroutes(t, map[string]int{token[len(token)-31:]: 1758787662})
	mw := NewRerouteMiddleware(pub, zap.NewNop())

	for _, h := range []string{token, "Bearer " + token} {
		if acct, set := runReroute(t, mw, h); !set || acct != 1758787662 {
			t.Fatalf("%q: accountId=%d set=%v, want 1758787662 true", h[:7], acct, set)
		}
	}
}

func TestRerouteMiddleware_ForgedOrForeignTokenNotRerouted(t *testing.T) {
	priv, _ := mustKeyPair(t)
	_, pub := mustKeyPair(t) // verifies against a different key than the one that signed
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})
	suffix := token[len(token)-31:]
	pinReroutes(t, map[string]int{suffix: 1758787662})
	mw := NewRerouteMiddleware(pub, zap.NewNop())

	// the suffix is public: a bare header ending in it, or a token signed by another key, must not reroute
	for _, h := range []string{"forged-" + suffix, token} {
		if _, set := runReroute(t, mw, h); set {
			t.Fatalf("%q: accountId must not be set for an unverified token", h[:7])
		}
	}
}

func TestRerouteMiddleware_OtherRequestsPassThrough(t *testing.T) {
	priv, pub := mustKeyPair(t)
	pinReroutes(t, map[string]int{"sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw": 1758787662})
	mw := NewRerouteMiddleware(pub, zap.NewNop())
	valid := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})

	// with auth off, nothing but a verified rerouted token may set the account; nothing is rejected
	for _, h := range []string{"", "garbage", valid} {
		if _, set := runReroute(t, mw, h); set {
			t.Fatalf("%q: accountId must not be set", h)
		}
	}
}

func TestRerouteMiddleware_NoKeyReroutesNothing(t *testing.T) {
	priv, _ := mustKeyPair(t)
	token := signToken(t, priv, jwt.MapClaims{"accountId": 1718012345})
	pinReroutes(t, map[string]int{token[len(token)-31:]: 1758787662})

	for _, key := range []string{"", "not-a-key"} {
		if _, set := runReroute(t, NewRerouteMiddleware(key, zap.NewNop()), token); set {
			t.Fatalf("key %q: accountId must not be set without a key to verify with", key)
		}
	}
}
