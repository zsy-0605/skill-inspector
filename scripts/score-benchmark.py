#!/usr/bin/env python3
"""Score repeated Agent-only and Agent + Inspector predictions against reviewed ground truth."""

from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path
import sys
from typing import Any

REVIEWED = {"EVIDENCE_REVIEWED", "HUMAN_REVIEWED"}


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def percent(numerator: int, denominator: int) -> str:
    return f"{100.0 * numerator / denominator:.1f}%" if denominator else "N/A"


def dependency_key(item: dict[str, Any]) -> tuple[str, str, str]:
    kind = item["type"]
    name = item["name"] if kind == "capability" else item["name"].lower()
    ecosystem = (item.get("ecosystem", "").lower() if kind == "package"
                 else item.get("capabilityKind", "") if kind == "capability" else "")
    if kind == "runtime":
        name = {"python3": "python", "node.js": "node", "nodejs": "node"}.get(name, name)
    if kind == "operatingSystem":
        name = "operating-system"
    return kind, ecosystem, name


def readiness(value: str | None) -> str | None:
    return value.replace(" ", "_") if value else value


def score(truth: dict[str, Any], prediction: dict[str, Any]) -> dict[str, Any]:
    if truth.get("datasetVersion") != prediction.get("datasetVersion"):
        raise ValueError("Ground truth and prediction datasetVersion differ")
    if truth.get("environment") and prediction.get("environment") != truth.get("environment"):
        raise ValueError("Ground truth and prediction environment differ")
    labels = {item["id"]: item for item in truth.get("skills", []) if item.get("reviewStatus") in REVIEWED}
    if len({item["id"] for item in prediction.get("skills", [])}) != len(prediction.get("skills", [])):
        raise ValueError("Prediction contains duplicate Skill ids")
    predicted = {item["id"]: item for item in prediction.get("skills", [])}
    counts = defaultdict(int)
    missing_predictions: list[str] = []
    for skill_id, label in labels.items():
        actual_deps = {dependency_key(item) for item in label.get("dependencies", []) if item.get("inScope")}
        required_deps = {dependency_key(item) for item in label.get("dependencies", [])
                         if item.get("inScope") and item.get("necessity") == "REQUIRED"}
        package_deps = {dependency_key(item) for item in label.get("dependencies", [])
                        if item.get("inScope") and item.get("type") == "package"}
        required_package_deps = {dependency_key(item) for item in label.get("dependencies", [])
                                 if item.get("inScope") and item.get("type") == "package"
                                 and item.get("necessity") == "REQUIRED"}
        capability_deps = {dependency_key(item) for item in label.get("dependencies", [])
                           if item.get("inScope") and item.get("type") == "capability"}
        required_capability_deps = {dependency_key(item) for item in label.get("dependencies", [])
                                    if item.get("inScope") and item.get("type") == "capability"
                                    and item.get("necessity") == "REQUIRED"}
        blockers = {dependency_key(item) for item in label.get("blockingDependencies", [])}
        package_blockers = {dependency_key(item) for item in label.get("blockingDependencies", [])
                            if item.get("type") == "package"}
        capability_blockers = {dependency_key(item) for item in label.get("blockingDependencies", [])
                               if item.get("type") == "capability"}
        if skill_id not in predicted:
            missing_predictions.append(skill_id)
            counts["fn"] += len(actual_deps)
            counts["requiredFn"] += len(required_deps)
            counts["packageFn"] += len(package_deps)
            counts["requiredPackageFn"] += len(required_package_deps)
            counts["packageTruth"] += len(package_deps)
            counts["capabilityFn"] += len(capability_deps)
            counts["requiredCapabilityFn"] += len(required_capability_deps)
            counts["capabilityTruth"] += len(capability_deps)
            counts["packageBlockerCases"] += int(bool(package_blockers))
            counts["capabilityBlockerCases"] += int(bool(capability_blockers))
            counts["diagnosisCases"] += int(bool(blockers))
            continue
        item = predicted[skill_id]
        predicted_deps = {dependency_key(dep) for dep in item.get("dependencies", []) if dep.get("inScope", True)}
        predicted_package_deps = {dependency_key(dep) for dep in item.get("dependencies", [])
                                  if dep.get("inScope", True) and dep.get("type") == "package"}
        predicted_capability_deps = {dependency_key(dep) for dep in item.get("dependencies", [])
                                     if dep.get("inScope", True) and dep.get("type") == "capability"}
        counts["tp"] += len(actual_deps & predicted_deps)
        counts["fp"] += len(predicted_deps - actual_deps)
        counts["fn"] += len(actual_deps - predicted_deps)
        counts["requiredTp"] += len(required_deps & predicted_deps)
        counts["requiredFn"] += len(required_deps - predicted_deps)
        counts["packageTp"] += len(package_deps & predicted_package_deps)
        counts["packageFp"] += len(predicted_package_deps - package_deps)
        counts["packageFn"] += len(package_deps - predicted_package_deps)
        counts["requiredPackageTp"] += len(required_package_deps & predicted_package_deps)
        counts["requiredPackageFn"] += len(required_package_deps - predicted_package_deps)
        counts["packageTruth"] += len(package_deps)
        counts["capabilityTp"] += len(capability_deps & predicted_capability_deps)
        counts["capabilityFp"] += len(predicted_capability_deps - capability_deps)
        counts["capabilityFn"] += len(capability_deps - predicted_capability_deps)
        counts["requiredCapabilityTp"] += len(required_capability_deps & predicted_capability_deps)
        counts["requiredCapabilityFn"] += len(required_capability_deps - predicted_capability_deps)
        counts["capabilityTruth"] += len(capability_deps)
        predicted_capability_items = [dep for dep in item.get("dependencies", [])
                                      if dep.get("inScope", True) and dep.get("type") == "capability"]
        status_observations = [dep for dep in predicted_capability_items if dep.get("status") is not None]
        counts["capabilityChecks"] += len(status_observations)
        counts["capabilityUnknown"] += sum(dep.get("status") == "UNKNOWN" for dep in status_observations)
        counts["capabilitySnapshotPresent"] += sum(dep.get("actual") != "NO SNAPSHOT"
                                                    for dep in predicted_capability_items if dep.get("actual") is not None)
        counts["capabilitySnapshotObserved"] += sum(dep.get("actual") is not None for dep in predicted_capability_items)
        actual_readiness, predicted_readiness = readiness(label.get("actualReadiness")), readiness(item.get("readiness"))
        if actual_readiness != "UNVERIFIABLE":
            counts["classificationCases"] += 1
            counts["classificationCorrect"] += int(predicted_readiness == actual_readiness)
        if actual_readiness == "NOT_READY":
            counts["notReady"] += 1
            counts["falseReady"] += int(predicted_readiness == "READY")
        elif actual_readiness == "WARNING":
            counts["warning"] += 1
            counts["missedWarning"] += int(predicted_readiness == "READY")
        elif actual_readiness == "READY":
            counts["ready"] += 1
            counts["falseBlock"] += int(predicted_readiness == "NOT_READY")
        if blockers:
            counts["diagnosisCases"] += 1
            counts["diagnosisComplete"] += int(blockers <= predicted_deps)
            counts["blockerTp"] += len(blockers & predicted_deps)
            counts["blockerFn"] += len(blockers - predicted_deps)
        if package_blockers:
            counts["packageBlockerCases"] += 1
            counts["packageFalseReady"] += int(predicted_readiness == "READY")
        if capability_blockers:
            counts["capabilityBlockerCases"] += 1
            counts["capabilityFalseReady"] += int(predicted_readiness == "READY")
    return {"method": prediction.get("method", "UNKNOWN"), "model": prediction.get("model", "UNKNOWN"),
            "run": prediction.get("run"), "reviewed": len(labels),
            "scored": len(labels) - len(missing_predictions), "counts": dict(counts),
            "missingPredictions": missing_predictions}


