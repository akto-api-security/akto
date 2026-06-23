package nhi

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
)

// Source is the contract every NHI collector implements.
type Source interface {
	Name() string
	Run(ctx context.Context)
}

// PublishContext is the source-supplied envelope around a batch of findings.
type PublishContext struct {
	DeviceID      string
	DeviceLabel   string
	Source        string
	SourceType    string
	AgentName     string
	AgentType     string
	ContextSource string
}

// Owner is the human identity responsible for a credential.
type Owner struct {
	Name  string `json:"name,omitempty"`
	Email string `json:"email,omitempty"`
}

// NhiIdentity is the wire shape posted to cyborg's /api/upsertNhiIdentity.
// Field names match com.akto.dto.nhi_governance.NhiIdentity.
type NhiIdentity struct {
	IdentityName  string         `json:"identityName"`
	IdentityType  string         `json:"identityType,omitempty"`
	ContextSource string         `json:"contextSource,omitempty"`
	AgentName     string         `json:"agentName,omitempty"`
	AgentType     string         `json:"agentType,omitempty"`
	Owner         *Owner         `json:"owner,omitempty"`
	AccessLevel   string         `json:"accessLevel,omitempty"`
	Status        string         `json:"status,omitempty"`
	RiskLevel     string         `json:"riskLevel,omitempty"`
	ExpiryDate    int            `json:"expiryDate,omitempty"`
	Prefix        string         `json:"prefix,omitempty"`
	Suffix        string         `json:"suffix,omitempty"`
	Hash          string         `json:"hash"`
	Source        string         `json:"source"`
	SourceType    string         `json:"sourceType,omitempty"`
	DeviceID      string         `json:"deviceId,omitempty"`
	DeviceLabel   string         `json:"deviceLabel,omitempty"`
	Metadata      map[string]any `json:"metadata,omitempty"`
}

// Redact converts a raw secret to the only three values ever persisted:
// prefix (first 4 chars), suffix (last 4 chars), and SHA-256 hash.
// Secrets shorter than 8 chars return empty prefix/suffix.
func Redact(secret string) (prefix, suffix, hash string) {
	sum := sha256.Sum256([]byte(secret))
	hash = hex.EncodeToString(sum[:])
	if len(secret) >= 8 {
		prefix = secret[:4]
		suffix = secret[len(secret)-4:]
	}
	return
}
