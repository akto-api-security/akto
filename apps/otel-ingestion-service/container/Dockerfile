# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM golang:1.24-alpine AS builder

ARG TARGETOS
ARG TARGETARCH

RUN apk add --no-cache git

WORKDIR /app

ENV GOPRIVATE=github.com/akto-api-security/*
ENV GONOSUMDB=github.com/akto-api-security/*

COPY --chmod=600 .netrc /root/.netrc

COPY src/go.mod src/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

RUN rm -f /root/.netrc

COPY src/ .
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -ldflags="-w -s" -o otel-ingestion-service .

FROM gcr.io/distroless/static-debian11:nonroot

COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/

WORKDIR /app
COPY --from=builder /app/otel-ingestion-service .

EXPOSE 8080

ENTRYPOINT ["/app/otel-ingestion-service"]
