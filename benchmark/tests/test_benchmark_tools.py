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


class BenchmarkRunnerTest(unittest.TestCase):
    def test_dataset_has_30_unique_pinned_skills(self):
        dataset = json.loads((PROJECT / "benchmark/dataset.json").read_text(encoding="utf-8"))
        runner.validate_dataset(dataset)
        skills = [skill["id"] for repo in dataset["repositories"] for skill in repo["skills"]]
        self.assertEqual(30, len(skills))
        self.assertEqual(30, len(set(skills)))
        self.assertTrue(all(len(repo["commit"]) == 40 for repo in dataset["repositories"]))

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


if __name__ == "__main__":
    unittest.main()