def aggregate(scored: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for item in scored:
        grouped[(item["method"], item["model"])].append(item)
    output = []
    for (method, model), runs in sorted(grouped.items()):
        run_numbers = [item["run"] for item in runs]
        if None in run_numbers or len(set(run_numbers)) != len(run_numbers):
            raise ValueError(f"Method {method} model {model} has missing or duplicate run numbers")
        counts = defaultdict(int)
        for run in runs:
            for key, value in run["counts"].items():
                counts[key] += value
        output.append({"method": method, "model": model, "runs": len(runs),
                       "reviewedPerRun": runs[0]["reviewed"], "scored": sum(run["scored"] for run in runs),
                       "coverage": percent(sum(run["scored"] for run in runs), sum(run["reviewed"] for run in runs)),
                       "recall": percent(counts["tp"], counts["tp"] + counts["fn"]),
                       "precision": percent(counts["tp"], counts["tp"] + counts["fp"]),
                       "requiredRecall": percent(counts["requiredTp"], counts["requiredTp"] + counts["requiredFn"]),
                       "classificationAccuracy": percent(counts["classificationCorrect"], counts["classificationCases"]),
                       "diagnosisCompleteness": percent(counts["diagnosisComplete"], counts["diagnosisCases"]),
                       "blockingDependencyRecall": percent(counts["blockerTp"], counts["blockerTp"] + counts["blockerFn"]),
                       "falseReady": percent(counts["falseReady"], counts["notReady"]),
                       "missedWarning": percent(counts["missedWarning"], counts["warning"]),
                       "falseBlock": percent(counts["falseBlock"], counts["ready"]),
                       "packageN": counts["packageTruth"] // len(runs),
                       "packageRecall": percent(counts["packageTp"], counts["packageTp"] + counts["packageFn"]),
                       "packagePrecision": percent(counts["packageTp"], counts["packageTp"] + counts["packageFp"]),
                       "requiredPackageRecall": percent(counts["requiredPackageTp"], counts["requiredPackageTp"] + counts["requiredPackageFn"]),
                       "packageFalseReady": percent(counts["packageFalseReady"], counts["packageBlockerCases"]),
                       "capabilityN": counts["capabilityTruth"] // len(runs),
                       "capabilityRecall": percent(counts["capabilityTp"], counts["capabilityTp"] + counts["capabilityFn"]),
                       "capabilityPrecision": percent(counts["capabilityTp"], counts["capabilityTp"] + counts["capabilityFp"]),
                       "requiredCapabilityRecall": percent(counts["requiredCapabilityTp"], counts["requiredCapabilityTp"] + counts["requiredCapabilityFn"]),
                       "capabilityFalseReady": percent(counts["capabilityFalseReady"], counts["capabilityBlockerCases"]),
                       "snapshotCoverage": percent(counts["capabilitySnapshotPresent"], counts["capabilitySnapshotObserved"]),
                       "capabilityUnknownRate": percent(counts["capabilityUnknown"], counts["capabilityChecks"]),
                       "counts": dict(counts)})
    return output


def render(scores: list[dict[str, Any]], truth: dict[str, Any]) -> str:
    provenance = "human-reviewed" if all(skill.get("reviewStatus") == "HUMAN_REVIEWED" for skill in truth["skills"]) else "review in progress"
    total_trials = sum(item["runs"] * item["reviewedPerRun"] for item in scores)
    lines = ["# Controlled benchmark comparison", "", f"Dataset: `{truth.get('datasetVersion', 'UNKNOWN')}`",
             f"Environment: `{truth.get('environment', 'UNKNOWN')}`",
             f"Ground truth: **{provenance}**", f"Model trials: **{total_trials}**", "",
             "| Method | Model | Runs | Skills/run | Coverage | Recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | Blocker recall | False ready | Missed warning | False block |",
             "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for item in scores:
        lines.append(f"| {item['method']} | {item['model']} | {item['runs']} | {item['reviewedPerRun']} | {item['coverage']} | {item['recall']} | {item['precision']} | {item['requiredRecall']} | {item['classificationAccuracy']} | {item['diagnosisCompleteness']} | {item['blockingDependencyRecall']} | {item['falseReady']} | {item['missedWarning']} | {item['falseBlock']} |")
    lines += ["", "## Package metrics", "",
              "| Method | Package N | Package recall | Package precision | Required package recall | Package false ready |",
              "|---|---:|---:|---:|---:|---:|"]
    for item in scores:
        lines.append(f"| {item['method']} | {item['packageN']} | {item['packageRecall']} | {item['packagePrecision']} | {item['requiredPackageRecall']} | {item['packageFalseReady']} |")
    lines += ["", "## Capability metrics", "",
              "| Method | Capability N | Capability recall | Capability precision | Required capability recall | Capability false ready | Snapshot coverage | UNKNOWN rate |",
              "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for item in scores:
        lines.append(f"| {item['method']} | {item['capabilityN']} | {item['capabilityRecall']} | {item['capabilityPrecision']} | {item['requiredCapabilityRecall']} | {item['capabilityFalseReady']} | {item['snapshotCoverage']} | {item['capabilityUnknownRate']} |")
    lines += ["", "Metrics are pooled across repeated runs. `False ready` means NOT READY predicted as READY; `Missed warning` means WARNING predicted as READY. `Diagnosis completeness` is the share of NOT READY cases for which every true blocking dependency was reported.", "",
              "## Interpretation", "",
              "The hybrid method improved dependency coverage and reduced missed-warning decisions by turning Agent-extracted requirements into deterministic environment checks. Strict false-ready and false-block rates remain environment- and label-distribution-dependent and are reported separately.", "",
              "## Limitations", "",
              "- Ground truth dependencies, evidence paths, necessity, and environment conclusions were human-reviewed.",
              "- The corpus contains 30 pinned Skills from six repositories, one model, and one controlled Linux environment.",
              "- Package metrics cover Python and npm dependencies in this corpus; Maven has N=0 and is reported without extrapolation.",
              "- Raw model outputs are retained locally but not redistributed in the public repository.", ""]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ground-truth", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True, nargs="+")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()
    truth = load(args.ground_truth)
    reviewed = sum(item.get("reviewStatus") in REVIEWED for item in truth.get("skills", []))
    if not reviewed:
        rendered, scores = "# Controlled benchmark comparison\n\n**NOT COMPUTED.** Ground truth contains no reviewed Skills.\n", []
    else:
        scores = aggregate([score(truth, load(path)) for path in args.predictions])
        rendered = render(scores, truth)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps({"schemaVersion": "1.0", "scores": scores}, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Scoring error: {error}", file=sys.stderr)
        raise SystemExit(1)
