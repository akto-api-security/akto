package keystore

import (
	"context"
	"crypto/rsa"
)

type staticProvider struct {
	key *rsa.PublicKey
}

func NewStaticProvider(pem string) (Provider, error) {
	key, err := ParseRSAPublicKeyPEM(pem)
	if err != nil {
		return nil, err
	}
	return &staticProvider{key: key}, nil
}

func (p *staticProvider) PublicKey(context.Context) (*rsa.PublicKey, error) {
	return p.key, nil
}

func (p *staticProvider) Close(context.Context) error {
	return nil
}
