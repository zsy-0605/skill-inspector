# Ground-truth review prompt

Independently review the Agent Skill in the current directory and relevant static files. Never execute or import target code. Create a complete dependency annotation for the Skill's supported workflows.

For in-scope requirements, include language runtimes, non-baseline third-party CLI commands, environment variables, fixed external files/directories, operating-system constraints, Python/npm/Maven packages, and exactly named MCP servers, Agent tools, or capability IDs. Exclude user task inputs/outputs, invocation-time paths, bundled Skill files, platform frameworks, browser binaries, remote services, shell builtins, and ordinary POSIX/coreutils. For packages, set `ecosystem` and preserve an explicit version constraint or `*`; otherwise set ecosystem to `null`. For capability requirements, set `type` to `capability`, set `capabilityKind`, preserve exact name case, and set version to `available`; otherwise set capabilityKind to `null`. Do not invent an ID from generic tool prose, MCP-building instructions, or an `/mcp/` URL.

Use `REQUIRED` only when every supported execution path needs the dependency, `CONDITIONAL` for a particular operation or alternative path, and `OPTIONAL` when absence removes only an enhancement. Keep how the requirement was found (`source`) separate from necessity. Use canonical lowercase runtime/command names. Every item needs one exact relative `file:line` evidence location and a concise rationale. Do not infer transitive implementation dependencies unless the Skill explicitly exposes them as prerequisites.

Return only JSON conforming to `benchmark/ground-truth-review.schema.json`.
