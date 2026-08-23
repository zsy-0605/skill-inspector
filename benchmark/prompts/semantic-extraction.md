# Fixed semantic extraction prompt

Statically inspect the Agent Skill in the current directory. Never execute or import target code.

Extract external requirements only: language runtimes, non-baseline third-party CLI commands, environment variables, fixed external files/directories, operating-system constraints, Python/npm/Maven packages, exactly named runtime capabilities, and exact Skill dependencies. Exclude user task inputs/outputs, invocation-time paths, bundled files, shell builtins, ordinary POSIX/coreutils, generic references to “other Skills”, suggestions, build-a-Skill instructions, and Skill names used only as examples. For packages set `ecosystem`; otherwise set it to `null`. For capabilities set `capabilityKind` and `required: available`; otherwise set capabilityKind to `null`. For Skill dependencies set `type: skill`, exact lowercase `name`, optional exact lowercase `namespace`, and a version constraint or `*`; set namespace to `null` for all other types. Do not infer capability providers or alternative providers.

Do not invent a capability ID from generic phrases such as “browse the web” or “use available tools”. Instructions for building an MCP server and URLs containing `/mcp/` are not existing-runtime requirements. If a concrete tool has a CLI or other explicit fallback, classify the tool as `CONDITIONAL`.

A dependency used only by an alternative path is `CONDITIONAL`, not `REQUIRED`. `REQUIRED` means every supported execution path needs it; `OPTIONAL` means absence only removes an enhancement. Canonicalize runtime, command, and package names. For each item set `required` to a version constraint, `*`, or `present`, `source` to `INFERRED`, include exact relative `file:line` evidence, confidence, a short matched excerpt, and `inferenceRule` as `agent-semantic-extraction`.

Return only JSON conforming to `benchmark/semantic-extraction.schema.json`.
