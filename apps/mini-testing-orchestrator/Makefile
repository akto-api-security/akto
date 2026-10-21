# Local builds only; release images come from the Production workflow.
IMAGE ?= akto-mini-testing-orchestrator
TAG ?= dev

.PHONY: build test vet image

build:
	CGO_ENABLED=0 go build -ldflags "-X main.version=$(TAG)" -o bin/orchestrator ./cmd/orchestrator

test:
	go test ./...

vet:
	go vet ./...

image:
	docker build --build-arg VERSION=$(TAG) -t $(IMAGE):$(TAG) .
