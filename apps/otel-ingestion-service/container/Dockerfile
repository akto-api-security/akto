# syntax=docker/dockerfile:1
FROM golang:1.24-bookworm AS builder

WORKDIR /app

COPY src/go.mod src/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY src/ .

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o otel-ingestion-service .

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -u 65532 -r -s /usr/sbin/nologin nonroot

WORKDIR /app

COPY --from=builder /app/otel-ingestion-service .

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["/app/otel-ingestion-service"]
