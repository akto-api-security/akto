#!/bin/bash

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "Error: GITHUB_TOKEN environment variable is not set"
    echo "Please set it with: export GITHUB_TOKEN=your_token"
    exit 1
fi

# Export Docker BuildKit environment variable and deploy
export DOCKER_BUILDKIT=1

# Deploy with wrangler
GITHUB_TOKEN=$GITHUB_TOKEN npx wrangler deploy

echo "Deployment complete!"
