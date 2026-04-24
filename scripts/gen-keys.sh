#!/usr/bin/env bash
# Generates RSA key pair for JWT signing, prints values to paste into .env.
set -euo pipefail

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMP/priv.pem"
openssl rsa -in "$TMP/priv.pem" -pubout -out "$TMP/pub.pem"

PRIV=$(grep -v '^-----' "$TMP/priv.pem" | tr -d '\n')
PUB=$(grep -v '^-----' "$TMP/pub.pem"  | tr -d '\n')

echo "JWT_PRIVATE_KEY=$PRIV"
echo "JWT_PUBLIC_KEY=$PUB"
