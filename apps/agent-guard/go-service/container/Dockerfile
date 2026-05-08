FROM golang:1.24-alpine AS builder

WORKDIR /app

# Copy go mod files
COPY src/go.mod src/go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY src/*.go ./

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o scanner-orchestrator .

# Final stage
FROM alpine:latest

RUN apk --no-cache add ca-certificates

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/scanner-orchestrator .

# Expose port
EXPOSE 8091

# Run the application
CMD ["./scanner-orchestrator"]

