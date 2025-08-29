# Variables
BINARY_NAME=mcp-proxy
API_BINARY_NAME=mcp-api
CLI_BINARY_NAME=mcp-cli
VERSION=$(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
BUILD_TIME=$(shell date -u '+%Y-%m-%d_%H:%M:%S')
LDFLAGS=-ldflags "-X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME}"

# Go parameters
GOCMD=go
GOBUILD=$(GOCMD) build
GOCLEAN=$(GOCMD) clean
GOTEST=$(GOCMD) test
GOMOD=$(GOCMD) mod
BINARY_DIR=bin

# Default target
.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help message
	@echo "MCP Threat Detection - Go Implementation"
	@echo "========================================"
	@echo ""
	@echo "Available targets:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: build
build: ## Build both API and CLI binaries
	@echo "Building binaries..."
	@mkdir -p $(BINARY_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BINARY_DIR)/$(API_BINARY_NAME) ./cmd/api
	$(GOBUILD) $(LDFLAGS) -o $(BINARY_DIR)/$(CLI_BINARY_NAME) ./cmd/cli
	@echo "Binaries built successfully in $(BINARY_DIR)/"

.PHONY: build-api
build-api: ## Build only the API binary
	@echo "Building API binary..."
	@mkdir -p $(BINARY_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BINARY_DIR)/$(API_BINARY_NAME) ./cmd/api
	@echo "API binary built successfully: $(BINARY_DIR)/$(API_BINARY_NAME)"

.PHONY: build-cli
build-cli: ## Build only the CLI binary
	@echo "Building CLI binary..."
	@mkdir -p $(BINARY_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BINARY_DIR)/$(CLI_BINARY_NAME) ./cmd/cli
	@echo "CLI binary built successfully: $(BINARY_DIR)/$(CLI_BINARY_NAME)"

.PHONY: clean
clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	$(GOCLEAN)
	@rm -rf $(BINARY_DIR)
	@echo "Clean complete"

.PHONY: test
test: ## Run tests
	@echo "Running tests..."
	$(GOTEST) -v ./...

.PHONY: deps
deps: ## Download dependencies
	@echo "Downloading dependencies..."
	$(GOMOD) download
	@echo "Dependencies downloaded"

.PHONY: deps-tidy
deps-tidy: ## Tidy dependencies
	@echo "Tidying dependencies..."
	$(GOMOD) tidy
	@echo "Dependencies tidied"

.PHONY: fmt
fmt: ## Format code
	@echo "Formatting code..."
	$(GOCMD) fmt ./...
	@echo "Code formatted"

.PHONY: vet
vet: ## Run go vet
	@echo "Running go vet..."
	$(GOCMD) vet ./...
	@echo "Go vet completed"

.PHONY: run-api
run-api: ## Run the API server
	@echo "Starting API server..."
	$(GOCMD) run ./cmd/api

.PHONY: run-cli
run-cli: ## Run the CLI (example)
	@echo "Running CLI example..."
	$(GOCMD) run ./cmd/cli --help

.PHONY: docker-build
docker-build: ## Build Docker image
	@echo "Building Docker image..."
	docker build -t $(BINARY_NAME):$(VERSION) .
	docker tag $(BINARY_NAME):$(VERSION) $(BINARY_NAME):latest
	@echo "Docker image built: $(BINARY_NAME):$(VERSION)"

.PHONY: docker-run
docker-run: ## Run Docker container
	@echo "Running Docker container..."
	docker run -p 8080:8080 --env-file .env $(BINARY_NAME):latest

.PHONY: docker-clean
docker-clean: ## Clean Docker images
	@echo "Cleaning Docker images..."
	docker rmi $(BINARY_NAME):$(VERSION) $(BINARY_NAME):latest || true
	@echo "Docker images cleaned"

.PHONY: install
install: ## Install binaries to system
	@echo "Installing binaries..."
	$(GOBUILD) $(LDFLAGS) -o /usr/local/bin/$(API_BINARY_NAME) ./cmd/api
	$(GOBUILD) $(LDFLAGS) -o /usr/local/bin/$(CLI_BINARY_NAME) ./cmd/cli
	@echo "Binaries installed to /usr/local/bin/"

.PHONY: uninstall
uninstall: ## Uninstall binaries from system
	@echo "Uninstalling binaries..."
	@rm -f /usr/local/bin/$(API_BINARY_NAME)
	@rm -f /usr/local/bin/$(CLI_BINARY_NAME)
	@echo "Binaries uninstalled"

.PHONY: all
all: clean deps fmt vet test build ## Run all checks and build
	@echo "All tasks completed successfully!" 