# Agent + Skill Inspector benchmark prompt

Use `$skill-inspector` to inspect the Agent Skill at `{{TARGET_SKILL}}` against the described environment.

Follow its read-only workflow. Read `SKILL.md` and relevant static evidence, then use the deterministic Java report. Never execute target code, import its modules, install dependencies, or modify the target. Keep Java findings and additional Agent semantic findings distinguishable.

Identify requirements in this evaluation scope only: runtime, command, environmentVariable, file, directory, and operatingSystem. Preserve relative file-and-line evidence and the Java check source when available. Package/library requirements may be included with `inScope: false`.

Return only JSON conforming to `benchmark/predictions.schema.json`. Use method `AGENT_WITH_INSPECTOR`. WARNING and UNKNOWN are not proof of readiness.
