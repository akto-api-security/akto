# syntax=docker/dockerfile:1
# Build stage — TARGETPLATFORM required for CGO + native Vectorscan per arch.
FROM --platform=$TARGETPLATFORM golang:1.24-bookworm AS builder

ARG TARGETOS
ARG TARGETARCH

RUN apt-get update && apt-get install -y --no-install-recommends \
    git make gcc pkg-config libvectorscan-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

ENV GOPRIVATE=github.com/akto-api-security/*
ENV GONOSUMDB=github.com/akto-api-security/*
ENV CGO_ENABLED=1

COPY --chmod=600 .netrc /root/.netrc

COPY src/go.mod src/go.sum ./

COPY src/internal/stub/ ./internal/stub/

RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

RUN rm -f /root/.netrc

COPY src/ .

# Unit tests run in CI (see staging.yml). In-image tests doubled compile time because
# they ran without -tags hyperscan while the binary build requires CGO + hyperscan.
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -tags hyperscan -ldflags="-w -s" -o guardrails-service .

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libvectorscan5 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -u 65532 -r -s /usr/sbin/nologin nonroot

WORKDIR /app

COPY --from=builder /app/guardrails-service .

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["/app/guardrails-service"]
