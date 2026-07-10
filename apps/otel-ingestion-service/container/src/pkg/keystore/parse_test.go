package keystore

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"testing"
)

func TestParseRSAPublicKeyPEM(t *testing.T) {
	_, err := ParseRSAPublicKeyPEM("")
	if err == nil {
		t.Fatal("expected error for empty key")
	}
}

func TestParseRSAPublicKeyFormats(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	der, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		t.Fatal(err)
	}

	rawB64 := base64.StdEncoding.EncodeToString(der)
	pemBlock := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der})
	pemMultiline := string(pemBlock)
	pemSingleLine := "-----BEGIN PUBLIC KEY----- " +
		insertSpaces(rawB64, 64) +
		" -----END PUBLIC KEY-----"

	for name, input := range map[string]string{
		"raw_base64":        rawB64,
		"pem_multiline":     pemMultiline,
		"pem_single_line":   pemSingleLine,
	} {
		t.Run(name, func(t *testing.T) {
			got, err := ParseRSAPublicKeyPEM(input)
			if err != nil {
				t.Fatalf("parse failed: %v", err)
			}
			if got.N.Cmp(priv.PublicKey.N) != 0 {
				t.Fatal("parsed key does not match")
			}
		})
	}
}

func insertSpaces(s string, every int) string {
	if every <= 0 || len(s) <= every {
		return s
	}
	var out []byte
	for i := 0; i < len(s); i += every {
		end := i + every
		if end > len(s) {
			end = len(s)
		}
		if len(out) > 0 {
			out = append(out, ' ')
		}
		out = append(out, s[i:end]...)
	}
	return string(out)
}
