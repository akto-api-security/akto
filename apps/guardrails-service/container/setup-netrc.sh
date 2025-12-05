#!/bin/bash

# Script to create .netrc file for GitHub authentication
# This is needed for Docker builds to access private repositories

if [ -z "$GITHUB_TOKEN" ]; then
    echo "Error: GITHUB_TOKEN environment variable is not set"
    echo "Please set it with: export GITHUB_TOKEN=your_token"
    exit 1
fi

# Create .netrc file
cat > .netrc <<EOF
machine github.com
login x-access-token
password $GITHUB_TOKEN
EOF

chmod 600 .netrc

echo "✓ .netrc file created successfully"
echo "You can now run: npx wrangler deploy"
echo ""
echo "⚠️  Remember: Never commit the .netrc file to git!"
