# Inspection report guidance

Prefer the Java inspector's JSON for Agent consumption. Summarize it without losing provenance:

```text
Skill: pdf-analysis
Readiness: NOT READY (72/100)

Blocking
- DECLARED command `pdftotext`: NOT FOUND
- DECLARED environment variable `PDF_API_KEY`: MISSING

Uncertainty
- INFERRED / CONDITIONAL command `docker` (MEDIUM, scripts/run.sh:14): not confirmed
  Matched: `docker build -t "$IMAGE" .`
  Rule: `shell-command-position`

Recommendation
Resolve declared blockers, then rerun the preflight inspection. Confirm inferred requirements with the Skill author when evidence is ambiguous.
```

Use `READY` only when all discovered checks pass. Preserve both `DECLARED` versus `INFERRED` and `REQUIRED` versus `OPTIONAL` versus `CONDITIONAL`. Do not print secret values or claim that passing compatibility checks proves the target is safe, correct, or trustworthy.

Minimal semantic handoff:

```json
{
  "schemaVersion": "1.0",
  "requirements": [{
    "type": "command",
    "name": "vercel",
    "necessity": "REQUIRED",
    "source": "INFERRED",
    "confidence": "HIGH",
    "evidence": "SKILL.md:18",
    "matched": "Requires the Vercel CLI.",
    "inferenceRule": "agent-semantic-extraction"
  }]
}
```

Inferred package handoff with structured evidence:

```json
{
  "schemaVersion": "1.0",
  "requirements": [{
    "type": "package",
    "ecosystem": "python",
    "name": "pdfplumber",
    "version": ">=0.11",
    "necessity": "REQUIRED",
    "source": "INFERRED",
    "confidence": "HIGH",
    "evidence": {
      "file": "SKILL.md:24",
      "matched": "Requires pdfplumber for table extraction.",
      "inferenceRule": "SEMANTIC_PACKAGE_REFERENCE"
    }
  }]
}
```

Java checks the corresponding local package metadata. It does not import the package, execute a lifecycle script, call a package manager, or query a registry. An unsupported constraint is `UNKNOWN`, never an assumed pass.

Capability handoff 1.1 with a platform-neutral Snapshot:

```json
{
  "schemaVersion": "1.1",
  "requirements": [{
    "type": "capability",
    "capabilityKind": "tool",
    "name": "search_docs",
    "required": "available",
    "necessity": "REQUIRED",
    "source": "INFERRED",
    "confidence": "HIGH",
    "evidence": {
      "file": "SKILL.md:38",
      "matched": "Always use the search_docs tool.",
      "inferenceRule": "SEMANTIC_TOOL_REFERENCE"
    }
  }]
}
```

```json
{
  "schemaVersion": "1.0",
  "runtime": {"name": "generic-agent-runtime"},
  "coverage": {"mcpServer": "COMPLETE", "tool": "COMPLETE", "capability": "PARTIAL"},
  "capabilities": [{
    "capabilityKind": "tool",
    "name": "mcp__docs__search",
    "aliases": ["search_docs"],
    "availability": "AVAILABLE",
    "source": "RUNTIME_INVENTORY"
  }]
}
```

Run:

```bash
java -jar target/skill-inspector.jar verify ./examples/capability-skill \
  --requirements ./examples/capability-skill/requirements.json \
  --capabilities ./examples/capability-skill/runtime-capabilities.json --json
```

The result reports `resolvedCapability: "mcp__docs__search"` for the explicit alias. `AVAILABLE` means only that the current runtime inventory advertises the tool; it does not prove permissions, authentication, parameter compatibility, network health, or successful execution.

## Skill dependency inventory

```json
{
  "schemaVersion": "1.0",
  "coverage": "COMPLETE",
  "skills": [{
    "identity": {"namespace": "acme", "name": "data-extractor"},
    "version": "1.4.0",
    "availability": "AVAILABLE",
    "source": "RUNTIME_INVENTORY",
    "dependencyCoverage": "COMPLETE",
    "dependencies": []
  }]
}
```

Run:

```bash
java -jar target/skill-inspector.jar inspect ./examples/skill-dependency-skill \
  --skills ./examples/skill-dependency-skill/skill-inventory.json --json
```

The check reports `dependencyPath`, `dependencyDepth`, the resolved identity/version, and inventory source. Only the supplied graph is traversed; no Skill is searched for, installed, activated, or executed.
