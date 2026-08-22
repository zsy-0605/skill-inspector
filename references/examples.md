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
