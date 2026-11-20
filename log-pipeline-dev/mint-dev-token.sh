#!/bin/bash
# Local-dev only. Cyborg's AuthFilter verifies an RS256 JWT against the public key stored in
# <mongo-main>/common.configs {_id:"HYBRID_SAAS"}. This seeds a fresh keypair there (once) and prints a JWT
# with {"accountId": <ACCOUNT_ID>}. Usage: ./mint-dev-token.sh [accountId=1000000]
# Run it BEFORE/after starting cyborg (cyborg reads the key on every request, no restart needed).
set -euo pipefail
cd "$(dirname "$0")"
ACCOUNT_ID="${1:-1000000}"
KEYDIR="$(pwd)/.keys"; mkdir -p "$KEYDIR"
if [ ! -f "$KEYDIR/private.pem" ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYDIR/private.pem" 2>/dev/null
  openssl pkey -in "$KEYDIR/private.pem" -pubout -out "$KEYDIR/public.pem"
fi
# Strip PEM armor/newlines (JwtAuthenticator strips them itself; either form works).
PRIV=$(grep -v -- '-----' "$KEYDIR/private.pem" | tr -d '\n')
PUB=$(grep -v -- '-----' "$KEYDIR/public.pem" | tr -d '\n')
docker compose exec -T mongo-main mongosh --quiet --eval "
db.getSiblingDB('common').configs.replaceOne({_id:'HYBRID_SAAS'},
 {_id:'HYBRID_SAAS', _t:'com.akto.dto.Config\$HybridSaasConfig', configType:'HYBRID_SAAS', privateKey:'$PRIV', publicKey:'$PUB'},
 {upsert:true})" >/dev/null
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
EXP=$(( $(date +%s) + 30*24*3600 ))
P=$(printf '{"accountId":%s,"iat":%s,"exp":%s}' "$ACCOUNT_ID" "$(date +%s)" "$EXP" | b64url)
S=$(printf '%s.%s' "$H" "$P" | openssl dgst -sha256 -sign "$KEYDIR/private.pem" | b64url)
echo "$H.$P.$S"
