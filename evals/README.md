# Skill Inspector evaluations

The regression suite has three layers:

1. JUnit checks deterministic version, command, environment, file, directory, OS, package, capability, Skill graph, parsing, scoring, and secret-redaction behavior.
2. `scripts/run-evals.sh` executes 41 synthetic Skills covering READY, WARNING, FAIL, and UNKNOWN, including 12 V0.4 Skill dependency cases.
3. `cases/skill-trigger-cases.yaml` is the fixed synthetic prompt set for trigger and diagnosis behavior.
4. [`benchmark/`](../benchmark/README.md) contains the 30-Skill, three-run controlled Agent-only versus Agent + Inspector experiment.

For the controlled experiment, give the same target Skills to the same fixed model three times per condition. The Agent-only condition reasons about the environment itself; the hybrid condition hands semantic requirements to Java. Record dependency recall, precision, required recall, exact classification accuracy, false-ready, false-block, blocking recall, and diagnosis completeness. Do not infer those metrics from the deterministic fixture suite.

Declared dependency recall and deterministic environment accuracy are gated at 100%. Static inference precision/recall targets are at least 90% on a separately labeled corpus. False-ready rate is the primary safety metric and should approach 0%.
