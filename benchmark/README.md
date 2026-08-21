# Real-world Skill Benchmark

This benchmark measures Skill Inspector against unmodified public Agent Skills pinned to exact Git commits. The repository stores only metadata, annotations, and generated inspection results—not third-party Skill source. `run-real-benchmark.py` measures the `JAVA_STATIC` layer; the fixed prompts and prediction schema measure the complete Agent-only and Agent + Skill Inspector methods.

## Safety and reproducibility

- Only explicit `https://github.com/<owner>/<repo>.git` URLs and full 40-character commits are accepted.
- Repositories use sparse checkout with Git LFS smudging disabled. Submodules and target scripts are never executed.
- The Java inspector skips symbolic links, limits inspected files to 1 MiB, and scans `scripts/` to depth 5.
- A cache mismatch is an error; the runner never overwrites an unexpected cache directory.
- Running the benchmark requires network access on first use. Review each source's terms before fetching.

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

## Annotation protocol

For each Skill, read `SKILL.md` and only the static files relevant to its workflow. Record all detectable requirements in `annotations/ground-truth.json`; package/library dependencies may be retained with `inScope: false` so V0.1 is not credited or penalized for checks it cannot represent. Every dependency needs a source path and line or frontmatter field.

Use canonical runtime names `java`, `python`, and `node`; keep command names literal (`python3` remains a command while Python is the runtime). Missing prediction records reduce coverage and their in-scope dependencies count as false negatives, so a method cannot improve recall by omitting hard Skills.

Use `reviewStatus: REVIEWED` only after the dependency list and actual readiness have been checked. Metrics remain `NOT_COMPUTED` while there are no reviewed annotations. Raw READY/WARNING/NOT READY counts are observations about one machine, not accuracy measurements.

Record the exact runtime/OS/PATH/environment description in the ground-truth `environment` field because readiness is environment-specific. Capture Agent results with `predictions.schema.json`, using the fixed prompts in `prompts/`, then compare runs with:

```bash
python3 scripts/score-benchmark.py \
  --ground-truth benchmark/annotations/ground-truth.json \
  --predictions agent-only.json agent-with-inspector.json
```

For a defensible published study, use two independent reviewers and resolve disagreements before running metrics. Run Agent-only and Agent + Skill Inspector with identical model versions, prompts, target snapshots, and environment descriptions; retain raw outputs.
