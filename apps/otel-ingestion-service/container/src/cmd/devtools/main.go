// Dev utilities for local OTLP testing (JWT signing, protobuf fixtures).
package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/plog"
	"go.opentelemetry.io/collector/pdata/plog/plogotlp"
)

// Cowork OTel docs:
// - https://support.claude.com/en/articles/14477985-monitor-claude-cowork-activity-with-opentelemetry
// - https://claude.com/docs/cowork/monitoring

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(1)
	}
	switch os.Args[1] {
	case "jwt":
		runJWT(os.Args[2:])
	case "protobuf-fixture":
		runProtobufFixture(os.Args[2:])
	default:
		usage()
		os.Exit(1)
	}
}

func usage() {
	fmt.Fprintf(os.Stderr, `Usage:
  devtools jwt [--account ID] [--out-dir DIR]
    Generate RSA keypair, write public.pem / private.pem, print RSA_PUBLIC_KEY and a signed JWT.

  devtools protobuf-fixture [--out FILE]
    Write a Cowork-style OTLP ExportLogsServiceRequest protobuf (user_prompt event).

Docs: https://claude.com/docs/cowork/monitoring
`)
}

func runJWT(args []string) {
	fs := flag.NewFlagSet("jwt", flag.ExitOnError)
	account := fs.Int("account", 42, "accountId claim")
	outDir := fs.String("out-dir", "", "directory to write public.pem and private.pem")
	_ = fs.Parse(args)

	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		fatal(err)
	}

	pubDER, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		fatal(err)
	}
	pubB64 := base64.StdEncoding.EncodeToString(pubDER)

	if *outDir != "" {
		if err := os.MkdirAll(*outDir, 0o755); err != nil {
			fatal(err)
		}
		if err := os.WriteFile(*outDir+"/public.pem", pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: pubDER}), 0o644); err != nil {
			fatal(err)
		}
		privDER, err := x509.MarshalPKCS8PrivateKey(priv)
		if err != nil {
			fatal(err)
		}
		if err := os.WriteFile(*outDir+"/private.pem", pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privDER}), 0o600); err != nil {
			fatal(err)
		}
	}

	claims := jwt.MapClaims{
		"accountId": *account,
		"exp":       time.Now().Add(24 * time.Hour).Unix(),
		"iss":       "Akto",
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, err := token.SignedString(priv)
	if err != nil {
		fatal(err)
	}

	fmt.Println("RSA_PUBLIC_KEY=" + pubB64)
	fmt.Println("JWT=" + signed)
}

func runProtobufFixture(args []string) {
	fs := flag.NewFlagSet("protobuf-fixture", flag.ExitOnError)
	out := fs.String("out", "", "output file path (default stdout)")
	_ = fs.Parse(args)

	logs := plog.NewLogs()
	rl := logs.ResourceLogs().AppendEmpty()
	rl.Resource().Attributes().PutStr("service.name", "cowork") // Cowork resource attr
	sl := rl.ScopeLogs().AppendEmpty()
	rec := sl.LogRecords().AppendEmpty()
	rec.SetEventName("user_prompt") // https://claude.com/docs/cowork/monitoring#user-prompt-event
	rec.SetTimestamp(pcommon.NewTimestampFromTime(time.Unix(1_700_000_000, 0)))
	rec.Attributes().PutStr("prompt.id", "prompt-test-abc")
	rec.Attributes().PutStr("session.id", "session-test-xyz")
	rec.Attributes().PutStr("user.email", "user@example.com")
	rec.Attributes().PutInt("prompt_length", 12)
	rec.Attributes().PutStr("prompt", "hello cowork")

	req := plogotlp.NewExportRequestFromLogs(logs)
	body, err := req.MarshalProto()
	if err != nil {
		fatal(err)
	}

	if *out == "" {
		_, _ = os.Stdout.Write(body)
		return
	}
	if err := os.WriteFile(*out, body, 0o644); err != nil {
		fatal(err)
	}
	fmt.Fprintf(os.Stderr, "wrote %d bytes to %s\n", len(body), *out)
}

func fatal(err error) {
	fmt.Fprintf(os.Stderr, "error: %v\n", err)
	os.Exit(1)
}
