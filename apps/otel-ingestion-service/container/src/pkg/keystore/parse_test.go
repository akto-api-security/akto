package keystore

import (
	"testing"
)

func TestParseRSAPublicKeyPEM(t *testing.T) {
	_, err := ParseRSAPublicKeyPEM("")
	if err == nil {
		t.Fatal("expected error for empty key")
	}
}
