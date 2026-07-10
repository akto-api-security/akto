package keystore

import (
	"context"
	"crypto/rsa"
	"fmt"
	"sync"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"go.uber.org/zap"
)

type mongoProvider struct {
	client          *mongo.Client
	dbName          string
	refreshInterval time.Duration
	logger          *zap.Logger

	mu          sync.RWMutex
	key         *rsa.PublicKey
	lastRefresh time.Time
}

func NewMongoProvider(mongoURI, dbName string, refreshInterval time.Duration, logger *zap.Logger) (Provider, error) {
	if mongoURI == "" {
		return nil, fmt.Errorf("AKTO_MONGO_CONN is empty")
	}
	if dbName == "" {
		dbName = "common"
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	client, err := mongo.Connect(ctx, options.Client().ApplyURI(mongoURI))
	if err != nil {
		return nil, fmt.Errorf("mongo connect: %w", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		_ = client.Disconnect(ctx)
		return nil, fmt.Errorf("mongo ping: %w", err)
	}

	p := &mongoProvider{
		client:          client,
		dbName:          dbName,
		refreshInterval: refreshInterval,
		logger:          logger,
	}
	if err := p.refresh(ctx); err != nil {
		_ = client.Disconnect(ctx)
		return nil, err
	}
	return p, nil
}

func (p *mongoProvider) PublicKey(ctx context.Context) (*rsa.PublicKey, error) {
	p.mu.RLock()
	key := p.key
	stale := time.Since(p.lastRefresh) > p.refreshInterval
	p.mu.RUnlock()

	if key != nil && !stale {
		return key, nil
	}

	if err := p.refresh(ctx); err != nil {
		if key != nil {
			p.logger.Warn("failed to refresh HYBRID_SAAS public key from mongo, using cached key", zap.Error(err))
			return key, nil
		}
		return nil, err
	}

	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.key, nil
}

func (p *mongoProvider) refresh(ctx context.Context) error {
	var doc struct {
		PublicKey string `bson:"publicKey"`
	}
	coll := p.client.Database(p.dbName).Collection("configs")
	if err := coll.FindOne(ctx, bson.M{"_id": hybridSaasConfigID}).Decode(&doc); err != nil {
		return fmt.Errorf("fetch HYBRID_SAAS config: %w", err)
	}
	if doc.PublicKey == "" {
		return fmt.Errorf("HYBRID_SAAS publicKey is empty")
	}

	key, err := ParseRSAPublicKeyPEM(doc.PublicKey)
	if err != nil {
		return fmt.Errorf("parse HYBRID_SAAS publicKey: %w", err)
	}

	p.mu.Lock()
	p.key = key
	p.lastRefresh = time.Now()
	p.mu.Unlock()
	return nil
}

func (p *mongoProvider) Close(ctx context.Context) error {
	if p.client == nil {
		return nil
	}
	return p.client.Disconnect(ctx)
}
