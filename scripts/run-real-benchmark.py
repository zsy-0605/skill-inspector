#!/usr/bin/env python3
"""Fetch pinned public Skills, inspect them without running target code, and report honest metrics."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from typing import Any

COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
GITHUB_RE = re.compile(r"^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.git$")
ACCEPTED_INSPECTOR_EXITS = {0, 2}


def run(command: list[str], cwd: Path | None = None, env: dict[str, str] | None = None,
        accepted: set[int] = {0}) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, cwd=cwd, env=env, text=True, capture_output=True, check=False)
    if result.returncode not in accepted:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"Command failed ({result.returncode}): {command[0]}: {detail}")
    return result


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def validate_dataset(dataset: dict[str, Any]) -> None:
    seen: set[str] = set()
    if dataset.get("schemaVersion") != "1.0":
        raise ValueError("Unsupported dataset schemaVersion")
    for repository in dataset.get("repositories", []):
        if not GITHUB_RE.fullmatch(repository.get("url", "")):
            raise ValueError(f"Only explicit public GitHub HTTPS URLs are allowed: {repository.get('url')}")
        if not COMMIT_RE.fullmatch(repository.get("commit", "")):
            raise ValueError(f"Repository {repository.get('id')} must use a full 40-character commit")
        for skill in repository.get("skills", []):
            skill_id = skill.get("id", "")
            path = PurePosixPath(skill.get("path", ""))
            if not skill_id or skill_id in seen:
                raise ValueError(f"Missing or duplicate Skill id: {skill_id}")
            if path.is_absolute() or ".." in path.parts or not path.parts:
                raise ValueError(f"Unsafe Skill path: {path}")
            seen.add(skill_id)
    if not seen:
        raise ValueError("Dataset contains no Skills")


def checkout_repository(repository: dict[str, Any], cache: Path, offline: bool) -> Path:
    destination = cache / repository["id"]
    marker = destination / ".skill-inspector-commit"
    expected = repository["commit"]
    if marker.is_file() and marker.read_text(encoding="utf-8").strip() == expected:
        return destination
    if offline:
        raise RuntimeError(f"Pinned cache is unavailable for {repository['id']}")
    if destination.exists():
        raise RuntimeError(f"Refusing to overwrite non-matching cache: {destination}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    git_env = os.environ.copy()
    git_env["GIT_LFS_SKIP_SMUDGE"] = "1"
    run(["git", "clone", "--filter=blob:none", "--no-checkout", repository["url"], str(destination)], env=git_env)
    paths = [skill["path"] for skill in repository["skills"]]
    run(["git", "sparse-checkout", "set", "--no-cone", *paths], cwd=destination, env=git_env)
    run(["git", "fetch", "--depth", "1", "origin", expected], cwd=destination, env=git_env)
    run(["git", "-c", "filter.lfs.smudge=", "-c", "filter.lfs.required=false",
         "checkout", "--detach", expected], cwd=destination, env=git_env)
    actual = run(["git", "rev-parse", "HEAD"], cwd=destination).stdout.strip()
    if actual != expected:
        raise RuntimeError(f"Commit mismatch for {repository['id']}: {actual}")
    marker.write_text(expected + "\n", encoding="utf-8")
    return destination


def inspect_skill(java: str, jar: Path, repository: dict[str, Any], skill: dict[str, Any], checkout: Path) -> dict[str, Any]:
    target = checkout.joinpath(*PurePosixPath(skill["path"]).parts)
    if not (target / "SKILL.md").is_file():
        raise RuntimeError(f"Pinned Skill is missing SKILL.md: {skill['id']}")
    result = run([java, "-jar", str(jar), "inspect", str(target), "--json"], accepted=ACCEPTED_INSPECTOR_EXITS)
    report = json.loads(result.stdout)
    report["target"] = f"{repository['url']}@{repository['commit']}:{skill['path']}"
    return {
        "id": skill["id"], "repository": repository["id"], "category": skill["category"],
        "sourcePath": skill["path"], "inspectorExitCode": result.returncode, "report": report
    }


def calculate_summary(results: list[dict[str, Any]]) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "totalSkills": len(results), "READY": 0, "WARNING": 0, "NOT_READY": 0, "ERROR": 0,
        "declaredDependencies": 0, "inferredDependencies": 0,
        "skillsWithNoChecks": 0,
        "missingCommands": 0, "missingRuntimes": 0, "missingEnvironmentVariables": 0,
    }
    for item in results:
        report = item.get("report", {})
        readiness = report.get("readiness", "ERROR")
        summary[readiness if readiness in ("READY", "WARNING", "NOT_READY") else "ERROR"] += 1
        if not report.get("checks"):
            summary["skillsWithNoChecks"] += 1
        for check in report.get("checks", []):
            source_key = "declaredDependencies" if check.get("source") == "DECLARED" else "inferredDependencies"
            summary[source_key] += 1
            if check.get("actual") in ("NOT FOUND", "MISSING"):
                key = {"command": "missingCommands", "runtime": "missingRuntimes",
                       "environmentVariable": "missingEnvironmentVariables"}.get(check.get("type"))
                if key:
                    summary[key] += 1
    return summary


def calculate_metrics(results: list[dict[str, Any]], annotations: dict[str, Any]) -> dict[str, Any]:
    labels = {item["id"]: item for item in annotations.get("skills", []) if item.get("reviewStatus") == "REVIEWED"}
    by_id = {item["id"]: item for item in results}
    true_positive = false_positive = false_negative = 0
    false_ready = false_block = ready_actual = not_ready_actual = 0
    for skill_id, label in labels.items():
        if skill_id not in by_id:
            continue
        actual = {(item["type"], item["name"].lower()) for item in label.get("dependencies", []) if item.get("inScope")}
        predicted = {(item["type"], item["name"].lower()) for item in by_id[skill_id]["report"].get("checks", [])}
        true_positive += len(actual & predicted)
        false_positive += len(predicted - actual)
        false_negative += len(actual - predicted)
        actual_readiness = label.get("actualReadiness")
        predicted_readiness = by_id[skill_id]["report"].get("readiness")
        if actual_readiness == "READY":
            ready_actual += 1
            false_block += int(predicted_readiness == "NOT READY")
        elif actual_readiness == "NOT_READY":
            not_ready_actual += 1
            false_ready += int(predicted_readiness == "READY")
    if not labels:
        return {"status": "NOT_COMPUTED", "reason": "No REVIEWED ground-truth annotations", "reviewedSkills": 0}
    ratio = lambda numerator, denominator: round(100.0 * numerator / denominator, 1) if denominator else None
    return {
        "status": "COMPUTED", "reviewedSkills": len(labels),
        "dependencyRecallPercent": ratio(true_positive, true_positive + false_negative),
        "dependencyPrecisionPercent": ratio(true_positive, true_positive + false_positive),
        "falseReadyRatePercent": ratio(false_ready, not_ready_actual),
        "falseBlockRatePercent": ratio(false_block, ready_actual),
        "counts": {"truePositive": true_positive, "falsePositive": false_positive, "falseNegative": false_negative,
                   "falseReady": false_ready, "falseBlock": false_block}
    }


def write_markdown(path: Path, payload: dict[str, Any]) -> None:
    summary, metrics = payload["summary"], payload["metrics"]
    lines = ["# Real-world Skill Benchmark — Java static layer", "", f"Dataset: `{payload['datasetVersion']}`",
             f"Generated: `{payload['generatedAt']}`", "", "## Raw inspection summary", "",
             "| Measure | Count |", "|---|---:|",
             f"| Skills | {summary['totalSkills']} |", f"| READY | {summary['READY']} |",
             f"| WARNING | {summary['WARNING']} |", f"| NOT READY | {summary['NOT_READY']} |",
             f"| Declared dependencies | {summary['declaredDependencies']} |",
             f"| Inferred dependencies | {summary['inferredDependencies']} |",
             f"| Skills with no checks | {summary['skillsWithNoChecks']} |",
             f"| Missing commands | {summary['missingCommands']} |",
             f"| Missing runtimes | {summary['missingRuntimes']} |",
             f"| Missing environment variables | {summary['missingEnvironmentVariables']} |", "",
             "## Ground-truth metrics", ""]
    if metrics["status"] == "NOT_COMPUTED":
        lines += ["**NOT COMPUTED.** No reviewed ground truth is available. Raw counts are not accuracy claims.", ""]
    else:
        lines += ["| Measure | Value |", "|---|---:|",
                  f"| Reviewed Skills | {metrics['reviewedSkills']} |",
                  f"| Dependency recall | {metrics['dependencyRecallPercent']}% |",
                  f"| Dependency precision | {metrics['dependencyPrecisionPercent']}% |",
                  f"| False ready rate | {metrics['falseReadyRatePercent']}% |",
                  f"| False block rate | {metrics['falseBlockRatePercent']}% |", ""]
    lines += ["## Per-Skill results", "", "| Skill | Repository | Readiness | Score | Checks |",
              "|---|---|---:|---:|---:|"]
    for item in payload["results"]:
        report = item["report"]
        lines.append(f"| `{item['id']}` | `{item['repository']}` | {report.get('readiness')} | {report.get('score')} | {len(report.get('checks', []))} |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    project = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=project / "benchmark/dataset.json")
    parser.add_argument("--annotations", type=Path, default=project / "benchmark/annotations/ground-truth.json")
    parser.add_argument("--cache", type=Path, default=project / "benchmark/.cache")
    parser.add_argument("--output", type=Path, default=project / "benchmark/results/latest.json")
    parser.add_argument("--jar", type=Path, default=project / "target/skill-inspector.jar")
    parser.add_argument("--java", default="java")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()

    dataset = load_json(args.dataset)
    validate_dataset(dataset)
    if not args.jar.is_file():
        raise RuntimeError(f"Inspector JAR not found: {args.jar}; run ./mvnw package first")
    results: list[dict[str, Any]] = []
    for repository in dataset["repositories"]:
        print(f"Fetching pinned snapshot: {repository['id']}", file=sys.stderr)
        checkout = checkout_repository(repository, args.cache, args.offline)
        for skill in repository["skills"]:
            print(f"Inspecting: {skill['id']}", file=sys.stderr)
            results.append(inspect_skill(args.java, args.jar.resolve(), repository, skill, checkout))

    annotations = load_json(args.annotations)
    if annotations.get("datasetVersion") != dataset["datasetVersion"]:
        raise ValueError("Dataset and ground-truth annotation versions differ")
    payload = {
        "schemaVersion": "1.0", "method": "JAVA_STATIC", "datasetVersion": dataset["datasetVersion"],
        "datasetSha256": hashlib.sha256(args.dataset.read_bytes()).hexdigest(),
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "safety": "Pinned sparse checkout; no target scripts, imports, package managers, hooks, or submodules executed.",
        "summary": calculate_summary(results), "metrics": calculate_metrics(results, annotations), "results": results
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    markdown = args.output.with_suffix(".md")
    write_markdown(markdown, payload)
    print(f"Wrote {args.output}", file=sys.stderr)
    print(f"Wrote {markdown}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"Benchmark error: {error}", file=sys.stderr)
        raise SystemExit(1)
