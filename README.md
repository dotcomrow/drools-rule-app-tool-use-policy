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
            "amount": 150.0,
            "accountAgeDays": 120
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
