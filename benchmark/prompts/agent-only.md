# Agent-only benchmark prompt

Inspect the Agent Skill at `{{TARGET_SKILL}}` and decide whether it can run in the described environment.

Do not use Skill Inspector or its Java report. Read `SKILL.md` and only relevant static files. Never execute target code, import its modules, install dependencies, or modify the target.

Identify requirements in this evaluation scope only: runtime, command, environmentVariable, file, directory, and operatingSystem. Preserve evidence as a relative file path plus line number where possible. Package/library requirements may be included with `inScope: false`.

Return only JSON conforming to `benchmark/predictions.schema.json`. Use method `AGENT_ONLY`. Do not treat missing evidence as satisfied and do not invent current environment facts.
