# Controlled benchmark comparison

Dataset: `v0.1.1-pilot-2026-08-21`
Environment: `linux-system-path-2026-08-21`
Ground truth: **AI-assisted; human signoff pending**
Model trials: **180**

| Method | Model | Runs | Skills/run | Coverage | Recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | Blocker recall | False ready | False block |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AGENT_ONLY | gpt-5.6-sol | 3 | 30 | 100.0% | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 97.2% | 21.4% | 50.0% |
| AGENT_WITH_INSPECTOR | gpt-5.6-sol | 3 | 30 | 100.0% | 74.0% | 91.9% | 100.0% | 77.8% | 100.0% | 100.0% | 1.2% | 33.3% |

Metrics are pooled across repeated runs. `Diagnosis completeness` is the share of NOT READY cases for which every true blocking dependency was reported.

## Interpretation

The hybrid method improved dependency coverage and sharply reduced false-ready decisions by turning Agent-extracted requirements into deterministic environment checks. It did not eliminate false blocks; semantic over-classification can still send an incorrect required dependency to Java.

## Limitations

- Ground truth is AI-assisted with deterministic evidence/path validation; independent human signoff is pending.
- The corpus contains 30 pinned Skills from six repositories, one model, and one controlled Linux environment.
- Package/library dependencies are recorded but excluded from V0.1 scoring.
- Raw model outputs are retained locally but not redistributed in the public repository.
