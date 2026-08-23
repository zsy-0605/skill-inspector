#!/usr/bin/env python3
"""Validate evidence and assign controlled-environment readiness to ground truth."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile
from typing import Any

EVIDENCE = re.compile(r"^([^:;]+):(\d+)")
ACCEPTED_INSPECTOR_EXITS = {0, 2}


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def controlled_environment() -> dict[str, str]:
    return {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "SKILL_INSPECTOR_PACKAGE_METADATA_MODE": "isolated"}


def handoff(dependencies: list[dict[str, Any]]) -> dict[str, Any]:
    requirements = []
    for item in dependencies:
        if not item.get("inScope"):
            continue
        required = item.get("required")
        if not required:
            required = "available" if item["type"] == "capability" else "*" if item["type"] in {"runtime", "package", "skill"} else "present"
        requirement = {"type": item["type"], "name": item["name"], "required": required,
                             "necessity": item["necessity"], "source": "INFERRED", "confidence": "HIGH",
                             "evidence": item["evidence"], "inferenceRule": "reviewed-ground-truth"}
        if item["type"] == "package": requirement["ecosystem"] = item["ecosystem"]
        if item["type"] == "capability": requirement["capabilityKind"] = item["capabilityKind"]
        if item["type"] == "skill" and item.get("namespace"): requirement["namespace"] = item["namespace"]
        requirements.append(requirement)
    schema_version = ("1.2" if any(item["type"] == "skill" for item in requirements)
                      else "1.1" if any(item["type"] == "capability" for item in requirements) else "1.0")
    return {"schemaVersion": schema_version, "requirements": requirements}


def main() -> int:
    project = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=project / "benchmark/dataset.json")
    parser.add_argument("--ground-truth", type=Path, default=project / "benchmark/annotations/ground-truth.json")
    parser.add_argument("--environment", type=Path, default=project / "benchmark/environment.json")
    parser.add_argument("--cache", type=Path, default=project / "benchmark/.cache")
    parser.add_argument("--jar", type=Path, default=project / "target/skill-inspector.jar")
    parser.add_argument("--java", required=True)
    parser.add_argument("--promote-evidence-reviewed", action="store_true")
    args = parser.parse_args()
    dataset, truth, environment = load(args.dataset), load(args.ground_truth), load(args.environment)
    if not Path(args.java).is_absolute() or not Path(args.java).is_file() or not args.jar.is_file():
        raise ValueError("A built JAR and absolute JDK 21+ --java path are required")
    targets = {skill["id"]: args.cache / repository["id"] / PurePosixPath(skill["path"])
               for repository in dataset["repositories"] for skill in repository["skills"]}

    with tempfile.TemporaryDirectory(prefix="skill-inspector-ground-truth-") as temporary:
        temp = Path(temporary)
        for label in truth["skills"]:
            target = targets[label["id"]]
            for dependency in label["dependencies"]:
                match = EVIDENCE.match(dependency["evidence"])
                if not match:
                    raise ValueError(f"Invalid evidence for {label['id']}: {dependency['evidence']}")
                source = target / match.group(1)
                if not source.is_file():
                    raise ValueError(f"Evidence file is missing for {label['id']}: {source}")
                with source.open(encoding="utf-8", errors="replace") as handle:
                    line_count = sum(1 for _ in handle)
                if int(match.group(2)) > line_count:
                    raise ValueError(f"Evidence line is outside file for {label['id']}: {dependency['evidence']}")

            input_path = temp / f"{label['id']}.json"
            input_path.write_text(json.dumps(handoff(label["dependencies"]), ensure_ascii=False), encoding="utf-8")
            command = [args.java, "-jar", str(args.jar.resolve()), "verify", str(target.resolve()),
                       "--requirements", str(input_path), "--json"]
            result = subprocess.run(command, text=True, capture_output=True, env=controlled_environment(), check=False)
            if result.returncode not in ACCEPTED_INSPECTOR_EXITS:
                raise RuntimeError(f"Inspector failed for {label['id']}: {result.stderr.strip() or result.stdout.strip()}")
            report = json.loads(result.stdout)
            label["actualReadiness"] = report["readiness"].replace(" ", "_")
            label["blockingDependencies"] = [{key: check[key] for key in ("type", "ecosystem", "capabilityKind", "name") if key in check}
                                               for check in report.get("checks", []) if check["status"] == "FAIL"]
            if args.promote_evidence_reviewed:
                label["reviewStatus"] = "EVIDENCE_REVIEWED"
                label["review"]["method"] = "static review plus deterministic evidence and environment validation; human signoff pending"
            print(f"Finalized: {label['id']} -> {label['actualReadiness']}", file=sys.stderr)

    truth["environment"] = environment["id"]
    args.ground_truth.write_text(json.dumps(truth, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"Ground-truth finalization error: {error}", file=sys.stderr)
        raise SystemExit(1)
