# drools-rule-app-tool-use-policy
Simple Drools KJAR that evaluates whether an AI model can use a tool based on
risk and context. The rules produce a ToolUseDecision with ALLOW, REVIEW, or DENY.

## Facts
- com.dotcomrow.rules.tooluse.ToolUseRequest
- com.dotcomrow.rules.tooluse.ToolUseDecision

## Build
```
mvn -q -DskipTests package
```

## Settings
Run the generator to list all available import/register settings and their defaults:

```
python scripts/generate-settings-doc.py
```

Outputs:
- `docs/settings.md`
- `manifests/workbench-import-config.full.yaml`
- `manifests/register-container-config.full.yaml`

Leaving `REPO_VERSION`/`VERSION` empty (or `auto`) resolves the version from `pom.xml` and git history.

## Project Placeholders
This repo includes placeholders for Workbench project settings:
- `src/main/resources/META-INF/kie-deployment-descriptor.xml` provides slots for environment entries,
  listeners, custom tasks (work item handlers), and globals.
- `src/main/resources/db/example.sql` is a minimal schema stub for app-owned data.

You can edit these from Workbench or in an external IDE.

## Yugabyte Database Example
`manifests/vault-yugabyte-tool-use-policy.yaml` provisions an app-owned Yugabyte DB using the same
pattern as the Drools system:
- Creates DB `rules_tool_use` (if missing)
- Creates DB role `tool_use_app` (static username)
- Configures Vault DB static role `yugabyte-db/static-roles/tool-use-policy-yb-app` (rotating password)
- Writes Vault policy `tool-use-policy-yb-app` for reading `yugabyte-db/static-creds/tool-use-policy-yb-app`
- Ensures the Vault Kubernetes auth role `drools` includes that policy (merged with existing policies)
- Creates schema/table: `tool_use.tool_use_audit`

No static DB password is stored in Kubernetes for this app.

Runtime overrides (optional, defaults match the cluster services):
- `TOOL_USE_POLICY_VAULT_ADDR` (or `VAULT_ADDR`)
- `TOOL_USE_POLICY_VAULT_ROLE` (defaults to `drools`)
- `TOOL_USE_POLICY_VAULT_STATIC_ROLE` (defaults to `tool-use-policy-yb-app`)
- `TOOL_USE_POLICY_DB_HOST` / `TOOL_USE_POLICY_DB_PORT`
- `TOOL_USE_POLICY_DB_NAME` (defaults to `rules_tool_use`)
- `TOOL_USE_POLICY_DB_SCHEMA` (defaults to `tool_use`)
- `TOOL_USE_POLICY_DB_SSLMODE` (defaults to `disable`)

## Publish to GitHub Packages
Update the owner/repo in pom.xml if needed, then configure Maven credentials:

`~/.m2/settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USER</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Deploy:

```
mvn deploy
```

CI publish: `.github/workflows/publish.yml` runs on pushes to `main` and `prod`.

## KIE Server call example
Container id: tool-use-policy
Session: tool-use-ksession

### Tool decision
```
POST /kie-server/services/rest/server/containers/tool-use-policy
Content-Type: application/json

{
  "lookup": "tool-use-ksession",
  "commands": [
    {
      "insert": {
        "object": {
          "com.dotcomrow.rules.tooluse.ToolUseRequest": {
            "tool": "refund",
            "userRole": "member",
            "riskScore": 72,
            "amount": 150.0
          }
        },
        "out-identifier": "request"
      }
    },
    { "fire-all-rules": {} },
    {
      "get-objects": {
        "out-identifier": "decisions",
        "object-filter": "com.dotcomrow.rules.tooluse.ToolUseDecision"
      }
    }
  ]
}
```

### DB test (Vault static creds + Yugabyte)
```
POST /kie-server/services/rest/server/containers/tool-use-policy
Content-Type: application/json

{
  "lookup": "tool-use-ksession",
  "commands": [
    {
      "insert": {
        "object": {
          "com.dotcomrow.rules.tooluse.DbTestRequest": {
            "message": "OK"
          }
        },
        "out-identifier": "dbRequest"
      }
    },
    { "fire-all-rules": {} },
    {
      "get-objects": {
        "out-identifier": "dbResults",
        "object-filter": "com.dotcomrow.rules.tooluse.DbTestResult"
      }
    }
  ]
}
```
