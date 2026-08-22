# Real-world Skill Benchmark

This benchmark measures Skill Inspector against unmodified public Agent Skills pinned to exact Git commits. The repository stores only metadata, annotations, and aggregate results—not third-party Skill source. `run-real-benchmark.py` measures the Java static layer. `run-controlled-benchmark.py` runs the complete Agent-only and Agent + Skill Inspector comparison.

## Safety and reproducibility

- Only explicit `https://github.com/<owner>/<repo>.git` URLs and full 40-character commits are accepted.
- Repositories use sparse checkout with Git LFS smudging disabled. Submodules and target scripts are never executed.
- The Java inspector skips symbolic links, limits inspected files to 1 MiB, and scans `scripts/` to depth 5.
- A cache mismatch is an error; the runner never overwrites an unexpected cache directory.
- Running the benchmark requires network access on first use. Review each source's terms before fetching.
- Controlled model trials use a read-only sandbox, disabled personal/project rules, a fixed model, sanitized environment variables, and `PATH=/usr/bin:/bin`.
- Raw model outputs and logs are ignored; published Markdown contains aggregate metrics only.

## Run

```bash
./mvnw -q package
python3 scripts/run-real-benchmark.py
```

Use the pinned cache without network access:

```bash
python3 scripts/run-real-benchmark.py --offline
```

Generated `latest.json` and `latest.md` are ignored so local environment results are not mistaken for universal facts. Copy a reviewed, dated report into `benchmark/results/` when publishing a benchmark release.

## Controlled comparison

The complete experiment uses the same model, target commits, environment description, task scope, and three repetitions for both conditions:

```bash
python3 scripts/run-controlled-benchmark.py \
  --runs 3 \
  --model gpt-5.6-sol \
  --java /absolute/path/to/jdk-21/bin/java

python3 scripts/score-benchmark.py \
  --ground-truth benchmark/annotations/ground-truth.json \
  --predictions benchmark/results/raw/agent-only-run-{1,2,3}.json \
                benchmark/results/raw/agent-with-inspector-run-{1,2,3}.json \
  --output benchmark/results/controlled-2026-08-21.md
```

Agent-only reads the target and reasons about the controlled environment itself. The hybrid condition returns semantic requirements only; the runner passes the exact JSON to `skill-inspector verify`, and Java determines availability. Use `--resume` to rebuild aggregates from completed raw model outputs without rerunning the model.

## Annotation protocol

For each Skill, read `SKILL.md` and only the static files relevant to its workflow. Record all detectable requirements in `annotations/ground-truth.json`; package/library dependencies may be retained with `inScope: false` so V0.1 is not credited or penalized for checks it cannot represent. Every dependency needs a source path and line or frontmatter field. Record provenance (`source`) and necessity (`REQUIRED`, `OPTIONAL`, or `CONDITIONAL`) as separate dimensions.

Use canonical runtime names `java`, `python`, and `node`; keep command names literal (`python3` remains a command while Python is the runtime). Missing prediction records reduce coverage and their in-scope dependencies count as false negatives, so a method cannot improve recall by omitting hard Skills.

Use `AI_REVIEWED` only after an AI-assisted static pass plus evidence and environment validation. Use `HUMAN_REVIEWED` only after independent human signoff. The published V0.1.1 pilot is AI-assisted and explicitly keeps `humanSignoff: false`; its metrics must not be described as final human-grounded accuracy. Raw READY/WARNING/NOT READY counts are observations about one controlled environment, not ecosystem-wide claims.

Record the runtime/OS/PATH/environment policy because readiness is environment-specific. `environment.json` contains the published controlled environment without local absolute paths or secret values. Capture Agent results with `predictions.schema.json` and the fixed prompts in `prompts/`.

```bash
python3 scripts/score-benchmark.py \
  --ground-truth benchmark/annotations/ground-truth.json \
  --predictions agent-only.json agent-with-inspector.json
```

For a final defensible study, replace AI-assisted labels with two independent human reviews and resolve disagreements before changing status to `HUMAN_REVIEWED`. The current results deliberately preserve that limitation.

## Metrics

The scorer reports dependency recall/precision, required-dependency recall, exact compatibility classification accuracy, false-ready and false-block rates, blocking-dependency recall, and diagnosis completeness. Diagnosis completeness is the share of NOT READY cases where every true blocker was reported; it prevents a correct overall classification from hiding an incomplete diagnosis.
