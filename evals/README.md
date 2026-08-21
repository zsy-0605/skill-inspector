# Skill Inspector evaluations

The regression suite has three layers:

1. JUnit checks deterministic version, command, environment, file, directory, OS, parsing, scoring, and secret-redaction behavior.
2. `scripts/run-evals.sh` executes synthetic Skills covering READY, WARNING, and FAIL, including a cross-platform generated OS mismatch.
3. `cases/skill-trigger-cases.yaml` is the fixed prompt set for comparing Agent-only and Agent + Skill Inspector trigger and diagnosis behavior.

For a baseline experiment, give the same target Skills to the same Agent/model twice: first with only the target `SKILL.md`, then with Skill Inspector available. Label actual requirements in advance and record dependency recall, precision, environment accuracy, false-ready rate, false-block rate, and diagnosis consistency. Do not infer those metrics from the deterministic fixture suite; they require repeated Agent trials.

Declared dependency recall and deterministic environment accuracy are gated at 100%. Static inference precision/recall targets are at least 90% on a separately labeled corpus. False-ready rate is the primary safety metric and should approach 0%.
