#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-drools}"
POD_SELECTOR="${POD_SELECTOR:-app=kie-workbench}"
CONTAINER="${CONTAINER:-kie-workbench}"
GRAVITEE_URL="${GRAVITEE_URL:-http://gravitee-gateway.k8s-api-gateway.svc.cluster.local:8082}"
CONTAINER_ID="${CONTAINER_ID:-tool-use-policy}"
SESSION="${SESSION:-tool-use-ksession}"
MESSAGE="${MESSAGE:-db-test-$(date +%s)}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl not found in PATH" >&2
  exit 1
fi

POD="$(kubectl -n "$NAMESPACE" get pods -l "$POD_SELECTOR" -o jsonpath='{.items[0].metadata.name}')"
if [ -z "$POD" ]; then
  echo "ERROR: no pods found for selector ${POD_SELECTOR} in namespace ${NAMESPACE}" >&2
  exit 1
fi

kubectl -n "$NAMESPACE" exec "$POD" -c "$CONTAINER" -- env \
  GRAVITEE_URL="$GRAVITEE_URL" \
  CONTAINER_ID="$CONTAINER_ID" \
  SESSION="$SESSION" \
  MESSAGE="$MESSAGE" \
  sh -s <<'EOF'
set -euo pipefail

. /vault-secrets/oidc-env.sh
. /vault-secrets/kie-users-env.sh

TOKEN_URL="${OIDC_AUTH_URL%/}/realms/${OIDC_REALM}/protocol/openid-connect/token"
TOKEN_JSON="$(curl -sS -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${OIDC_CLIENT_ID}" \
  --data-urlencode "client_secret=${OIDC_CLIENT_SECRET}" \
  --data-urlencode "username=${KIE_SERVER_CONTROLLER_USER}" \
  --data-urlencode "password=${KIE_SERVER_CONTROLLER_PWD}" \
  "$TOKEN_URL")"

TOKEN="$(TOKEN_JSON="$TOKEN_JSON" python -c 'import os,json; print(json.loads(os.environ["TOKEN_JSON"]).get("access_token",""))')"
if [ -z "$TOKEN" ]; then
  echo "ERROR: failed to acquire access token"
  exit 1
fi

python - <<'PY'
import json, os
payload = {
  "lookup": os.environ.get("SESSION", "tool-use-ksession"),
  "commands": [
    {
      "insert": {
        "object": {
          "com.dotcomrow.rules.tooluse.DbTestRequest": {
            "message": os.environ.get("MESSAGE", "db-test")
          }
        },
        "out-identifier": "request",
      }
    },
    { "fire-all-rules": {} },
    {
      "get-objects": {
        "out-identifier": "results",
        "object-filter": "com.dotcomrow.rules.tooluse.DbTestResult",
      }
    }
  ]
}
with open("/tmp/tool-use-policy-dbtest.json", "w") as handle:
  json.dump(payload, handle)
PY

code="$(curl -sS -D /tmp/resp.hdrs -o /tmp/resp.body -w "%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d @/tmp/tool-use-policy-dbtest.json \
  "${GRAVITEE_URL%/}/kie-server/services/rest/server/containers/instances/${CONTAINER_ID}")"

echo "HTTP=${code}"
if [ -s /tmp/resp.body ]; then
  echo "--- response body ---"
  cat /tmp/resp.body
fi
EOF

