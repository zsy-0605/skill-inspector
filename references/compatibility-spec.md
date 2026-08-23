# Compatibility metadata specification — V0.4

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
  packages:
    - ecosystem: python
      name: pdfplumber
      version: ">=0.11"
      necessity: required
    - ecosystem: npm
      name: playwright
      version: "^1.50"
    - ecosystem: maven
      name: com.fasterxml.jackson.core:jackson-databind
      version: "[2.18,3.0)"
  capabilities:
    - capabilityKind: mcpServer
      name: openaiDeveloperDocs
      necessity: required
    - capabilityKind: tool
      name: hf_jobs
      necessity: conditional
  skills:
    - namespace: acme
      name: data-extractor
      version: ">=1.2"
      necessity: required
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
| `packages` | list | `{ecosystem, name, version?, necessity?}` for `python`, `npm`, or `maven` |
| `capabilities` | list | `{capabilityKind, name, necessity?}` where kind is `mcpServer`, `tool`, or `capability` |
| `skills` | list | `{namespace?, name, version?, necessity?}` using an exact lowercase Skill identity |

Runtime constraints support `>`, `>=`, `<`, `<=`, `=`, exact versions, `*`, and one trailing wildcard such as `21.x`. Package constraints intentionally cover a conservative subset: Python comparison lists and compatible releases, common npm exact/comparison/wildcard/caret/tilde/range expressions, and exact or single-interval Maven versions. Unsupported expressions produce `UNKNOWN`; they are never guessed. `source` (`DECLARED` or `INFERRED`) describes provenance; `necessity` (`REQUIRED`, `OPTIONAL`, or `CONDITIONAL`) independently controls blocking behavior. Missing required dependencies are `FAIL`; missing optional or conditional dependencies are `WARNING`.

Root-level `requirements.txt`, `pyproject.toml`, `package.json`, and `pom.xml` entries are parsed as declarations. npm `optionalDependencies` are optional; `peerDependencies` and `devDependencies` are conditional rather than required runtime dependencies. Maven `test`, `provided`, and `system` scopes are conditional, and `<optional>true</optional>` is optional.

Package verification is local and read-only. Python checks distribution metadata under target/configured virtual environments and conventional local site-package roots; npm checks package manifests in the applicable local `node_modules` path; Maven checks artifacts already present in the configured or standard local repository. The inspector never imports a package, runs lifecycle scripts, invokes an installer, downloads an artifact, or queries a remote registry.

Capability names are exact and case-sensitive. Matching uses only the canonical name or aliases explicitly present in the Runtime Capability Snapshot; the Java core contains no platform aliases. The only supported capability constraint is `required: available`. Abstract `capability` entries require an explicit identifier and must not be invented from generic natural language.

Skill identities are exact lowercase `namespace/name` pairs or bare `name` values; a bare and namespaced identity never match implicitly. V0.4 has no Skill aliases. Skill versions support `*`, exact/`=`, `>`, `>=`, `<`, `<=`, and one-segment `1.x`/`1.*`. Unsupported constraints and unknown installed versions yield `UNKNOWN`.

## Semantic handoff

Natural-language requirements discovered by an Agent can be verified without pretending Java extracted the prose:

```bash
java -jar target/skill-inspector.jar verify ./target-skill \
  --requirements requirements.json --json
```

The input must conform to [`semantic-requirements.schema.json`](semantic-requirements.schema.json), is limited to 1 MiB and 1,000 entries, and must be a regular non-symbolic-link file. Semantic entries must use `source: INFERRED`, include evidence, confidence, and necessity, and may include bounded matched text. Package entries additionally require `ecosystem`; Skill entries accept an optional `namespace`; both accept `version` (or the backward-compatible `required` alias). Evidence can use the legacy file string or `{file, matched, inferenceRule}`. Java merges duplicate findings by preferring declarations and then stronger necessity/confidence. Handoff 1.0 remains accepted for V0.2 types, 1.1 adds capability requirements, and 1.2 adds Skill requirements while continuing to accept all older types.

