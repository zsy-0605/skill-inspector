# Fixed semantic extraction prompt

Statically inspect the Agent Skill in the current directory. Never execute or import target code.

Extract external local-environment requirements only: language runtimes, non-baseline third-party CLI commands, environment variables, fixed external files/directories, and operating-system constraints. Exclude user task inputs/outputs, paths supplied at invocation time, files bundled inside this Skill, language packages/libraries, shell builtins, and ordinary POSIX/coreutils such as `bash`, `sh`, `mkdir`, `rm`, `mv`, `cp`, `grep`, `sed`, `cut`, `find`, `head`, `wc`, `tr`, `sleep`, `tar`, and `basename` unless the Skill explicitly declares a nonstandard version.

A tool used only by an alternative path is `CONDITIONAL`, not `REQUIRED`. `REQUIRED` means every supported execution path needs it; `OPTIONAL` means absence only removes an enhancement. Canonicalize runtime and command names to lowercase. For each item set `required` to a version constraint or `present`, `source` to `INFERRED`, include exact relative `file:line` evidence, confidence, a short matched excerpt, and `inferenceRule` as `agent-semantic-extraction`.

Return only JSON conforming to `benchmark/semantic-extraction.schema.json`.
