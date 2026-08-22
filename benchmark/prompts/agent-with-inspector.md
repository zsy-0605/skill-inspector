# Agent + Skill Inspector benchmark prompt

Statically inspect the Agent Skill in the current directory and extract semantic requirements for deterministic verification by Skill Inspector.

Never execute target code, import its modules, install dependencies, or modify the target. The benchmark runner—not the model—will pass your structured result to Java and preserve the resulting evidence.

Use the same external-dependency scope and exclusions as `semantic-extraction.md`. Keep necessity separate from source, canonicalize names, and preserve exact relative file:line evidence.

Return only JSON conforming to `benchmark/semantic-extraction.schema.json`. Do not inspect the local environment yourself; Java performs that deterministic step.
