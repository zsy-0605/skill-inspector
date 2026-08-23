# Controlled benchmark comparison

Dataset: `v0.2.0-rc1-2026-08-22`
Environment: `linux-system-path-2026-08-21`
Ground truth: **human-reviewed**
Model trials: **180**

| Method | Model | Runs | Skills/run | Coverage | Recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | Blocker recall | False ready | Missed warning | False block |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AGENT_ONLY | gpt-5.6-sol | 3 | 30 | 100.0% | 47.5% | 79.6% | 66.7% | 62.2% | 89.7% | 91.7% | 0.0% | 29.4% | N/A |
| AGENT_WITH_INSPECTOR | gpt-5.6-sol | 3 | 30 | 100.0% | 73.5% | 91.3% | 100.0% | 76.7% | 100.0% | 100.0% | 0.0% | 7.8% | N/A |

## Package metrics

| Method | Package N | Package recall | Package precision | Required package recall | Package false ready |
|---|---:|---:|---:|---:|---:|
| AGENT_ONLY | 147 | 44.4% | 83.8% | 100.0% | 0.0% |
| AGENT_WITH_INSPECTOR | 147 | 75.7% | 94.1% | 100.0% | 0.0% |

## V0.1.1 to V0.2 comparison

| Method | Metric | V0.1.1 | V0.2 | Change |
|---|---|---:|---:|---:|
| AGENT_ONLY | False ready | 0.0% | 0.0% | 0.0 pp |
| AGENT_ONLY | Missed warning | 33.3% | 29.4% | -3.9 pp |
| AGENT_ONLY | Required recall | 93.1% | 66.7% | -26.4 pp |
| AGENT_ONLY | Blocker recall | 97.2% | 91.7% | -5.5 pp |
| AGENT_ONLY | Diagnosis completeness | 96.7% | 89.7% | -7.0 pp |
| AGENT_WITH_INSPECTOR | False ready | 0.0% | 0.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Missed warning | 1.9% | 7.8% | +5.9 pp |
| AGENT_WITH_INSPECTOR | Required recall | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Blocker recall | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Diagnosis completeness | 100.0% | 100.0% | 0.0 pp |
| AGENT_WITH_INSPECTOR | Package recall | N/A | 75.7% | new |

The same 30 pinned Skills, model, three-run protocol, and base Linux environment were retained, but the evaluation scope and extraction prompts were expanded for packages. V0.2 adds 147 in-scope package labels, four package blockers, and changes three Skills' readiness labels, so cross-version percentages are not directly comparable as a regression test. Strict false ready remained 0% in both versions. The previously reported 1.2% and 4.4% values had combined WARNING -> READY with NOT_READY -> READY; under the corrected definitions they are missed-warning rates of 1.9% and 7.8%. Package false ready was 0% for both V0.2 conditions; package discovery coverage is the demonstrated package-level gain.

Metrics are pooled across repeated runs. `False ready` means NOT READY predicted as READY; `Missed warning` means WARNING predicted as READY. `Diagnosis completeness` is the share of NOT READY cases for which every true blocking dependency was reported.

## Interpretation

The hybrid method improved dependency coverage and reduced missed warnings relative to Agent only. No NOT_READY case was predicted READY in either V0.2 condition. The corpus has no READY labels after package blockers are included, so V0.2 false block is N/A.

## Limitations

- Ground truth dependencies, evidence paths, necessity, and environment conclusions were human-reviewed.
- The corpus contains 30 pinned Skills from six repositories, one model, and one controlled Linux environment.
- Package metrics cover Python and npm dependencies in this corpus; Maven has N=0 and is reported without extrapolation.
- Raw model outputs are retained locally but not redistributed in the public repository.
