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
