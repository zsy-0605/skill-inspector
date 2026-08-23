#!/usr/bin/env python3
"""Run repeated Agent-only and Agent + Skill Inspector trials under fixed conditions."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import time
from typing import Any

METHODS = ("AGENT_ONLY", "AGENT_WITH_INSPECTOR")
ACCEPTED_INSPECTOR_EXITS = {0, 2}


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def controlled_environment() -> dict[str, str]:
    allowed = {"HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "TERM", "SSL_CERT_FILE",
               "HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY", "NO_PROXY", "CODEX_HOME"}
    environment = {key: value for key, value in os.environ.items() if key in allowed}
    environment["PATH"] = "/usr/bin:/bin"
    environment["SKILL_INSPECTOR_PACKAGE_METADATA_MODE"] = "isolated"
    environment.setdefault("LANG", "C.UTF-8")
    environment.setdefault("CODEX_HOME", str(Path.home() / ".codex"))
    return environment


def run_codex(codex: str, model: str, target: Path, schema: Path, prompt: str,
              output: Path, log: Path, timeout: int, resume: bool) -> dict[str, Any]:
    metadata_path = output.with_suffix(".meta.json")
    expected = {"model": model, "promptSha256": hashlib.sha256(prompt.encode()).hexdigest(),
                "schemaSha256": hashlib.sha256(schema.read_bytes()).hexdigest(),
                "skillMdSha256": hashlib.sha256((target / "SKILL.md").read_bytes()).hexdigest()}
    if resume and (output.exists() or metadata_path.exists()):
        if not output.is_file() or not metadata_path.is_file():
            raise RuntimeError(f"Incomplete resumable output for {target.name}")
        metadata = load(metadata_path)
        actual_digest = hashlib.sha256(output.read_bytes()).hexdigest()
        if any(metadata.get(key) != value for key, value in expected.items()) or metadata.get("outputSha256") != actual_digest:
            raise RuntimeError(f"Refusing to resume mismatched output for {target.name}")
        return load(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    log.parent.mkdir(parents=True, exist_ok=True)
    command = [codex, "exec", "--ephemeral", "--ignore-user-config", "--ignore-rules",
               "--sandbox", "read-only", "--model", model, "--cd", str(target),
               "--output-schema", str(schema), "--output-last-message", str(output), prompt]
    last_detail = ""
    for attempt in range(1, 4):
        result = subprocess.run(command, text=True, capture_output=True, env=controlled_environment(),
                                timeout=timeout, check=False)
        log.write_text(result.stderr + "\n--- STDOUT ---\n" + result.stdout, encoding="utf-8")
        if result.returncode == 0:
            metadata_path.write_text(json.dumps({**expected, "outputSha256": hashlib.sha256(output.read_bytes()).hexdigest()},
                                                indent=2) + "\n", encoding="utf-8")
            return load(output)
        last_detail = result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "no error detail"
        if attempt < 3:
            time.sleep(2 * attempt)
    raise RuntimeError(f"Codex failed for {target.name}: {last_detail}")


def prediction_requirement(item: dict[str, Any], source: str | None = None) -> dict[str, Any]:
    result = {"type": item["type"], "name": item["name"], "inScope": True,
              "evidence": item.get("evidence") or "No evidence returned",
              "necessity": item.get("necessity", "CONDITIONAL")}
    if item.get("type") == "package" and item.get("ecosystem"):
        result["ecosystem"] = item["ecosystem"]
    if item.get("type") == "capability" and item.get("capabilityKind"):
        result["capabilityKind"] = item["capabilityKind"]
    if item.get("version") or item.get("required"):
        result["required"] = item.get("version") or item.get("required")
    if source or item.get("source"):
        result["source"] = source or item["source"]
    if item.get("confidence"):
        result["confidence"] = item["confidence"]
    if item.get("status"):
        result["status"] = item["status"]
    if item.get("actual"):
        result["actual"] = item["actual"]
    return result


def semantic_handoff(payload: dict[str, Any]) -> dict[str, Any]:
    handoff = json.loads(json.dumps(payload))
    for requirement in handoff.get("requirements", []):
        for field in [name for name, value in requirement.items() if value is None]:
            requirement.pop(field)
        if requirement.get("type") != "package":
            requirement.pop("ecosystem", None)
        if requirement.get("type") != "capability":
            requirement.pop("capabilityKind", None)
    if any(item.get("type") == "capability" for item in handoff.get("requirements", [])):
        handoff["schemaVersion"] = "1.1"
    return handoff


def run_trial(method: str, run_number: int, skill_id: str, target: Path, project: Path,
              raw: Path, codex: str, model: str, java: str, jar: Path,
              environment: dict[str, Any], timeout: int, resume: bool) -> dict[str, Any]:
    method_dir = raw / method.lower().replace("_", "-") / f"run-{run_number}"
    model_output = method_dir / f"{skill_id}-model.json"
    log = method_dir / f"{skill_id}.log"
    environment_text = json.dumps(environment, ensure_ascii=False, sort_keys=True)
    common = ("\n\nControlled environment: " + environment_text +
              "\nFor shell availability checks, use exactly PATH=/usr/bin:/bin. Do not browse the web. "
              "Do not read files outside the target Skill directory.")
    if method == "AGENT_ONLY":
        prompt = (project / "benchmark/prompts/agent-only.md").read_text(encoding="utf-8") + common
        payload = run_codex(codex, model, target, (project / "benchmark/agent-only-output.schema.json").resolve(),
                            prompt, model_output, log, timeout, resume)
        return {"id": skill_id, "readiness": payload["readiness"],
                "dependencies": [prediction_requirement(item) for item in payload["requirements"]]}

    prompt = (project / "benchmark/prompts/semantic-extraction.md").read_text(encoding="utf-8") + common + \
             "\nReturn semantic requirements only. Do not inspect availability; Java will verify it."
    payload = run_codex(codex, model, target, (project / "benchmark/semantic-extraction.schema.json").resolve(),
                        prompt, model_output, log, timeout, resume)
    handoff = semantic_handoff(payload)
    handoff_output = method_dir / f"{skill_id}-handoff.json"
    handoff_output.write_text(json.dumps(handoff, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    inspector_output = method_dir / f"{skill_id}-inspector.json"
    command = [java, "-jar", str(jar), "verify", str(target), "--requirements", str(handoff_output), "--json"]
    result = subprocess.run(command, text=True, capture_output=True, env=controlled_environment(), check=False)
    if result.returncode not in ACCEPTED_INSPECTOR_EXITS:
        raise RuntimeError(f"Inspector failed for {skill_id}: {result.stderr.strip() or result.stdout.strip()}")
    inspector_output.write_text(result.stdout, encoding="utf-8")
    report = json.loads(result.stdout)
    return {"id": skill_id, "readiness": report["readiness"].replace(" ", "_"),
            "dependencies": [prediction_requirement(check, check.get("source")) for check in report.get("checks", [])]}


def main() -> int:
    project = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=project / "benchmark/dataset.json")
    parser.add_argument("--cache", type=Path, default=project / "benchmark/.cache")
    parser.add_argument("--environment", type=Path, default=project / "benchmark/environment.json")
    parser.add_argument("--output-dir", type=Path, default=project / "benchmark/results/raw")
    parser.add_argument("--jar", type=Path, default=project / "target/skill-inspector.jar")
    parser.add_argument("--java", required=True, help="Absolute JDK 21+ java executable used to launch the inspector")
    parser.add_argument("--codex", default="codex")
    parser.add_argument("--model", default="gpt-5.6-sol")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--skill", action="append", help="Limit to one or more Skill ids (smoke testing)")
    args = parser.parse_args()
    if args.runs < 1 or args.runs > 10 or args.jobs < 1 or args.jobs > 8:
        raise ValueError("runs must be 1..10 and jobs must be 1..8")
    if not Path(args.java).is_absolute() or not Path(args.java).is_file():
        raise ValueError("--java must be an absolute executable path")
    codex = str(Path(args.codex).resolve()) if "/" in args.codex else shutil.which(args.codex)
    if not codex:
        raise ValueError("Codex CLI was not found; pass --codex /absolute/path/to/codex")
    if not args.jar.is_file():
        raise RuntimeError("Inspector JAR is missing; run ./mvnw package first")

    dataset, environment = load(args.dataset), load(args.environment)
    skills: list[tuple[str, Path]] = []
    for repository in dataset["repositories"]:
        checkout = args.cache / repository["id"]
        marker = checkout / ".skill-inspector-commit"
        if not marker.is_file() or marker.read_text(encoding="utf-8").strip() != repository["commit"]:
            raise RuntimeError(f"Pinned cache mismatch: {repository['id']}")
        for skill in repository["skills"]:
            if args.skill and skill["id"] not in args.skill:
                continue
            target = checkout.joinpath(*PurePosixPath(skill["path"]).parts).resolve()
            skills.append((skill["id"], target))
    if args.skill and len(skills) != len(set(args.skill)):
        raise ValueError("One or more --skill ids are not in the dataset")

    tasks = [(method, run_number, skill_id, target) for method in METHODS
             for run_number in range(1, args.runs + 1) for skill_id, target in skills]
    completed: dict[tuple[str, int, str], dict[str, Any]] = {}
    with ThreadPoolExecutor(max_workers=args.jobs) as executor:
        futures = {executor.submit(run_trial, method, run_number, skill_id, target, project,
                                   args.output_dir.resolve(), codex, args.model, args.java,
                                   args.jar.resolve(), environment, args.timeout, args.resume): (method, run_number, skill_id)
                   for method, run_number, skill_id, target in tasks}
        for future in as_completed(futures):
            key = futures[future]
            completed[key] = future.result()
            print(f"Completed: {key[0]} run={key[1]} skill={key[2]}", file=sys.stderr)

    generated = dt.datetime.now(dt.timezone.utc).isoformat()
    for method in METHODS:
        for run_number in range(1, args.runs + 1):
            payload = {"schemaVersion": "1.1", "datasetVersion": dataset["datasetVersion"],
                       "method": method, "model": args.model, "run": run_number,
                       "environment": environment["id"], "generatedAt": generated,
                       "skills": [completed[(method, run_number, skill_id)] for skill_id, _ in skills]}
            output = args.output_dir / f"{method.lower().replace('_', '-')}-run-{run_number}.json"
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"Wrote {output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError, subprocess.TimeoutExpired) as error:
        print(f"Controlled benchmark error: {error}", file=sys.stderr)
        raise SystemExit(1)