## Runtime Capability Snapshot

Capability checks use a separate, platform-neutral input:

```bash
java -jar target/skill-inspector.jar verify ./target-skill \
  --requirements requirements.json \
  --capabilities runtime-capabilities.json --json
```

The Snapshot must conform to [`runtime-capabilities.schema.json`](runtime-capabilities.schema.json). It is a regular non-symbolic-link JSON file, at most 1 MiB with at most 1,000 entries. It may identify the generic runtime and contain canonical capability names, explicit aliases, availability, source, and per-kind inventory coverage. It must not contain commands, launch arguments, endpoints, tokens, credentials, or private Agent configuration.

| Snapshot observation | Required check |
|---|---|
| `AVAILABLE` | `PASS` |
| `CONFIGURED` | `UNKNOWN` |
| `UNAVAILABLE` | `FAIL` |
| Not listed under `COMPLETE` coverage | `FAIL` |
| Not listed under `PARTIAL` coverage | `UNKNOWN` |
| No Snapshot | `UNKNOWN` |

An optional or conditional failure is downgraded to `WARNING`. Duplicate names or name/alias ambiguity within one capability kind makes the Snapshot invalid. Snapshot generation may use only a capability inventory already exposed in the current session or an explicit static inventory. Inspection never starts a server, sends MCP requests, calls or enumerates tools, checks authentication/network health, or changes configuration.

## Skill Inventory and read-only graph resolution

Skill dependency checks use a separate platform-neutral inventory:

```bash
java -jar target/skill-inspector.jar inspect ./target-skill \
  --skills skill-inventory.json --json
```

The inventory must conform to [`skill-inventory.schema.json`](skill-inventory.schema.json): a regular non-symbolic-link JSON file no larger than 1 MiB, with at most 1,000 Skill entries and 5,000 dependency edges. Global `COMPLETE` coverage means an absent identity is missing; under `PARTIAL`, absence is `UNKNOWN`. Each available entry separately declares whether its dependency list is `COMPLETE` or `PARTIAL`. A partial child graph always adds an `UNKNOWN` check and cannot produce `READY`.

Resolution follows only edges already present in the supplied inventory, to depth 64. It reports the exact dependency path, effective necessity, transitive version result, and cycles. An all-required cycle is `FAIL`; a cycle reached through an optional or conditional edge is `WARNING`; a graph or depth limit is `UNKNOWN`. `CONFIGURED` and `UNKNOWN` availability remain `UNKNOWN`, and `LOCAL_SKILL_DIRECTORY` cannot by itself claim `AVAILABLE`.

Inspection does not discover arbitrary Skill directories, download or install a Skill, activate one, execute it, recursively inspect a child Skill's runtime/package requirements, contact a registry, or resolve capabilities supplied by another Skill. Here, resolution means only deterministic traversal of caller-supplied data: **Inspection ≠ Resolution ≠ Execution**.

## Stable result contract

JSON reports use `schemaVersion: "1.2"` and contain `skill`, an absolute normalized string `target`, `status`, `score`, `readiness`, `checks`, and `issues`. Each check contains `type`, `name`, `required`, `actual`, `status`, `source`, `necessity`, and the applicable package, capability, evidence, or Skill graph fields. Skill checks may include `skillNamespace`, `resolvedSkill`, `skillVersion`, `inventorySource`, `dependencyDepth`, `dependencyPath`, and `resolutionKind`. `matched` is limited to 240 characters and redacts likely token, secret, password, and API-key assignments. Consumers must ignore unknown additive fields.

Every inferred dependency must be explainable. `evidence` identifies the file and line when available, `matched` shows the bounded static text that triggered inference, and `inferenceRule` names the deterministic rule. Symbolic links under `scripts/` are never inspected.

The score is transparent: PASS=100 points, WARNING=50, FAIL/UNKNOWN=0, divided by the number of checks and rounded. An empty inspection scores 0 and is WARNING. A single required FAIL forces overall FAIL regardless of score.
