# Ground-truth review prompt

Independently review the Agent Skill in the current directory and relevant static files. Never execute or import target code. Create a complete dependency annotation for the Skill's supported workflows.

For in-scope requirements, include only language runtimes, non-baseline third-party CLI commands, environment variables, fixed external files/directories, and operating-system constraints. Exclude user task inputs/outputs, invocation-time paths, bundled Skill files, shell builtins, and ordinary POSIX/coreutils. Record language packages/libraries separately as `type: package` and `inScope: false` when they are genuinely needed.

Use `REQUIRED` only when every supported execution path needs the dependency, `CONDITIONAL` for a particular operation or alternative path, and `OPTIONAL` when absence removes only an enhancement. Keep how the requirement was found (`source`) separate from necessity. Use canonical lowercase runtime/command names. Every item needs one exact relative `file:line` evidence location and a concise rationale. Do not infer transitive implementation dependencies unless the Skill explicitly exposes them as prerequisites.

Return only JSON conforming to `benchmark/ground-truth-review.schema.json`.
