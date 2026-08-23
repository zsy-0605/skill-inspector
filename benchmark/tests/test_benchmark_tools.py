import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = PROJECT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


runner = load_script("run-real-benchmark.py")
scorer = load_script("score-benchmark.py")
controlled = load_script("run-controlled-benchmark.py")
draft = load_script("draft-ground-truth.py")
finalizer = load_script("finalize-ground-truth.py")


class BenchmarkRunnerTest(unittest.TestCase):
    def test_dataset_has_30_unique_pinned_skills(self):
        dataset = json.loads((PROJECT / "benchmark/dataset.json").read_text(encoding="utf-8"))
        runner.validate_dataset(dataset)
        skills = [skill["id"] for repo in dataset["repositories"] for skill in repo["skills"]]
        self.assertEqual(30, len(skills))
        self.assertEqual(30, len(set(skills)))
        self.assertTrue(all(len(repo["commit"]) == 40 for repo in dataset["repositories"]))

    def test_v03_capability_pilot_has_fixed_positive_and_negative_samples(self):
        pilot = json.loads((PROJECT / "benchmark/annotations/v0.3-capability-pilot.json").read_text(encoding="utf-8"))
        dataset = json.loads((PROJECT / "benchmark/dataset.json").read_text(encoding="utf-8"))
        dataset_ids = {skill["id"] for repo in dataset["repositories"] for skill in repo["skills"]}
        sample_ids = [sample["skillId"] for sample in pilot["samples"]]
        self.assertEqual(6, len(sample_ids))
        self.assertEqual(6, len(set(sample_ids)))
        self.assertTrue(set(sample_ids) <= dataset_ids)
        self.assertEqual(3, sum(sample["classification"] == "NEGATIVE" for sample in pilot["samples"]))
        self.assertGreaterEqual(sum(len(sample["requirements"]) for sample in pilot["samples"]), 6)

    def test_v04_skill_dependency_pilot_reuses_fixed_corpus(self):
        pilot = json.loads((PROJECT / "benchmark/annotations/v0.4-skill-dependency-pilot.json").read_text(encoding="utf-8"))
        dataset = json.loads((PROJECT / "benchmark/dataset.json").read_text(encoding="utf-8"))
        dataset_ids = {skill["id"] for repo in dataset["repositories"] for skill in repo["skills"]}
        sample_ids = [sample["skillId"] for sample in pilot["samples"]]
        self.assertEqual(5, len(sample_ids))
        self.assertEqual(5, len(set(sample_ids)))
        self.assertTrue(set(sample_ids) <= dataset_ids)
        self.assertEqual(2, sum(bool(sample["dependencies"]) for sample in pilot["samples"]))
        self.assertEqual(3, sum(not sample["dependencies"] for sample in pilot["samples"]))

    def test_capability_ground_truth_preserves_exact_name_and_kind(self):
        payload = {"requirements": [{"type": "capability", "ecosystem": None, "capabilityKind": "tool",
                                      "name": "ExactTool", "version": "available", "necessity": "REQUIRED",
                                      "source": "SKILL_TEXT", "inScope": True, "evidence": "SKILL.md:1", "notes": ""}]}
        normalized = draft.normalize_requirements("sample", payload, PROJECT)
        handoff = finalizer.handoff(normalized)

        self.assertEqual("ExactTool", normalized[0]["name"])
        self.assertEqual("tool", normalized[0]["capabilityKind"])
        self.assertEqual("1.1", handoff["schemaVersion"])
        self.assertEqual("available", handoff["requirements"][0]["required"])

    def test_dataset_rejects_traversal_and_non_github_urls(self):
        invalid = {"schemaVersion": "1.0", "repositories": [{
            "id": "bad", "url": "https://example.test/repo.git", "commit": "a" * 40,
            "skills": [{"id": "bad", "path": "../outside", "category": "test"}]
        }]}
        with self.assertRaises(ValueError):
            runner.validate_dataset(invalid)

    def test_raw_summary_does_not_claim_accuracy(self):
        results = [{"id": "sample", "report": {"readiness": "WARNING", "checks": []}}]
        summary = runner.calculate_summary(results)
        metrics = runner.calculate_metrics(results, {"skills": []})
        self.assertEqual(1, summary["skillsWithNoChecks"])
        self.assertEqual("NOT_COMPUTED", metrics["status"])

    def test_static_layer_scoring_separates_false_ready_and_missed_warning(self):
        results = [{"id": "blocked", "report": {"readiness": "READY", "checks": []}},
                   {"id": "warning", "report": {"readiness": "READY", "checks": []}}]
        truth = {"skills": [{"id": "blocked", "reviewStatus": "HUMAN_REVIEWED",
                             "actualReadiness": "NOT_READY", "dependencies": []},
                            {"id": "warning", "reviewStatus": "HUMAN_REVIEWED",
                             "actualReadiness": "WARNING", "dependencies": []}]}

        metrics = runner.calculate_metrics(results, truth)

        self.assertEqual(100.0, metrics["falseReadyRatePercent"])
        self.assertEqual(100.0, metrics["missedWarningRatePercent"])
        self.assertEqual(1, metrics["counts"]["falseReady"])
        self.assertEqual(1, metrics["counts"]["missedWarning"])

    def test_static_layer_scoring_distinguishes_package_ecosystems(self):
        results = [{"id": "package", "report": {"readiness": "READY", "checks": [
            {"type": "package", "ecosystem": "npm", "name": "shared-name"}
        ]}}]
        truth = {"skills": [{"id": "package", "reviewStatus": "HUMAN_REVIEWED",
                             "actualReadiness": "READY", "blockingDependencies": [],
                             "dependencies": [{"type": "package", "ecosystem": "python",
                                               "name": "shared-name", "inScope": True}]}]}

        metrics = runner.calculate_metrics(results, truth)

        self.assertEqual(0.0, metrics["dependencyRecallPercent"])
        self.assertEqual(0.0, metrics["dependencyPrecisionPercent"])

    def test_scoring_counts_dependency_and_readiness_errors(self):
        truth = {"datasetVersion": "v1", "skills": [{
            "id": "sample", "reviewStatus": "EVIDENCE_REVIEWED", "actualReadiness": "NOT_READY",
            "blockingDependencies": [{"type": "command", "name": "git"}],
            "dependencies": [{"type": "command", "name": "git", "necessity": "REQUIRED", "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v1", "method": "AGENT_ONLY", "model": "test", "run": 1, "skills": [{
            "id": "sample", "readiness": "READY", "dependencies": [
                {"type": "command", "name": "git", "inScope": True, "evidence": "SKILL.md:1"},
                {"type": "command", "name": "curl", "inScope": True, "evidence": "SKILL.md:2"}
            ]
        }]}
        result = scorer.aggregate([scorer.score(truth, prediction)])[0]
        self.assertEqual("100.0%", result["recall"])
        self.assertEqual("50.0%", result["precision"])
        self.assertEqual("100.0%", result["falseReady"])
        self.assertEqual("100.0%", result["coverage"])
        self.assertEqual("100.0%", result["diagnosisCompleteness"])

    def test_ground_truth_keeps_source_necessity_and_human_signoff_separate(self):
        truth = json.loads((PROJECT / "benchmark/annotations/ground-truth.json").read_text(encoding="utf-8"))
        self.assertEqual(30, len(truth["skills"]))
        self.assertTrue(all(skill["reviewStatus"] == "HUMAN_REVIEWED" for skill in truth["skills"]))
        self.assertTrue(all(skill["review"]["humanSignoff"] is True for skill in truth["skills"]))
        dependencies = [item for skill in truth["skills"] for item in skill["dependencies"]]
        self.assertTrue(all(item["necessity"] in {"REQUIRED", "OPTIONAL", "CONDITIONAL"} for item in dependencies))
        self.assertTrue(all(item["source"] in {"COMPATIBILITY_METADATA", "SKILL_TEXT", "SCRIPT", "REFERENCE", "MANIFEST"} for item in dependencies))
        packages = [item for item in dependencies if item["type"] == "package"]
        self.assertGreater(len(packages), 0)
        self.assertTrue(all(item["ecosystem"] in {"python", "npm", "maven"} for item in packages))
        self.assertTrue(all("required" in item for item in packages))
        self.assertTrue(all(item["version"] == item["required"] for item in packages))

    def test_scoring_reports_package_recall_and_package_false_ready(self):
        truth = {"datasetVersion": "v2", "skills": [{
            "id": "packages", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "NOT_READY",
            "blockingDependencies": [{"type": "package", "ecosystem": "python", "name": "pypdf"}],
            "dependencies": [{"type": "package", "ecosystem": "python", "name": "pypdf",
                              "necessity": "REQUIRED", "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v2", "method": "AGENT_ONLY", "model": "test", "run": 1,
                      "skills": [{"id": "packages", "readiness": "READY", "dependencies": []}]}

        result = scorer.aggregate([scorer.score(truth, prediction)])[0]

        self.assertEqual(1, result["packageN"])
        self.assertEqual("0.0%", result["packageRecall"])
        self.assertEqual("0.0%", result["requiredPackageRecall"])
        self.assertEqual("100.0%", result["packageFalseReady"])

    def test_scoring_keeps_false_ready_and_missed_warning_separate(self):
        truth = {"datasetVersion": "v2", "skills": [{
            "id": "warning", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "WARNING",
            "blockingDependencies": [], "dependencies": []
        }, {
            "id": "blocked", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "NOT_READY",
            "blockingDependencies": [{"type": "command", "name": "missing"}],
            "dependencies": [{"type": "command", "name": "missing", "necessity": "REQUIRED",
                              "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v2", "method": "AGENT_ONLY", "model": "test", "run": 1,
                      "skills": [{"id": "warning", "readiness": "READY", "dependencies": []},
                                 {"id": "blocked", "readiness": "READY", "dependencies": []}]}

        result = scorer.aggregate([scorer.score(truth, prediction)])[0]

        self.assertEqual("100.0%", result["falseReady"])
        self.assertEqual("100.0%", result["missedWarning"])
        self.assertEqual(1, result["counts"]["falseReady"])
        self.assertEqual(1, result["counts"]["missedWarning"])

    def test_scoring_ignores_ecosystem_on_non_package_requirements(self):
        truth = {"datasetVersion": "v2", "skills": [{
            "id": "runtime", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "READY",
            "blockingDependencies": [],
            "dependencies": [{"type": "runtime", "name": "python", "necessity": "REQUIRED",
                              "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v2", "method": "AGENT_ONLY", "model": "test", "run": 1,
                      "skills": [{"id": "runtime", "readiness": "READY", "dependencies": [{
                          "type": "runtime", "ecosystem": "python", "name": "python",
                          "inScope": True, "evidence": "SKILL.md:1"
                      }]}]}

        result = scorer.aggregate([scorer.score(truth, prediction)])[0]

        self.assertEqual("100.0%", result["recall"])
        self.assertEqual("100.0%", result["requiredRecall"])

    def test_capability_scoring_is_kind_qualified_and_case_sensitive(self):
        truth = {"datasetVersion": "v3", "skills": [{
            "id": "cap", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "NOT_READY",
            "blockingDependencies": [{"type": "capability", "capabilityKind": "tool", "name": "ExactTool"}],
            "dependencies": [{"type": "capability", "capabilityKind": "tool", "name": "ExactTool",
                              "necessity": "REQUIRED", "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v3", "method": "AGENT_WITH_INSPECTOR", "model": "test", "run": 1,
                      "skills": [{"id": "cap", "readiness": "NOT_READY", "dependencies": [
                          {"type": "capability", "capabilityKind": "tool", "name": "ExactTool", "status": "FAIL",
                           "actual": "NOT LISTED (COMPLETE)", "inScope": True, "evidence": "SKILL.md:1"},
                          {"type": "capability", "capabilityKind": "mcpServer", "name": "ExactTool",
                           "inScope": True, "evidence": "SKILL.md:1"},
                          {"type": "capability", "capabilityKind": "tool", "name": "exacttool",
                           "inScope": True, "evidence": "SKILL.md:1"}
                      ]}]}

        result = scorer.aggregate([scorer.score(truth, prediction)])[0]

        self.assertEqual("100.0%", result["capabilityRecall"])
        self.assertEqual("33.3%", result["capabilityPrecision"])
        self.assertEqual("100.0%", result["requiredCapabilityRecall"])
        self.assertEqual("0.0%", result["capabilityFalseReady"])
        self.assertEqual("100.0%", result["snapshotCoverage"])

    def test_controlled_environment_does_not_forward_dependency_secrets(self):
        old = __import__("os").environ.get("OPENAI_API_KEY")
        __import__("os").environ["OPENAI_API_KEY"] = "must-not-be-forwarded"
        try:
            environment = controlled.controlled_environment()
            self.assertNotIn("OPENAI_API_KEY", environment)
            self.assertEqual("/usr/bin:/bin", environment["PATH"])
            self.assertEqual("isolated", environment["SKILL_INSPECTOR_PACKAGE_METADATA_MODE"])
        finally:
            if old is None:
                __import__("os").environ.pop("OPENAI_API_KEY", None)
            else:
                __import__("os").environ["OPENAI_API_KEY"] = old

    def test_resume_rejects_outputs_from_different_conditions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "SKILL.md").write_text("---\nname: sample\n---\n", encoding="utf-8")
            output = root / "sample.json"
            output.write_text("{}\n", encoding="utf-8")
            output.with_suffix(".meta.json").write_text("{}\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "mismatched output"):
                controlled.run_codex("unused", "model-a", root,
                                     PROJECT / "benchmark/semantic-extraction.schema.json",
                                     "fixed prompt", output, root / "sample.log", 1, True)

    def test_semantic_handoff_removes_ecosystem_from_non_packages_only(self):
        payload = {"schemaVersion": "1.0", "requirements": [
            {"type": "runtime", "ecosystem": "python", "name": "python", "matched": None},
            {"type": "package", "ecosystem": "python", "name": "pypdf"}
        ]}

        handoff = controlled.semantic_handoff(payload)

        self.assertNotIn("ecosystem", handoff["requirements"][0])
        self.assertNotIn("matched", handoff["requirements"][0])
        self.assertEqual("python", handoff["requirements"][1]["ecosystem"])
        self.assertIn("ecosystem", payload["requirements"][0])

    def test_semantic_handoff_upgrades_capabilities_to_11(self):
        payload = {"schemaVersion": "1.0", "requirements": [
            {"type": "capability", "capabilityKind": "tool", "ecosystem": None,
             "name": "ExactTool", "required": "available"}
        ]}

        handoff = controlled.semantic_handoff(payload)

        self.assertEqual("1.1", handoff["schemaVersion"])
        self.assertEqual("tool", handoff["requirements"][0]["capabilityKind"])
        self.assertNotIn("ecosystem", handoff["requirements"][0])

    def test_semantic_handoff_upgrades_skill_dependencies_to_12(self):
        payload = {"schemaVersion": "1.1", "requirements": [
            {"type": "skill", "namespace": "acme", "capabilityKind": None, "ecosystem": None,
             "name": "reader", "required": ">=1"}
        ]}
        handoff = controlled.semantic_handoff(payload)
        self.assertEqual("1.2", handoff["schemaVersion"])
        self.assertEqual("acme", handoff["requirements"][0]["namespace"])
        self.assertNotIn("capabilityKind", handoff["requirements"][0])

    def test_scoring_reports_exact_skill_identity_metrics(self):
        truth = {"datasetVersion": "v4", "skills": [{
            "id": "root", "reviewStatus": "HUMAN_REVIEWED", "actualReadiness": "NOT_READY",
            "blockingDependencies": [{"type": "skill", "namespace": "acme", "name": "reader"}],
            "dependencies": [{"type": "skill", "namespace": "acme", "name": "reader",
                              "necessity": "REQUIRED", "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v4", "method": "AGENT_WITH_INSPECTOR", "model": "test", "run": 1,
                      "skills": [{"id": "root", "readiness": "NOT_READY", "dependencies": [
                          {"type": "skill", "name": "acme/reader", "status": "FAIL",
                           "actual": "Missing from COMPLETE inventory", "inScope": True, "evidence": "SKILL.md:1"}
                      ]}]}
        result = scorer.aggregate([scorer.score(truth, prediction)])[0]
        self.assertEqual("100.0%", result["skillRecall"])
        self.assertEqual("100.0%", result["skillPrecision"])
        self.assertEqual("100.0%", result["requiredSkillRecall"])
        self.assertEqual("0.0%", result["skillFalseReady"])
        self.assertEqual("100.0%", result["skillInventoryCoverage"])

    def test_prediction_requirement_removes_ecosystem_from_non_packages(self):
        runtime = controlled.prediction_requirement(
            {"type": "runtime", "ecosystem": "python", "name": "python"})
        package = controlled.prediction_requirement(
            {"type": "package", "ecosystem": "python", "name": "pypdf"})

        self.assertNotIn("ecosystem", runtime)
        self.assertEqual("python", package["ecosystem"])


if __name__ == "__main__":
    unittest.main()
