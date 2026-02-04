#!/usr/bin/env python3
"""Generate settings documentation and full config templates."""
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
IMPORT_JOB = ROOT / "manifests/workbench-import-job.yaml"
IMPORT_CONFIG = ROOT / "manifests/workbench-import-config.yaml"
REGISTER_JOB = ROOT / "manifests/register-container-job.yaml"
REGISTER_CONFIG = ROOT / "manifests/register-container-config.yaml"

DOCS_DIR = ROOT / "docs"
DOCS_PATH = DOCS_DIR / "settings.md"
IMPORT_FULL = ROOT / "manifests/workbench-import-config.full.yaml"
REGISTER_FULL = ROOT / "manifests/register-container-config.full.yaml"

IMPORT_SETTINGS = [
    {
        "key": "WORKBENCH_BASE",
        "required": True,
        "desc": "Base URL for Workbench (business-central).",
    },
    {
        "key": "SPACE_NAME",
        "required": True,
        "desc": "Workbench space name to create/use.",
    },
    {
        "key": "SPACE_DESC",
        "required": True,
        "desc": "Workbench space description.",
    },
    {
        "key": "DEFAULT_GROUP_ID",
        "required": True,
        "desc": "Default groupId for projects created in the space.",
    },
    {
        "key": "REPO_NAME",
        "required": True,
        "desc": "Workbench project/repo name (Niogit repo name).",
    },
    {
        "key": "REPO_URL",
        "required": True,
        "desc": "Git URL used to clone the project into Workbench.",
    },
    {
        "key": "REPO_BRANCH",
        "required": False,
        "desc": "Preferred branch to set as HEAD (falls back to prod/main/master).",
    },
    {
        "key": "REPO_VERSION",
        "required": False,
        "desc": "Version to stamp into pom.xml after import. If empty or 'auto', derived from the repo.",
    },
    {
        "key": "VERSION_PREFIX",
        "required": False,
        "desc": "Prefix used when deriving a version from git history (e.g. 1.0.). Leave empty to infer from pom.",
    },
    {
        "key": "SPACE_WAIT_SECONDS",
        "required": False,
        "desc": "Max seconds to wait for async space creation to complete.",
    },
    {
        "key": "SPACE_OWNER",
        "required": False,
        "desc": "Owner for the space. Leave empty to auto-pick controller user or service account.",
    },
    {
        "key": "HOLD_POD",
        "required": False,
        "desc": "If true, keep the import pod running on failure for debugging.",
    },
    {
        "key": "DEBUG_REST",
        "required": False,
        "desc": "If true, log REST responses for Workbench API calls.",
    },
]

REGISTER_SETTINGS = [
    {
        "key": "WORKBENCH_CONTROLLER_URL",
        "required": True,
        "desc": "Workbench controller REST base URL.",
    },
    {
        "key": "REPO_URL",
        "required": False,
        "desc": "Git URL used to resolve the container version when VERSION is empty/auto.",
    },
    {
        "key": "REPO_BRANCH",
        "required": False,
        "desc": "Preferred branch to resolve the container version from (falls back to prod/main/master).",
    },
    {
        "key": "KIE_SERVER_ID",
        "required": False,
        "desc": "KIE server id to register with (required if KIE_SERVER_IDS is empty).",
    },
    {
        "key": "KIE_SERVER_IDS",
        "required": False,
        "desc": "Comma-separated list of KIE server ids to register with.",
    },
    {
        "key": "KIE_SERVER_ID_PREFIX",
        "required": False,
        "desc": "Prefix used when selecting auto KIE server ids.",
    },
    {
        "key": "KIE_SERVER_MIN_COUNT",
        "required": False,
        "desc": "Minimum number of KIE servers that must be available for auto selection.",
    },
    {
        "key": "CONTAINER_ID",
        "required": True,
        "desc": "Container id to register on the KIE server.",
    },
    {
        "key": "CONTAINER_NAME",
        "required": False,
        "desc": "Container display name (defaults to CONTAINER_ID if empty).",
    },
    {
        "key": "GROUP_ID",
        "required": True,
        "desc": "Maven groupId for the KJAR.",
    },
    {
        "key": "ARTIFACT_ID",
        "required": True,
        "desc": "Maven artifactId for the KJAR.",
    },
    {
        "key": "VERSION",
        "required": False,
        "desc": "KJAR version to register in the container spec. If empty or 'auto', derived from the repo.",
    },
    {
        "key": "VERSION_PREFIX",
        "required": False,
        "desc": "Prefix used when deriving a version from git history (e.g. 1.0.). Leave empty to infer from pom.",
    },
    {
        "key": "CONTAINER_STATUS",
        "required": False,
        "desc": "Container status after registration (e.g., STARTED or STOPPED).",
    },
    {
        "key": "TOKEN_REFRESH_SKEW",
        "required": False,
        "desc": "Seconds to subtract from token expiry before refreshing.",
    },
    {
        "key": "LOG_HTTP",
        "required": False,
        "desc": "If true, log HTTP interactions with the Workbench controller.",
    },
    {
        "key": "LOG_HTTP_ONLY_ERRORS",
        "required": False,
        "desc": "If true, only log non-2xx/3xx HTTP responses.",
    },
    {
        "key": "LOG_HTTP_BODY_MAX",
        "required": False,
        "desc": "Max bytes of HTTP body to log.",
    },
    {
        "key": "CONTROLLER_WAIT_SECONDS",
        "required": False,
        "desc": "Max seconds to wait for Workbench controller availability.",
    },
    {
        "key": "CONTROLLER_WAIT_INTERVAL",
        "required": False,
        "desc": "Seconds between controller availability checks.",
    },
    {
        "key": "KIE_SERVER_WAIT_SECONDS",
        "required": False,
        "desc": "Max seconds to wait for KIE server availability.",
    },
    {
        "key": "KIE_SERVER_WAIT_INTERVAL",
        "required": False,
        "desc": "Seconds between KIE server availability checks.",
    },
]


