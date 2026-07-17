package keystore

import (
	"context"
	"crypto/rsa"
)

const hybridSaasConfigID = "HYBRID_SAAS"

type Provider interface {
	PublicKey(ctx context.Context) (*rsa.PublicKey, error)
	Close(context.Context) error
}
