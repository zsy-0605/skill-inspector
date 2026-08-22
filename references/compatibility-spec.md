# Compatibility metadata specification — V0.1

## Purpose

`compatibility` is an optional, backward-compatible mapping in a Skill's existing YAML frontmatter. Runtimes that do not know this extension can ignore it. Paths are resolved from the target Skill directory.

```yaml
---
name: pdf-analysis
description: Analyze PDF documents.
compatibility:
  runtimes:
    java: ">=21"
    python:
      version: ">=3.11"
      optional: false
  commands:
    - pdftotext
    - name: docker
      optional: true
  env:
    - OPENAI_API_KEY
  os: [linux, macos]
  files:
    - ./config.json
  directories:
    - ./scripts
---
```

## Fields

| Field | Shape | Meaning |
|---|---|---|
| `runtimes` | map | `java`, `python`, or `node` to a version constraint or `{version, optional}` |
| `commands` | list | Command name string or `{name, optional}` |
| `env` | list | Variable name string or `{name, optional}`; values are never reported |
| `os` | list | Any of `windows`, `linux`, `macos` |
| `supportedOs` | list | Backward-compatible alias for `os` |
| `files` | list | Relative/absolute path string or `{path, optional}` |
| `directories` | list | Relative/absolute path string or `{path, optional}` |

Supported version constraints are `>`, `>=`, `<`, `<=`, `=`, exact versions, `*`, and one trailing wildcard such as `21.x`. `source` (`DECLARED` or `INFERRED`) describes provenance; `necessity` (`REQUIRED`, `OPTIONAL`, or `CONDITIONAL`) independently controls blocking behavior. Missing required dependencies are `FAIL`; missing optional or conditional dependencies are `WARNING`.

## Semantic handoff

Natural-language requirements discovered by an Agent can be verified without pretending Java extracted the prose:

```bash
java -jar target/skill-inspector.jar verify ./target-skill \
  --requirements requirements.json --json
```

The input must conform to [`semantic-requirements.schema.json`](semantic-requirements.schema.json), is limited to 1 MiB and 1,000 entries, and must be a regular non-symbolic-link file. Semantic entries must use `source: INFERRED`, include evidence, confidence, and necessity, and may include bounded matched text. Java merges duplicate findings by preferring declarations and then stronger necessity/confidence.

## Stable result contract

JSON reports use `schemaVersion: "1.0"` and contain `skill`, an absolute normalized string `target`, `status`, `score`, `readiness`, `checks`, and `issues`. Each check contains `type` (`runtime`, `command`, `environmentVariable`, `file`, `directory`, or `operatingSystem`), `name`, `required`, `actual`, `status`, `source`, `necessity`, optional `confidence`, `evidence`, `matched`, `inferenceRule`, and `message`. `matched` is limited to 240 characters and redacts likely token, secret, password, and API-key assignments. Additive fields may appear in compatible V0.1 releases; consumers must ignore unknown fields.

Every inferred dependency must be explainable. `evidence` identifies the file and line when available, `matched` shows the bounded static text that triggered inference, and `inferenceRule` names the deterministic rule. Symbolic links under `scripts/` are never inspected.

The score is transparent: PASS=100 points, WARNING=50, FAIL/UNKNOWN=0, divided by the number of checks and rounded. An empty inspection scores 0 and is WARNING. A single required FAIL forces overall FAIL regardless of score.
