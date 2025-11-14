# Build stage
FROM golang:1.24-alpine AS builder

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

# Download dependencies
RUN go mod download

# Clean up credentials after download
RUN rm -f /root/.netrc

# Copy source code from src directory
COPY src/ .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -ldflags="-w -s" -o guardrails-service .

# Final stage - use distroless for smaller image and better security
FROM gcr.io/distroless/static-debian11:nonroot

# Copy ca-certificates from builder
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/

# Set working directory
WORKDIR /app

# Copy the binary from builder
COPY --from=builder /app/guardrails-service .

# Create directory for mcp-endpoint-shield data
USER nonroot:nonroot

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/app/guardrails-service"]
