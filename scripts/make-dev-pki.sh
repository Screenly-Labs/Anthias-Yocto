#!/bin/sh
#
# Generate the development PKI used to sign RAUC bundles for test builds.
#
#   ./scripts/make-dev-pki.sh
#
# These files are deliberately NOT in git. This repository is public, and the
# CA private key is the root of trust for every device flashed with a non-
# production image: publishing it would let anyone forge an update that those
# devices accept. Generate your own locally; they are gitignored.
#
# For a real fleet do not use this at all - see kas/production.yml and
# ANTHIAS_SIGNING_DIR, and keep the CA key offline or in an HSM.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUNDLE_DIR="$ROOT/recipes-core/rauc-bundles/files"
KEYRING_DIR="$ROOT/recipes-core/rauc/files"
mkdir -p "$BUNDLE_DIR" "$KEYRING_DIR"

if [ -f "$BUNDLE_DIR/development-1.key.pem" ] && [ "${FORCE:-0}" != "1" ]; then
    echo "Development PKI already present. Re-run with FORCE=1 to replace it."
    exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
umask 077

cat > "$TMP/ca.cnf" <<'CNF'
[req]
distinguished_name = dn
prompt             = no
x509_extensions    = v3_ca
[dn]
O  = Anthias
CN = Anthias Development CA
[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage         = critical,keyCertSign,cRLSign
CNF

cat > "$TMP/leaf.cnf" <<'CNF'
[req]
distinguished_name = dn
prompt             = no
[dn]
O  = Anthias
CN = Anthias Development Signing Key 1
[v3_leaf]
basicConstraints = critical,CA:FALSE
keyUsage         = critical,digitalSignature
# rauc's system.conf sets check-purpose=codesign; without this EKU rauc
# rejects the bundle with "unsuitable certificate purpose".
extendedKeyUsage = codeSigning
CNF

echo "Generating development CA (20 years)..."
openssl req -x509 -newkey rsa:4096 -nodes -sha256 -days 7300 \
    -keyout "$BUNDLE_DIR/ca.key.pem" -out "$KEYRING_DIR/ca.cert.pem" \
    -config "$TMP/ca.cnf" 2>/dev/null

echo "Generating signing key (10 years)..."
openssl req -new -newkey rsa:4096 -nodes -sha256 \
    -keyout "$BUNDLE_DIR/development-1.key.pem" -out "$TMP/leaf.csr" \
    -config "$TMP/leaf.cnf" 2>/dev/null

openssl x509 -req -in "$TMP/leaf.csr" -sha256 -days 3650 \
    -CA "$KEYRING_DIR/ca.cert.pem" -CAkey "$BUNDLE_DIR/ca.key.pem" \
    -CAcreateserial -out "$BUNDLE_DIR/development-1.cert.pem" \
    -extfile "$TMP/leaf.cnf" -extensions v3_leaf 2>/dev/null

chmod 0600 "$BUNDLE_DIR/ca.key.pem" "$BUNDLE_DIR/development-1.key.pem"
chmod 0644 "$KEYRING_DIR/ca.cert.pem" "$BUNDLE_DIR/development-1.cert.pem"

openssl verify -purpose codesign -CAfile "$KEYRING_DIR/ca.cert.pem" \
    "$BUNDLE_DIR/development-1.cert.pem"
echo "Development PKI written. These files are gitignored - do not commit them."
