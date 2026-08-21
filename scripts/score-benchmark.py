#!/usr/bin/env python3
"""Score Agent-only and Agent + Skill Inspector predictions against reviewed ground truth."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any


def load(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def percent(numerator: int, denominator: int) -> str:
    return f"{100.0 * numerator / denominator:.1f}%" if denominator else "N/A"


def score(truth: dict[str, Any], prediction: dict[str, Any]) -> dict[str, Any]:
    if truth.get("datasetVersion") != prediction.get("datasetVersion"):
        raise ValueError("Ground truth and prediction datasetVersion differ")
    labels = {item["id"]: item for item in truth.get("skills", []) if item.get("reviewStatus") == "REVIEWED"}
    predicted = {item["id"]: item for item in prediction.get("skills", [])}
    tp = fp = fn = false_ready = false_block = actual_ready = actual_not_ready = 0
    missing_predictions: list[str] = []
    for skill_id, label in labels.items():
        if skill_id not in predicted:
            missing_predictions.append(skill_id)
            fn += sum(bool(item.get("inScope")) for item in label.get("dependencies", []))
            continue
        actual_deps = {(item["type"], item["name"].lower()) for item in label.get("dependencies", []) if item.get("inScope")}
        predicted_deps = {(item["type"], item["name"].lower()) for item in predicted[skill_id].get("dependencies", []) if item.get("inScope")}
        tp += len(actual_deps & predicted_deps)
        fp += len(predicted_deps - actual_deps)
        fn += len(actual_deps - predicted_deps)
        if label.get("actualReadiness") == "READY":
            actual_ready += 1
            false_block += int(predicted[skill_id].get("readiness") == "NOT_READY")
        elif label.get("actualReadiness") == "NOT_READY":
            actual_not_ready += 1
            false_ready += int(predicted[skill_id].get("readiness") == "READY")
    return {
        "method": prediction.get("method", "UNKNOWN"), "model": prediction.get("model", "UNKNOWN"),
        "reviewed": len(labels), "scored": len(labels) - len(missing_predictions),
        "coverage": percent(len(labels) - len(missing_predictions), len(labels)),
        "recall": percent(tp, tp + fn), "precision": percent(tp, tp + fp),
        "falseReady": percent(false_ready, actual_not_ready), "falseBlock": percent(false_block, actual_ready),
        "missingPredictions": missing_predictions,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ground-truth", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True, nargs="+")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    truth = load(args.ground_truth)
    reviewed = sum(item.get("reviewStatus") == "REVIEWED" for item in truth.get("skills", []))
    if not reviewed:
        rendered = "# Benchmark comparison\n\n**NOT COMPUTED.** Ground truth contains no REVIEWED Skills.\n"
    else:
        scores = [score(truth, load(path)) for path in args.predictions]
        lines = ["# Benchmark comparison", "", "| Method | Model | Reviewed | Scored | Coverage | Recall | Precision | False Ready | False Block |",
                 "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
        for item in scores:
            lines.append(f"| {item['method']} | {item['model']} | {item['reviewed']} | {item['scored']} | {item['coverage']} | {item['recall']} | {item['precision']} | {item['falseReady']} | {item['falseBlock']} |")
        rendered = "\n".join(lines) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Scoring error: {error}", file=sys.stderr)
        raise SystemExit(1)
