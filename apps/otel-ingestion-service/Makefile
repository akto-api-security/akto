.PHONY: build test docker image run-local dev-keys functional benchmark

IMAGE ?= aktosecurity/otel-ingestion-service:local
SRC := container/src

build:
	cd $(SRC) && go build -o ../../bin/otel-ingestion-service .

test:
	cd $(SRC) && go test ./...

docker:
	docker build -t $(IMAGE) container

image: docker

run-local:
	cd $(SRC) && go run .

dev-keys:
	./scripts/generate_dev_token.sh

functional:
	./scripts/functional-test.sh

benchmark:
	./scripts/benchmark.sh
