package keystore

import (
	"fmt"
	"time"

	"go.uber.org/zap"
)

type Options struct {
	AuthEnabled       bool
	RSAPublicKey      string
	MongoConn         string
	MongoDB           string
	KeyRefreshMinutes int
	Logger            *zap.Logger
}

// New mirrors database-abstractor / data-ingestion auth key resolution:
// RSA_PUBLIC_KEY env overrides Mongo HYBRID_SAAS config when set.
func New(opts Options) (Provider, error) {
	if !opts.AuthEnabled {
		return nil, nil
	}

	if opts.RSAPublicKey != "" {
		return NewStaticProvider(opts.RSAPublicKey)
	}

	if opts.MongoConn != "" {
		refresh := time.Duration(opts.KeyRefreshMinutes) * time.Minute
		if refresh <= 0 {
			refresh = 5 * time.Minute
		}
		return NewMongoProvider(opts.MongoConn, opts.MongoDB, refresh, opts.Logger)
	}

	return nil, fmt.Errorf("auth enabled: set RSA_PUBLIC_KEY or AKTO_MONGO_CONN")
}
