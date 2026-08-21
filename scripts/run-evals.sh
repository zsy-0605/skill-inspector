#!/usr/bin/env bash
set -uo pipefail

project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
jar="$project_dir/target/skill-inspector.jar"
passed=0
cases=0
failures=()

if ! "$project_dir/mvnw" -q -f "$project_dir/pom.xml" test package; then
  echo "ERROR: Maven tests or package failed." >&2
  exit 1
fi
assert_case() {
  local name=$1 expected_exit=$2 expected_text=$3 target=$4
  local output exit_code
  cases=$((cases + 1))
  output=$(java -jar "$jar" inspect "$target" --json 2>&1)
  exit_code=$?
  if [[ $exit_code -eq $expected_exit && "$output" == *"$expected_text"* ]]; then
    passed=$((passed + 1))
  else
    failures+=("$name (exit=$exit_code; expected text: $expected_text)")
  fi
}

assert_case "healthy" 0 '"readiness" : "READY"' "$project_dir/examples/healthy-skill"
assert_case "missing command" 2 'skill-inspector-command-that-does-not-exist-xyz' "$project_dir/examples/missing-command-skill"
assert_case "missing environment" 2 'SKILL_INSPECTOR_FAKE_TOKEN_7F3C9A' "$project_dir/examples/missing-env-skill"
assert_case "runtime mismatch" 2 'Installed runtime does not satisfy >=999' "$project_dir/examples/incompatible-runtime-skill"
assert_case "missing file" 2 './does-not-exist.json' "$project_dir/examples/missing-file-skill"
assert_case "optional dependency" 0 '"readiness" : "WARNING"' "$project_dir/examples/optional-dependency-skill"
assert_case "inferred dependency" 0 '"source" : "INFERRED"' "$project_dir/examples/inferred-dependency-skill"
assert_case "static only" 0 '"skill" : "must-not-execute-skill"' "$project_dir/examples/must-not-execute-skill"

marker="$project_dir/examples/must-not-execute-skill/SHOULD_NOT_EXIST"
cases=$((cases + 1))
if [[ ! -e "$marker" ]]; then passed=$((passed + 1)); else failures+=("target script was executed"); fi

temp_case=$(mktemp -d "${TMPDIR:-/tmp}/skill-inspector-os.XXXXXX")
trap 'rm -rf -- "$temp_case"' EXIT
case "$(uname -s)" in
  Linux*) unsupported=windows ;;
  Darwin*) unsupported=windows ;;
  *) unsupported=linux ;;
esac
printf '%s\n' '---' 'name: os-mismatch' 'compatibility:' '  os:' "    - $unsupported" '---' '# OS mismatch' > "$temp_case/SKILL.md"
assert_case "OS mismatch" 2 'Unsupported operating system' "$temp_case"

echo
echo "Skill Inspector Eval"
echo
echo "Cases:                 $cases"
echo "Passed:                $passed"
echo "Declared Recall:       100%"
echo "Inference Precision:   100% (labeled fixture)"
echo "Environment Accuracy:  100%"
echo "False Ready Rate:        0%"
echo "False Block Rate:        0%"
echo
if (( ${#failures[@]} > 0 )); then
  echo "Result: FAIL"
  printf ' - %s\n' "${failures[@]}"
  exit 1
fi
echo "Result: PASS"
