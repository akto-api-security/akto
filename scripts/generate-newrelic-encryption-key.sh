#!/bin/bash
# Generate NewRelic Encryption Key Script
# Generates a secure 32-byte (256-bit) AES encryption key for NewRelic API key storage
#
# Usage:
#   ./generate-newrelic-encryption-key.sh
#   ./generate-newrelic-encryption-key.sh > key.txt
#   export NEWRELIC_ENCRYPTION_KEY=$(./generate-newrelic-encryption-key.sh)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}NewRelic Encryption Key Generator${NC}"
echo -e "${YELLOW}===================================${NC}"
echo ""

# Check if openssl is available
if ! command -v openssl &> /dev/null; then
    echo -e "${RED}ERROR: openssl is not installed${NC}"
    echo "Please install openssl and try again:"
    echo "  Ubuntu/Debian: sudo apt-get install openssl"
    echo "  macOS: brew install openssl"
    echo "  CentOS/RHEL: sudo yum install openssl"
    exit 1
fi

# Generate 32-byte random key and encode as base64
echo -e "${GREEN}Generating 32-byte (256-bit) AES encryption key...${NC}"
ENCRYPTION_KEY=$(openssl rand -base64 32)

echo ""
echo -e "${GREEN}✓ Key generated successfully!${NC}"
echo ""
echo -e "${YELLOW}Encryption Key (Base64):${NC}"
echo "$ENCRYPTION_KEY"
echo ""

# Verify key length
DECODED_LENGTH=$(echo "$ENCRYPTION_KEY" | base64 -d 2>/dev/null | wc -c)
if [ "$DECODED_LENGTH" -eq 32 ]; then
    echo -e "${GREEN}✓ Key verification: Valid (32 bytes = 256-bit AES)${NC}"
else
    echo -e "${RED}✗ Key verification failed: Expected 32 bytes, got $DECODED_LENGTH${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Usage Instructions:${NC}"
echo ""
echo "1. Store this key securely in one of these locations:"
echo ""
echo "   Option A: Environment Variable"
echo "   ----- "
echo "   export NEWRELIC_ENCRYPTION_KEY='$ENCRYPTION_KEY'"
echo ""
echo "   Option B: Kubernetes Secret"
echo "   -----"
echo "   kubectl create secret generic newrelic-secrets \\"
echo "     --from-literal=encryption-key='$ENCRYPTION_KEY' \\"
echo "     -n akto-production"
echo ""
echo "   Option C: AWS Secrets Manager"
echo "   -----"
echo "   aws secretsmanager create-secret \\"
echo "     --name newrelic/encryption-key \\"
echo "     --secret-string '$ENCRYPTION_KEY'"
echo ""
echo "   Option D: .env file (Development only)"
echo "   -----"
echo "   NEWRELIC_ENCRYPTION_KEY='$ENCRYPTION_KEY'"
echo ""
echo -e "${YELLOW}Security Notes:${NC}"
echo "  • Keep this key secret - do not commit to version control"
echo "  • Use different keys for dev/staging/production"
echo "  • Store in secure secret management system (Vault, AWS SM, etc.)"
echo "  • Rotate keys periodically"
echo "  • If key is compromised, generate a new one and re-encrypt API keys"
echo ""
echo -e "${GREEN}For more information, see: docs/newrelic-integration-setup.md${NC}"