def parse_config_map(path: Path) -> dict[str, str]:
    text = path.read_text().splitlines()
    data = {}
    in_data = False
    data_indent = 0
    for line in text:
        if not in_data:
            if re.match(r"^\s*data:\s*$", line):
                in_data = True
                data_indent = len(line) - len(line.lstrip(" "))
            continue
        if not line.strip():
            continue
        if line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent <= data_indent:
            break
        match = re.match(r"^\s*([A-Za-z0-9_]+):\s*(.*)$", line)
        if not match:
            continue
        key, raw = match.group(1), match.group(2).strip()
        if raw.startswith('"') and raw.endswith('"'):
            raw = raw[1:-1]
        elif raw.startswith("'") and raw.endswith("'"):
            raw = raw[1:-1]
        data[key] = raw
    return data


def parse_job_defaults(path: Path) -> dict[str, str]:
    text = path.read_text()
    pattern = re.compile(r"^\s*([A-Z][A-Z0-9_]+)=\"\$\{\1:-([^}]*)\}\"", re.MULTILINE)
    return {m.group(1): m.group(2) for m in pattern.finditer(text)}


def parse_env_defaults(path: Path) -> dict[str, str]:
    text = path.read_text()
    envs = {}
    for match in re.finditer(r"-\s*name:\s*([A-Z0-9_]+)\s*\n\s*value:\s*\"([^\"]*)\"", text):
        envs[match.group(1)] = match.group(2)
    return envs


def resolve_default(
    key: str,
    config_defaults: dict[str, str],
    env_defaults: dict[str, str],
    job_defaults: dict[str, str],
) -> tuple[str, str]:
    if key in config_defaults:
        return config_defaults[key], "config"
    if key in env_defaults:
        return env_defaults[key], "env"
    if key in job_defaults:
        return job_defaults[key], "job"
    return "", "none"


