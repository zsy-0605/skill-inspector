# Ground-truth review prompt

Independently review the Agent Skill in the current directory and relevant static files. Never execute or import target code. Create a complete dependency annotation for the Skill's supported workflows.

For in-scope requirements, include runtimes, non-baseline commands, environment variables, fixed files/directories, OS constraints, Python/npm/Maven packages, exact runtime capabilities, and exact Skill dependencies. Exclude invocation inputs, bundled files, platform frameworks, remote services, shell builtins/coreutils, generic “other Skills” prose, suggestions, examples, and build-a-Skill instructions. For packages set `ecosystem`; for capabilities set `capabilityKind`, exact case, and version `available`. For Skill dependencies set `type: skill`, exact lowercase `name`, optional exact lowercase `namespace`, and a version or `*`. Set namespace to null for non-Skill entries. Do not infer capability providers or alternatives.

Use `REQUIRED` only when every supported execution path needs the dependency, `CONDITIONAL` for a particular operation or alternative path, and `OPTIONAL` when absence removes only an enhancement. Keep how the requirement was found (`source`) separate from necessity. Use canonical lowercase runtime/command names. Every item needs one exact relative `file:line` evidence location and a concise rationale. Do not infer transitive implementation dependencies unless the Skill explicitly exposes them as prerequisites.

Return only JSON conforming to `benchmark/ground-truth-review.schema.json`.
