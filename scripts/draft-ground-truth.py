#!/usr/bin/env python3
"""Create reviewable ground-truth drafts from pinned Skill snapshots."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import datetime as dt
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
from typing import Any

BASELINE_COMMANDS = {"bash", "sh", "mkdir", "rm", "mv", "cp", "grep", "sed", "cut", "find",
                     "head", "wc", "tr", "sleep", "tar", "basename"}
TRANSIENT_ENVIRONMENT = {"OSTYPE", "HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy",
                         "TMPDIR", "TEMP", "TMP", "CODEX_HOME"}
STANDARD_LIBRARY_PACKAGES = {"argparse", "json", "sys"}
RUNTIME_ALIASES = {"python3": "python", "node.js": "node", "nodejs": "node"}
SUPPORTED_RUNTIMES = {"java", "python", "node"}
SUPPORTED_OPERATING_SYSTEMS = {"linux", "macos", "windows"}
NECESSITY_OVERRIDES = {
    ("openai-speech", "environmentVariable", "OPENAI_API_KEY"): "REQUIRED",
    ("openai-transcribe", "environmentVariable", "OPENAI_API_KEY"): "REQUIRED",
}


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def safe_environment() -> dict[str, str]:
    allowed = {"HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "TERM", "SSL_CERT_FILE",
               "HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY", "NO_PROXY", "CODEX_HOME"}
    environment = {key: value for key, value in os.environ.items() if key in allowed}
    environment["PATH"] = "/usr/bin:/bin"
    return environment


def review_one(codex: str, model: str, prompt: str, schema: Path, target: Path,
               output: Path, timeout: int) -> dict[str, Any]:
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [codex, "exec", "--ephemeral", "--ignore-user-config", "--ignore-rules",
               "--sandbox", "read-only", "--model", model, "--cd", str(target),
               "--output-schema", str(schema), "--output-last-message", str(output), prompt]
    result = subprocess.run(command, text=True, capture_output=True, env=safe_environment(),
                            timeout=timeout, check=False)
    if result.returncode:
        detail = result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "no error detail"
        raise RuntimeError(f"Codex review failed for {target.name}: {detail}")
    payload = load(output)
    if not isinstance(payload.get("requirements"), list):
        raise ValueError(f"Review output has no requirements array: {target}")
    return payload


def normalize_requirements(skill_id: str, payload: dict[str, Any], target: Path) -> list[dict[str, Any]]:
    normalized: dict[tuple[str, str, str], dict[str, Any]] = {}
    os_values: list[str] = []
    necessity_rank = {"OPTIONAL": 1, "CONDITIONAL": 2, "REQUIRED": 3}
    for raw in payload.get("requirements", []):
        item = dict(raw)
        kind = item["type"]
        name = item["name"].strip()
        lowered = name.lower()
        if kind == "runtime":
            lowered = RUNTIME_ALIASES.get(lowered, lowered)
            if lowered in BASELINE_COMMANDS:
                continue
            name = lowered
            if lowered not in SUPPORTED_RUNTIMES:
                item["inScope"] = False
        elif kind == "command":
            name = lowered
            if name in BASELINE_COMMANDS:
                continue
        elif kind == "environmentVariable" and name in TRANSIENT_ENVIRONMENT:
            continue
        elif kind == "package" and lowered in STANDARD_LIBRARY_PACKAGES:
            continue
        elif kind == "package":
            if item.get("ecosystem") not in {"python", "npm", "maven"}:
                raise ValueError(f"Package requirement needs a supported ecosystem: {skill_id}/{name}")
            item["version"] = item.get("version") or item.get("required") or "*"
            item["required"] = item["version"]
        elif kind == "capability":
            if item.get("capabilityKind") not in {"mcpServer", "tool", "capability"}:
                raise ValueError(f"Capability requirement needs a supported capabilityKind: {skill_id}/{name}")
            item["required"] = "available"
        elif kind == "skill":
            namespace = item.get("namespace")
            if namespace:
                namespace = namespace.strip()
                item["namespace"] = namespace
            item["version"] = item.get("version") or item.get("required") or "*"
            item["required"] = item["version"]
        elif kind == "operatingSystem":
            name = lowered
            if name not in SUPPORTED_OPERATING_SYSTEMS:
                item["inScope"] = False
            else:
                os_values.append(name)
                name = "operating-system"
                item["required"] = ",".join(dict.fromkeys(os_values))
        elif kind in {"file", "directory"}:
            candidate = Path(name)
            if not candidate.is_absolute() and target.joinpath(candidate).exists():
                continue
            if candidate.is_absolute() and str(candidate).startswith("/tmp"):
                continue
        item["name"] = name
        if kind not in {"package", "skill"}:
            item.pop("ecosystem", None)
            item.pop("version", None)
        if kind != "capability":
            item.pop("capabilityKind", None)
        if kind != "skill":
            item.pop("namespace", None)
        item["necessity"] = NECESSITY_OVERRIDES.get((skill_id, kind, name), item["necessity"])
        qualifier = str(item.get("ecosystem", "") if kind == "package" else item.get("capabilityKind", "")
                        if kind == "capability" else item.get("namespace", "") if kind == "skill" else "")
        key = (kind, qualifier, name if kind in {"capability", "skill"} else name.lower())
        previous = normalized.get(key)
        if previous is None or necessity_rank[item["necessity"]] > necessity_rank[previous["necessity"]]:
            normalized[key] = item
        elif kind == "operatingSystem" and previous is not None and os_values:
            previous["required"] = ",".join(dict.fromkeys(os_values))
    return list(normalized.values())


def main() -> int:
    project = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=project / "benchmark/dataset.json")
    parser.add_argument("--cache", type=Path, default=project / "benchmark/.cache")
    parser.add_argument("--output", type=Path, default=project / "benchmark/annotations/ground-truth.json")
    parser.add_argument("--draft-dir", type=Path, default=project / "benchmark/annotations/drafts")
    parser.add_argument("--codex", default="codex")
    parser.add_argument("--model", default="gpt-5.6-sol")
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--reuse-drafts", action="store_true")
    args = parser.parse_args()
    if args.jobs < 1 or args.jobs > 8:
        raise ValueError("--jobs must be between 1 and 8")
    codex = str(Path(args.codex).resolve()) if "/" in args.codex else shutil.which(args.codex)
    if not args.reuse_drafts and not codex:
        raise ValueError("Codex CLI was not found; pass --codex /absolute/path/to/codex")

    dataset = load(args.dataset)
    prompt = (project / "benchmark/prompts/ground-truth-review.md").read_text(encoding="utf-8")
    schema = (project / "benchmark/ground-truth-review.schema.json").resolve()
    work: list[tuple[str, Path, Path]] = []
    for repository in dataset["repositories"]:
        checkout = args.cache / repository["id"]
        marker = checkout / ".skill-inspector-commit"
        if not marker.is_file() or marker.read_text(encoding="utf-8").strip() != repository["commit"]:
            raise RuntimeError(f"Pinned cache is missing or mismatched: {repository['id']}")
        for skill in repository["skills"]:
            target = checkout.joinpath(*PurePosixPath(skill["path"]).parts)
            if not (target / "SKILL.md").is_file():
                raise RuntimeError(f"Missing cached Skill: {skill['id']}")
            work.append((skill["id"], target.resolve(), (args.draft_dir / f"{skill['id']}.json").resolve()))

    completed: dict[str, dict[str, Any]] = {}
    if args.reuse_drafts:
        for skill_id, _, output in work:
            completed[skill_id] = load(output)
    else:
        with ThreadPoolExecutor(max_workers=args.jobs) as executor:
            futures = {executor.submit(review_one, codex, args.model, prompt, schema, target, output, args.timeout): skill_id
                       for skill_id, target, output in work}
            for future in as_completed(futures):
                skill_id = futures[future]
                completed[skill_id] = future.result()
                print(f"Reviewed: {skill_id}", file=sys.stderr)

    timestamp = dt.datetime.now(dt.timezone.utc).isoformat()
    skills = []
    for skill_id, target, _ in work:
        draft = completed[skill_id]
        skills.append({"id": skill_id, "reviewStatus": "DRAFT", "actualReadiness": None,
                       "blockingDependencies": [],
                       "dependencies": normalize_requirements(skill_id, draft, target), "notes": draft.get("notes", ""),
                       "review": {"method": "static review; human signoff pending",
                                  "model": args.model, "reviewedAt": timestamp, "humanSignoff": False}})
    payload = {
        "schemaVersion": "1.1", "datasetVersion": dataset["datasetVersion"],
        "environment": "Readiness is assigned separately under benchmark/environment.json.",
        "scope": ["runtime", "command", "environmentVariable", "file", "directory", "operatingSystem", "package", "capability"],
        "reviewPolicy": "static review; dependencies require evidence and keep necessity separate from source. EVIDENCE_REVIEWED requires deterministic evidence and environment validation. Human signoff is never implied.",
        "skills": skills,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError, subprocess.TimeoutExpired) as error:
        print(f"Ground-truth draft error: {error}", file=sys.stderr)
        raise SystemExit(1)
