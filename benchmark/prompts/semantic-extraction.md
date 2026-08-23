# Fixed semantic extraction prompt

Statically inspect the Agent Skill in the current directory. Never execute or import target code.

Extract external local-environment requirements only: language runtimes, non-baseline third-party CLI commands, environment variables, fixed external files/directories, operating-system constraints, Python/npm/Maven packages, and exactly named MCP servers, Agent tools, or capability IDs required from the current runtime. Exclude user task inputs/outputs, paths supplied at invocation time, files bundled inside this Skill, shell builtins, and ordinary POSIX/coreutils such as `bash`, `sh`, `mkdir`, `rm`, `mv`, `cp`, `grep`, `sed`, `cut`, `find`, `head`, `wc`, `tr`, `sleep`, `tar`, and `basename` unless the Skill explicitly declares a nonstandard version. For every package set `ecosystem` to `python`, `npm`, or `maven`; set it to `null` for every non-package requirement. For every capability set `type` to `capability`, set `capabilityKind` to `mcpServer`, `tool`, or `capability`, and `required` to `available`; set `capabilityKind` to `null` for all other types. Do not classify platform frameworks, browser binaries, or remote services as packages.

Do not invent a capability ID from generic phrases such as “browse the web” or “use available tools”. Instructions for building an MCP server and URLs containing `/mcp/` are not existing-runtime requirements. If a concrete tool has a CLI or other explicit fallback, classify the tool as `CONDITIONAL`.

A dependency used only by an alternative path is `CONDITIONAL`, not `REQUIRED`. `REQUIRED` means every supported execution path needs it; `OPTIONAL` means absence only removes an enhancement. Canonicalize runtime, command, and package names. For each item set `required` to a version constraint, `*`, or `present`, `source` to `INFERRED`, include exact relative `file:line` evidence, confidence, a short matched excerpt, and `inferenceRule` as `agent-semantic-extraction`.

Return only JSON conforming to `benchmark/semantic-extraction.schema.json`.
