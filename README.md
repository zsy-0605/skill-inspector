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
       └── discovers dependencies
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

## Why a Skill and a Java program?

```text
Target Skill ──> SKILL.md / Agent reasoning ──> dependency requirements
                                                    │
                                                    v
                                      Java deterministic checks
                                                    │
                                                    v
                                         compatibility report
```

The Agent understands instructions and evidence. Java verifies the actual machine. `SKILL.md` defines when and how to inspect, how to distinguish `DECLARED` from `INFERRED`, and when to stop. The Java CLI safely handles version comparison, PATH lookup, environment presence, paths, OS detection, scoring, and stable JSON.

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

## V0.1 scope

Supported:

- Java, Python, and Node presence/version constraints
- Portable command discovery using PATH/PATHEXT, without assuming `which`
- Environment-variable presence with mandatory redaction
- Required files and directories, relative to the target Skill
- Windows, Linux, and macOS declarations
- YAML frontmatter parsing and conservative high-confidence inference from script extensions, shebangs, and simple shell command positions
- DECLARED/INFERRED provenance, READY/WARNING/NOT READY, transparent scores, human and JSON output

Not supported:

- MCP, Agent Tool, network, permission, package-manager, or Skill-to-Skill capability checks
- General natural-language dependency extraction in Java; the Agent handles semantic interpretation
- Automatic installation, remediation, or target modification
- Malware, prompt-injection, vulnerability, virus, or supply-chain scanning
- Proving that a compatible Skill is safe or functionally correct

## Design notes

The package structure separates `parse`, `model`, `check`, `core`, `report`, and `cli`. Checkers share a small `EnvironmentProbe` boundary, keeping OS/process access replaceable in unit tests without a dependency-injection framework. The score is explanatory rather than authoritative: any declared required failure always produces NOT READY.

The next valuable capabilities are MCP/Agent Tool runtime adapters, a structured Agent-to-Java semantic dependency handoff, and CI evaluation against a broader corpus. Longer-term possibilities include a capability graph, Skill-to-Skill dependencies, migration assistance, Skill CI/registry integration, and trace-based evolution. They intentionally remain outside the current scope.