def yaml_quote(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', "\\\"")
    return f'"{escaped}"'


def write_full_template(
    out_path: Path,
    base_config: Path,
    settings: list[dict[str, object]],
    config_defaults: dict[str, str],
    env_defaults: dict[str, str],
    job_defaults: dict[str, str],
) -> None:
    lines = [
        "# Generated by scripts/generate-settings-doc.py",
        "# This is a full reference template; it is not included in kustomization.yaml.",
        "",
    ]
    header_lines = []
    for line in base_config.read_text().splitlines():
        header_lines.append(line)
        if line.strip() == "data:":
            break
    if not any(line.strip() == "data:" for line in header_lines):
        header_lines.append("data:")
    lines.extend(header_lines)

    for entry in settings:
        key = str(entry["key"])
        required = bool(entry["required"])
        desc = str(entry["desc"])
        default, source = resolve_default(key, config_defaults, env_defaults, job_defaults)
        value = default
        if source == "job" and "$" in default:
            # Keep dynamic defaults empty so the script can resolve them.
            value = ""
        req_label = "Required" if required else "Optional"
        if default == "":
            default_note = "empty"
        else:
            default_note = yaml_quote(default)
        lines.append(f"  # {req_label}. {desc}")
        lines.append(f"  # Default: {default_note} (source: {source})")
        lines.append(f"  {key}: {yaml_quote(value)}")

    out_path.write_text("\n".join(lines) + "\n")


def write_docs(
    out_path: Path,
    import_rows: list[dict[str, str]],
    register_rows: list[dict[str, str]],
) -> None:
    lines = []
    lines.append("# Settings")
    lines.append("")
    lines.append("Generated by `scripts/generate-settings-doc.py`.")
    lines.append("")
    lines.append("Defaults are resolved in this order:")
    lines.append("1. Value in the ConfigMap.")
    lines.append("2. Value set in the job manifest `env:` list.")
    lines.append("3. Default value in the job script.")
    lines.append("")
    lines.append("Version resolution (when VERSION/REPO_VERSION is empty or 'auto'):")
    lines.append("- If the pom has a literal `<version>`, use it.")
    lines.append("- Else if `<revision>` exists and is not a `-SNAPSHOT`, use it.")
    lines.append("- Else derive a prefix from `<revision>` (major.minor) or use `VERSION_PREFIX`.")
    lines.append("- Then append `git rev-list --count HEAD`.")
    lines.append("")
    lines.append("## Workbench Import")
    lines.append("")
    lines.append("| Setting | Required | Default | Source | Description |")
    lines.append("| --- | --- | --- | --- | --- |")
    for row in import_rows:
        lines.append(
            f"| `{row['key']}` | {row['required']} | `{row['default']}` | {row['source']} | {row['desc']} |"
        )
    lines.append("")
    lines.append("## Register Container")
    lines.append("")
    lines.append("| Setting | Required | Default | Source | Description |")
    lines.append("| --- | --- | --- | --- | --- |")
    for row in register_rows:
        lines.append(
            f"| `{row['key']}` | {row['required']} | `{row['default']}` | {row['source']} | {row['desc']} |"
        )
    out_path.write_text("\n".join(lines) + "\n")


def build_rows(
    settings: list[dict[str, object]],
    config_defaults: dict[str, str],
    env_defaults: dict[str, str],
    job_defaults: dict[str, str],
) -> list[dict[str, str]]:
    rows = []
    for entry in settings:
        key = str(entry["key"])
        required = "yes" if entry["required"] else "no"
        desc = str(entry["desc"])
        default, source = resolve_default(key, config_defaults, env_defaults, job_defaults)
        rows.append(
            {
                "key": key,
                "required": required,
                "default": default,
                "source": source,
                "desc": desc,
            }
        )
    return rows


def main() -> None:
    import_config_defaults = parse_config_map(IMPORT_CONFIG)
    import_job_defaults = parse_job_defaults(IMPORT_JOB)
    import_env_defaults = parse_env_defaults(IMPORT_JOB)

    register_config_defaults = parse_config_map(REGISTER_CONFIG)
    register_job_defaults = parse_job_defaults(REGISTER_JOB)
    register_env_defaults = parse_env_defaults(REGISTER_JOB)

    import_rows = build_rows(
        IMPORT_SETTINGS, import_config_defaults, import_env_defaults, import_job_defaults
    )
    register_rows = build_rows(
        REGISTER_SETTINGS,
        register_config_defaults,
        register_env_defaults,
        register_job_defaults,
    )

    DOCS_DIR.mkdir(parents=True, exist_ok=True)
    write_docs(DOCS_PATH, import_rows, register_rows)

    write_full_template(
        IMPORT_FULL,
        IMPORT_CONFIG,
        IMPORT_SETTINGS,
        import_config_defaults,
        import_env_defaults,
        import_job_defaults,
    )
    write_full_template(
        REGISTER_FULL,
        REGISTER_CONFIG,
        REGISTER_SETTINGS,
        register_config_defaults,
        register_env_defaults,
        register_job_defaults,
    )


if __name__ == "__main__":
    main()
