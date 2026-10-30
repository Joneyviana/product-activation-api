#!/usr/bin/env bash
set -Eeuo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8081}"
REQUESTS_PATH="${REQUESTS_PATH:-/api/v1/requests}"
TOKEN_FILE="${TOKEN_FILE:-.access_token}"
# Pass a JSON request body file, for example: REQUEST_BODY_FILE=./request-success.json ./02-test-api.sh
REQUEST_BODY_FILE="${REQUEST_BODY_FILE:-}"

command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required." >&2; exit 1; }
[[ -s "$TOKEN_FILE" ]] || { echo "ERROR: token file '$TOKEN_FILE' not found. Run ./01-get-token.sh first." >&2; exit 1; }
[[ -n "$REQUEST_BODY_FILE" && -f "$REQUEST_BODY_FILE" ]] || {
  echo "ERROR: set REQUEST_BODY_FILE to a JSON file matching your API request DTO." >&2
  echo "Example: REQUEST_BODY_FILE=./request.json ./02-test-api.sh" >&2
  exit 1
}

ACCESS_TOKEN="$(cat "$TOKEN_FILE")"
URL="${API_BASE_URL%/}${REQUESTS_PATH}"
echo "Calling POST ${URL}"
curl --silent --show-error --include \
  --request POST "$URL" \
  --header "Authorization: Bearer ${ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --data-binary "@${REQUEST_BODY_FILE}"
printf '\n'
