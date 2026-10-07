# Build stage - use native platform for building (avoids QEMU emulation issues)
FROM --platform=$BUILDPLATFORM golang:1.25-alpine AS builder

# Build arguments for cross-compilation (automatically set by BuildKit)
ARG TARGETOS
ARG TARGETARCH

# Install build dependencies
RUN apk add --no-cache git make

# Set working directory
WORKDIR /app

# Set GOPRIVATE to skip checksum verification for private repos
ENV GOPRIVATE=github.com/akto-api-security/*
ENV GONOSUMDB=github.com/akto-api-security/*

# Copy .netrc for GitHub authentication (create one with your token first)
# Run: echo -e "machine github.com\nlogin x-access-token\npassword YOUR_TOKEN" > .netrc
COPY --chmod=600 .netrc /root/.netrc

# Copy go mod files from src directory
COPY src/go.mod src/go.sum ./

# Copy local replacement modules referenced by replace directives in go.mod
# (required so `go mod download` can resolve them)
COPY src/internal/stub/ ./internal/stub/

# Download dependencies
RUN go mod download

# Clean up credentials after download
RUN rm -f /root/.netrc

# Copy source code from src directory
COPY src/ .

# Run unit tests before building the release binary.
RUN go test ./...

# Build the application with cross-compilation
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} go build -a -installsuffix cgo -ldflags="-w -s" -o guardrails-service .

# Final stage - alpine so a shell (/bin/sh, bash) is available for pre-start steps
FROM alpine:3.22

# CA certs for outbound TLS; nonroot user keeps distroless's UID/GID (65532) and
# writable /home/nonroot (endpoint-shield writes ~/.akto-endpoint-shield)
RUN apk add --no-cache ca-certificates bash \
    && addgroup -S -g 65532 nonroot \
    && adduser -S -D -h /home/nonroot -u 65532 -G nonroot -s /bin/bash nonroot

# Set working directory
WORKDIR /app

# Copy the binary from builder
COPY --from=builder --chown=nonroot:nonroot /app/guardrails-service .

USER nonroot:nonroot

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/guardrails-service"]
