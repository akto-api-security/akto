package auth

import (
	"encoding/base64"
	"sync"
	"testing"
)

func fakeJWT(payload string) string {
	seg := func(s string) string { return base64.RawURLEncoding.EncodeToString([]byte(s)) }
	return seg(`{"alg":"RS256"}`) + "." + seg(payload) + "." + seg("sig")
}

// resetTokenAccount clears the process-lifetime memoization so each case
// re-reads the env var.
func resetTokenAccount() {
	tokenAccountOnce = sync.Once{}
	tokenAccountID = ""
}

func TestAccountIDFromServiceToken_NumericClaim(t *testing.T) {
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", fakeJWT(`{"accountId":1000000,"iat":1782988305}`))
	resetTokenAccount()
	if got := AccountIDFromServiceToken(); got != "1000000" {
		t.Fatalf("expected 1000000, got %q", got)
	}
}

func TestAccountIDFromServiceToken_MissingOrMalformed(t *testing.T) {
	for _, tok := range []string{"", "not-a-jwt", fakeJWT(`{"iat":1}`), "a.!!!.c"} {
		t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", tok)
		resetTokenAccount()
		if got := AccountIDFromServiceToken(); got != "" {
			t.Fatalf("token %q: expected empty account, got %q", tok, got)
		}
	}
}
