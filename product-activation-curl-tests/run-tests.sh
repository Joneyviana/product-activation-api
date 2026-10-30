#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/01-get-token.sh"
echo
echo "=== API test ==="
# Forward environment variables such as API_BASE_URL and REQUEST_BODY_FILE.
"${SCRIPT_DIR}/02-test-api.sh"

echo "== tabela =="
"${SCRIPT_DIR}/show_requests.sh"
