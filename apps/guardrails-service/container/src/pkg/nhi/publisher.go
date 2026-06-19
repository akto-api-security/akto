package nhi

import (
	"strings"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/nhi/extract"
	"go.uber.org/zap"
)

// maxBatchSize bounds a single upsertNhiIdentities call. Cyborg processes the
// list serially; large batches hold a connection open. 200 is a comfortable
// per-tick payload for any single device scan.
const maxBatchSize = 200

// Publisher is the single sink for all NHI findings, regardless of which Source
// produced them. It owns the redaction step, the wire-format conversion, and
// the batched upsert to cyborg.
//
// One Publisher is shared across every Source. It is goroutine-safe: each Run
// goroutine may call Publish concurrently.
type Publisher struct {
	client *dbabstractor.Client
	logger *zap.Logger
}

// NewPublisher wires a Publisher against an existing cyborg client. The client
// is reused (no separate HTTP connection pool for NHI), so the rest of the
// guardrails-service's cyborg activity benefits from request coalescing.
func NewPublisher(client *dbabstractor.Client, logger *zap.Logger) *Publisher {
	return &Publisher{client: client, logger: logger}
}

// Publish redacts each finding, builds NhiIdentity records using pctx as the
// envelope, and pushes them to cyborg in batches of up to maxBatchSize.
//
// Per-finding behaviour:
//   - empty Secret → silently skipped (extractor bug, but not fatal)
//   - per-source defaults from pctx fill in fields the extractor left blank
//   - the raw Secret is consumed by Redact() and is not referenced again
//
// If a batch fails, the error is logged with the batch size and pctx — never
// with secret values or hashes individually (defence in depth against log
// scraping). The next Publish call is unaffected.
func (p *Publisher) Publish(pctx PublishContext, findings []extract.Finding) {
	if len(findings) == 0 {
		return
	}

	batch := make([]any, 0, len(findings))
	for _, f := range findings {
		if strings.TrimSpace(f.Secret) == "" {
			continue
		}
		prefix, suffix, hash := Redact(f.Secret)
		identity := NhiIdentity{
			IdentityName:  f.IdentityName,
			IdentityType:  f.IdentityType,
			ContextSource: pctx.ContextSource,
			AgentName:     coalesce(f.AgentName, pctx.AgentName),
			AgentType:     coalesce(f.AgentType, pctx.AgentType),
			Owner:         ownerFromLabel(pctx.DeviceLabel),
			AccessLevel:   defaultAccessLevel(f.IdentityType),
			Status:        "ACTIVE",
			RiskLevel:     f.RiskLevel,
			ExpiryDate:    f.ExpiryDate,
			Prefix:        prefix,
			Suffix:        suffix,
			Hash:          hash,
			Source:        pctx.Source,
			SourceType:    coalesce(pctx.SourceType, f.SourceType),
			DeviceID:      pctx.DeviceID,
			DeviceLabel:   pctx.DeviceLabel,
			Metadata:      f.Metadata,
		}
		batch = append(batch, identity)

		if len(batch) >= maxBatchSize {
			p.flush(pctx, batch)
			batch = batch[:0]
		}
	}
	if len(batch) > 0 {
		p.flush(pctx, batch)
	}
}

func (p *Publisher) flush(pctx PublishContext, batch []any) {
	if err := p.client.UpsertNhiIdentities(batch); err != nil {
		p.logger.Warn("NHI: batch upsert failed",
			zap.String("contextSource", pctx.ContextSource),
			zap.String("sourceType", pctx.SourceType),
			zap.String("device", pctx.DeviceID),
			zap.Int("batchSize", len(batch)),
			zap.Error(err))
	}
}

func coalesce(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

func ownerFromLabel(label string) *Owner {
	if label == "" {
		return nil
	}
	return &Owner{Name: label}
}

// defaultAccessLevel is a conservative guess at what the credential can do.
// Service accounts / machine identities historically map to admin-shaped
// scopes (full API access), while bearer/API keys most commonly carry
// read-write scope. Policies can override per-identity later.
func defaultAccessLevel(identityType string) string {
	switch identityType {
	case "Service Account", "Machine Identity":
		return "ADMIN"
	default:
		return "READ_WRITE"
	}
}
