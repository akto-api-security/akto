"""RS256 JWT assertions for the OAuth token minters (gcp_auth, azure_ad_auth).

Pure Python (`rsa` + `pyasn1`) because the native `cryptography` lib cannot
load in Pyodide/workerd.
"""

import base64
import json

import rsa
from pyasn1.codec.der import decoder as der_decoder


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def load_private_key(pem: str) -> "rsa.PrivateKey":
    """Load a PKCS#1 ("BEGIN RSA PRIVATE KEY") or PKCS#8 ("BEGIN PRIVATE KEY") PEM key."""
    if "BEGIN RSA PRIVATE KEY" in pem:
        return rsa.PrivateKey.load_pkcs1(pem.encode(), format="PEM")
    body = "".join(line for line in pem.splitlines() if line and not line.startswith("-----"))
    pkcs8_der = base64.b64decode(body)
    decoded, _ = der_decoder.decode(pkcs8_der)
    # PKCS#8 PrivateKeyInfo: [version, algorithm, privateKey OCTET STRING (PKCS#1)]
    pkcs1_der = bytes(decoded[2])
    return rsa.PrivateKey.load_pkcs1(pkcs1_der, format="DER")


def sign_rs256(header: dict, claims: dict, private_key: "rsa.PrivateKey") -> str:
    """Return the compact JWT `header.claims.signature`, RS256-signed."""
    signing_input = b64url(json.dumps(header).encode()) + "." + b64url(json.dumps(claims).encode())
    signature = rsa.sign(signing_input.encode(), private_key, "SHA-256")
    return signing_input + "." + b64url(signature)
