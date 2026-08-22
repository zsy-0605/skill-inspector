# Controlled benchmark comparison

Dataset: `v0.2.0-rc1-2026-08-22`
Environment: `linux-system-path-2026-08-21`
Ground truth: **human-reviewed**
Model trials: **180**

| Method | Model | Runs | Skills/run | Coverage | Recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | Blocker recall | False ready | False block |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AGENT_ONLY | gpt-5.6-sol | 3 | 30 | 100.0% | 46.7% | 78.2% | 61.9% | 62.2% | 89.7% | 91.7% | 16.7% | N/A |
| AGENT_WITH_INSPECTOR | gpt-5.6-sol | 3 | 30 | 100.0% | 73.5% | 91.3% | 100.0% | 76.7% | 100.0% | 100.0% | 4.4% | N/A |

## Package metrics

| Method | Package N | Package recall | Package precision | Required package recall | Package false ready |
|---|---:|---:|---:|---:|---:|
| AGENT_ONLY | 147 | 44.4% | 83.8% | 100.0% | 0.0% |
| AGENT_WITH_INSPECTOR | 147 | 75.7% | 94.1% | 100.0% | 0.0% |

## V0.1.1 to V0.2 comparison

| Method | Metric | V0.1.1 | V0.2 | Change |
|---|---|---:|---:|---:|
| AGENT_ONLY | False ready | 21.4% | 16.7% | -4.7 pp |
| AGENT_ONLY | Required recall | 93.1% | 61.9% | -31.2 pp |
| AGENT_ONLY | Blocker recall | 97.2% | 91.7% | -5.5 pp |
| AGENT_ONLY | Diagnosis completeness | 96.7% | 89.7% | -7.0 pp |
| AGENT_WITH_INSPECTOR | False ready | 1.2% | 4.4% | +3.2 pp |
| AGENT_WITH_INSPECTOR | Required recall | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Blocker recall | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Diagnosis completeness | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Package recall | N/A | 75.7% | new |

The same 30 pinned Skills, model, three-run protocol, and base Linux environment were retained. V0.2 adds 147 in-scope package labels and four package blockers, so the V0.1.1 and V0.2 percentages do not have identical denominators. The hybrid method retained perfect required/blocker recall and diagnosis completeness, but its overall false-ready rate increased by 3.2 percentage points rather than improving. Package false ready was 0% for both V0.2 conditions; package discovery coverage, not that metric, is the demonstrated package-level gain.

Metrics are pooled across repeated runs. `Diagnosis completeness` is the share of NOT READY cases for which every true blocking dependency was reported.

## Interpretation

The hybrid method improved dependency coverage and sharply reduced false-ready decisions by turning Agent-extracted requirements into deterministic environment checks. It did not eliminate false blocks; semantic over-classification can still send an incorrect required dependency to Java.

## Limitations

- Ground truth dependencies, evidence paths, necessity, and environment conclusions were human-reviewed.
- The corpus contains 30 pinned Skills from six repositories, one model, and one controlled Linux environment.
- Package metrics cover Python and npm dependencies in this corpus; Maven has N=0 and is reported without extrapolation.
- Raw model outputs are retained locally but not redistributed in the public repository.
