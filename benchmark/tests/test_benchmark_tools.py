import importlib.util
import json
from pathlib import Path
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
            "id": "sample", "reviewStatus": "REVIEWED", "actualReadiness": "NOT_READY",
            "dependencies": [{"type": "command", "name": "git", "inScope": True, "evidence": "SKILL.md:1"}]
        }]}
        prediction = {"datasetVersion": "v1", "method": "AGENT_ONLY", "model": "test", "skills": [{
            "id": "sample", "readiness": "READY", "dependencies": [
                {"type": "command", "name": "git", "inScope": True, "evidence": "SKILL.md:1"},
                {"type": "command", "name": "curl", "inScope": True, "evidence": "SKILL.md:2"}
            ]
        }]}
        result = scorer.score(truth, prediction)
        self.assertEqual("100.0%", result["recall"])
        self.assertEqual("50.0%", result["precision"])
        self.assertEqual("100.0%", result["falseReady"])
        self.assertEqual("100.0%", result["coverage"])


if __name__ == "__main__":
    unittest.main()
