# Skill Inspector

**Preflight compatibility inspection for Agent Skills**

Your agent can read a Skill. That doesn't mean it can run it.

Skill Inspector is an **Agent Skill** that performs a read-only compatibility check before another Skill is installed, activated, executed, debugged, or migrated. It combines Agent reasoning with deterministic environment verification so dependencies are found before a workflow fails halfway through.

```text
User: “Can I run this Skill?”
                 │
                 v
       Agent + Skill Inspector
       ├── understands SKILL.md
       ├── discovers dependencies
       └── preserves evidence
                 │
                 ▼
       Structured requirements JSON
       ├── source: DECLARED / INFERRED
       └── necessity: REQUIRED / OPTIONAL / CONDITIONAL
                 │
                 v
        Java deterministic checker
       ├── runtimes and versions
       ├── commands and environment
       └── files, directories, OS
                 │
                 v
        READY / WARNING / NOT READY
```

```text
$skill-inspector: Check whether ./third-party-skill can run here.

INFERRED command: pdftotext
Evidence: scripts/convert.sh:14
Matched: pdftotext "$INPUT" "$OUTPUT"
Environment: NOT FOUND
Readiness: WARNING
```

## Why a Skill and a Java program?

```text
Target Skill ──> Agent semantic extraction ──> requirements JSON
                                                    │
                           Java static discovery ───┤
                                                    v
                                      deterministic verification
                                                    │
                                                    v
                                         compatibility report
```

The Agent understands instructions and evidence. Java verifies the actual machine. `SKILL.md` defines when and how to inspect, how to distinguish `DECLARED` from `INFERRED`, and when to stop. The Java CLI safely handles version comparison, PATH lookup, environment presence, paths, OS detection, scoring, and stable JSON.

**Every inferred dependency should be explainable.** Inferred checks include an evidence location, a bounded and redacted matched line, a deterministic inference rule, and confidence. A clue is never presented as a declaration.

Inspection is not execution. Skill Inspector never runs target scripts, imports target code, installs dependencies, changes the target, or prints environment-variable values. Runtime checks execute only the inspector's fixed allowlist: `java --version`, `python3 --version` (falling back to `python --version`), and `node --version`.

## Requirements and build

- JDK 21+ is the only system build prerequisite; the included Maven Wrapper downloads Maven 3.9.16 on first use

```bash
./mvnw clean package
java -jar target/skill-inspector.jar --help
```

The shaded executable is `target/skill-inspector.jar`. The small `scripts/skill-inspector` launcher provides a convenient command after the build.

## Usage

Human-readable report:

```bash
java -jar target/skill-inspector.jar inspect ./examples/healthy-skill
```

Stable machine-readable report:

```bash
java -jar target/skill-inspector.jar inspect ./examples/missing-env-skill --json
```

Verify semantic requirements extracted from prose by an Agent:

```bash
java -jar target/skill-inspector.jar verify ./third-party-skill \
  --requirements requirements.json --json
```

The handoff schema is [`references/semantic-requirements.schema.json`](references/semantic-requirements.schema.json). `source` describes how a requirement was found; `necessity` independently records whether it is `REQUIRED`, `OPTIONAL`, or `CONDITIONAL`. A missing required semantic dependency can therefore block readiness without being misrepresented as a declaration.

Exit codes are `0` for READY/WARNING, `2` for compatibility FAIL, and `1` for invalid input or an inspection error. Callers must also inspect `status`/`readiness`; WARNING is not proof of readiness.

Declare compatibility in a target Skill's existing frontmatter:

```yaml
compatibility:
  runtimes:
    java: ">=21"
    python: ">=3.11"
  commands: [git, pdftotext]
  env: [OPENAI_API_KEY]
  os: [linux, macos]
  files: [./config.json]
  directories: [./scripts]
```

The extension is optional, human-readable, and safe for runtimes that ignore unknown fields. See [the V0.1 specification](references/compatibility-spec.md) for object forms, optional dependencies, scoring, and the JSON contract.

## Tests and evaluations

```bash
./mvnw test
./scripts/run-evals.sh
```

JUnit covers deterministic checkers and parsing. The Eval runner builds the project and exercises cross-platform synthetic cases for READY, WARNING, missing command/env/file, impossible runtime, OS mismatch, inferred dependency provenance, and the no-execution invariant. Trigger prompts and baseline methodology live in [`evals/`](evals/README.md).

### Controlled real-world benchmark

V0.1.1 evaluates 30 unmodified public Agent Skills pinned across six GitHub repositories. The controlled experiment locks the model (`gpt-5.6-sol`), prompts, target commits, environment, and three runs per Skill for both conditions: 180 model trials total.

```bash
python3 scripts/run-controlled-benchmark.py \
  --runs 3 --java /absolute/path/to/jdk-21/bin/java
```

| Method | Dependency recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | False ready |
|---|---:|---:|---:|---:|---:|---:|
| Agent only | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 21.4% |
| **Agent + Skill Inspector** | **74.0%** | **91.9%** | **100.0%** | **77.8%** | **100.0%** | **1.2%** |

The hybrid method also reduced false blocks from 50.0% to 33.3%, but did not eliminate them. These are pooled pilot results against AI-assisted reviewed annotations; **human signoff is still pending**, so they are evidence, not a final ecosystem-wide claim. Within this benchmark and Skill Inspector's machine-readable dependency definition, none of the 30 pinned Skills declared a structured runtime contract in the supported schema.

See [the benchmark protocol](benchmark/README.md), [pinned dataset](benchmark/dataset.json), [controlled environment](benchmark/environment.json), and [full comparison](benchmark/results/controlled-2026-08-21.md).

## V0.1 scope

Supported:

- Java, Python, and Node presence/version constraints
- Portable command discovery using PATH/PATHEXT, without assuming `which`
- Environment-variable presence with mandatory redaction
- Required files and directories, relative to the target Skill
- Windows, Linux, and macOS declarations
- YAML frontmatter parsing and conservative high-confidence inference from script extensions, shebangs, and simple shell command positions
- Explainable inference with evidence location, matched text, rule, confidence, redaction, and symbolic-link exclusion
- Structured Agent-to-Java semantic handoff with independent source and necessity dimensions
- DECLARED/INFERRED provenance, READY/WARNING/NOT READY, transparent scores, human and JSON output

Not supported:

- MCP, Agent Tool, network, permission, package-manager, or Skill-to-Skill capability checks
- General natural-language dependency extraction in Java; the Agent handles semantic interpretation
- Automatic installation, remediation, or target modification
- Malware, prompt-injection, vulnerability, virus, or supply-chain scanning
- Proving that a compatible Skill is safe or functionally correct

## Design notes

The package structure separates `parse`, `model`, `check`, `core`, `report`, and `cli`. Checkers share a small `EnvironmentProbe` boundary, keeping OS/process access replaceable in unit tests without a dependency-injection framework. The score is explanatory rather than authoritative: any `REQUIRED` failure produces NOT READY, while optional and conditional failures remain warnings.

## Roadmap

- **V0.1:** Local compatibility inspection — complete.
- **V0.1.1:** Minimal semantic handoff, 30 pinned samples, three-run controlled benchmark, and AI-assisted ground truth — implemented; independent human signoff pending.
- **V0.2:** Richer semantic contracts and package/capability verification.
- **V0.3:** MCP and Agent Tool runtime adapters.
- **V0.4:** Skill-to-Skill dependencies.

Longer-term possibilities include a capability graph, migration assistance, Skill CI/registry integration, and trace-based evolution. They intentionally remain outside the current scope.
