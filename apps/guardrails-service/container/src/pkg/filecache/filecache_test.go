package filecache

import (
	"context"
	"os"
	"strings"
	"testing"
	"time"

	redis "github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

func TestKey(t *testing.T) {
	a := Key("scope", []byte("file"))
	if !strings.HasPrefix(a, keyPrefix) || a != Key("scope", []byte("file")) {
		t.Fatalf("key is not stable: %q", a)
	}
	if a == Key("other scope", []byte("file")) || a == Key("scope", []byte("other file")) {
		t.Fatal("scope and bytes must both change the key")
	}
}

func TestNewRedisIsOffWithoutAClientOrTTL(t *testing.T) {
	client := redis.NewClient(&redis.Options{Addr: "127.0.0.1:1"})
	defer client.Close()
	if NewRedis(nil, time.Hour, zap.NewNop()) != nil || NewRedis(client, 0, zap.NewNop()) != nil {
		t.Fatal("want no store without a client or a positive TTL")
	}
}

// Every Redis failure reads as a miss: the file is inspected, never let through by the cache.
func TestUnreachableRedisIsAMiss(t *testing.T) {
	client := redis.NewClient(&redis.Options{Addr: "127.0.0.1:1", MaxRetries: -1})
	defer client.Close()
	store := NewRedis(client, time.Hour, zap.NewNop())
	store.Remember(context.Background(), "k")
	start := time.Now()
	if store.Allowed(context.Background(), "k") {
		t.Fatal("an unreachable Redis must read as a miss")
	}
	if elapsed := time.Since(start); elapsed > time.Second {
		t.Fatalf("lookup took %v; it must give up quickly", elapsed)
	}
}

// Against a real Redis when GUARDRAILS_TEST_REDIS_URL is set (e.g. redis://localhost:6379/15).
func TestRedisRoundTrip(t *testing.T) {
	url := os.Getenv("GUARDRAILS_TEST_REDIS_URL")
	if url == "" {
		t.Skip("GUARDRAILS_TEST_REDIS_URL not set")
	}
	opts, err := redis.ParseURL(url)
	if err != nil {
		t.Fatal(err)
	}
	client := redis.NewClient(opts)
	defer client.Close()
	ctx := context.Background()
	store := NewRedis(client, 2*time.Second, zap.NewNop())
	key := Key("test", []byte(time.Now().String()))
	defer client.Del(ctx, key)

	if store.Allowed(ctx, key) {
		t.Fatal("hit before remembering")
	}
	store.Remember(ctx, key)
	if !store.Allowed(ctx, key) {
		t.Fatal("miss after remembering")
	}
	if ttl := client.TTL(ctx, key).Val(); ttl <= 0 || ttl > 2*time.Second {
		t.Fatalf("ttl = %v, want the configured 2s", ttl)
	}
}
