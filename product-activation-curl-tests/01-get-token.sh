#!/usr/bin/env bash
set -Eeuo pipefail

# Keycloak local defaults. Override through environment variables if needed.
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8000}"
REALM="${REALM:-product-activation}"
CLIENT_ID="${CLIENT_ID:-activation-api}"
USERNAME="${USERNAME:-dev-test}"
PASSWORD="${PASSWORD:-dev-test}"
TOKEN_FILE="${TOKEN_FILE:-.access_token}"

command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required." >&2; exit 1; }

TOKEN_URL="${KEYCLOAK_URL%/}/realms/${REALM}/protocol/openid-connect/token"
echo "Requesting access token from: ${TOKEN_URL}"

RESPONSE="$(curl --silent --show-error --fail-with-body \
  --request POST "$TOKEN_URL" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}")" || {
    echo "ERROR: token request failed. Check Keycloak, realm, client and user credentials." >&2
    exit 1
  }

ACCESS_TOKEN="$(printf '%s' "$RESPONSE" | jq -r '.access_token // empty')"
if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: Keycloak response did not contain access_token:" >&2
  printf '%s\n' "$RESPONSE" >&2
  exit 1
fi

umask 077
printf '%s' "$ACCESS_TOKEN" > "$TOKEN_FILE"
echo "Token obtained and saved to ${TOKEN_FILE}"
echo "Token expires in: $(printf '%s' "$RESPONSE" | jq -r '.expires_in // "unknown"') seconds"
