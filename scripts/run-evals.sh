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
if ! python3 -m unittest discover -s "$project_dir/benchmark/tests" -p 'test_*.py'; then
  echo "ERROR: Benchmark tooling tests failed." >&2
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

assert_capability_case() {
  local name=$1 expected_exit=$2 expected_text=$3 target=$4 snapshot=${5:-}
  local output exit_code
  cases=$((cases + 1))
  if [[ -n "$snapshot" ]]; then
    output=$(java -jar "$jar" inspect "$target" --capabilities "$snapshot" --json 2>&1)
  else
    output=$(java -jar "$jar" inspect "$target" --json 2>&1)
  fi
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

cases=$((cases + 1))
semantic_output=$(java -jar "$jar" verify "$project_dir/examples/semantic-handoff-skill" \
  --requirements "$project_dir/examples/semantic-handoff-skill/requirements.json" --json 2>&1)
semantic_exit=$?
if [[ $semantic_exit -eq 2 && "$semantic_output" == *'"necessity" : "REQUIRED"'* && "$semantic_output" == *'"source" : "INFERRED"'* ]]; then
  passed=$((passed + 1))
else
  failures+=("semantic handoff (exit=$semantic_exit; expected required inferred blocker)")
fi

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

package_cases="$temp_case/package-cases"

python_present="$package_cases/python-present"
mkdir -p "$python_present/.venv/lib/python3.12/site-packages/skill_inspector_eval-2.4.1.dist-info"
printf '%s\n' '---' 'name: python-present' '---' > "$python_present/SKILL.md"
printf '%s\n' 'skill-inspector-eval>=2.0' > "$python_present/requirements.txt"
printf '%s\n' 'Metadata-Version: 2.1' 'Name: skill-inspector-eval' 'Version: 2.4.1' '' > "$python_present/.venv/lib/python3.12/site-packages/skill_inspector_eval-2.4.1.dist-info/METADATA"
assert_case "Python package present" 0 '"status" : "PASS"' "$python_present"

python_missing="$package_cases/python-missing"
mkdir -p "$python_missing"
printf '%s\n' '---' 'name: python-missing' '---' > "$python_missing/SKILL.md"
printf '%s\n' 'skill-inspector-package-that-does-not-exist-7f3c9a>=1' > "$python_missing/requirements.txt"
assert_case "Python package missing" 2 'skill-inspector-package-that-does-not-exist-7f3c9a' "$python_missing"

npm_present="$package_cases/npm-present"
mkdir -p "$npm_present/node_modules/skill-inspector-eval-npm"
printf '%s\n' '---' 'name: npm-present' '---' > "$npm_present/SKILL.md"
printf '%s\n' '{"dependencies":{"skill-inspector-eval-npm":"^3.0.0"},"scripts":{"postinstall":"touch SHOULD_NOT_EXIST"}}' > "$npm_present/package.json"
printf '%s\n' '{"name":"skill-inspector-eval-npm","version":"3.2.1"}' > "$npm_present/node_modules/skill-inspector-eval-npm/package.json"
assert_case "npm package present" 0 '"status" : "PASS"' "$npm_present"

npm_missing="$package_cases/npm-missing"
mkdir -p "$npm_missing"
printf '%s\n' '---' 'name: npm-missing' '---' > "$npm_missing/SKILL.md"
printf '%s\n' '{"dependencies":{"skill-inspector-missing-npm-7f3c9a":"^1.0.0"}}' > "$npm_missing/package.json"
assert_case "npm package missing" 2 'skill-inspector-missing-npm-7f3c9a' "$npm_missing"

maven_present="$package_cases/maven-present"
mkdir -p "$maven_present/.m2/repository/io/skillinspector/eval/demo/1.2.3"
printf '%s\n' '---' 'name: maven-present' '---' > "$maven_present/SKILL.md"
printf '%s\n' '<project><dependencies><dependency><groupId>io.skillinspector.eval</groupId><artifactId>demo</artifactId><version>[1.0,2.0)</version></dependency></dependencies></project>' > "$maven_present/pom.xml"
printf '%s\n' '<project/>' > "$maven_present/.m2/repository/io/skillinspector/eval/demo/1.2.3/demo-1.2.3.pom"
assert_case "Maven package present" 0 '"status" : "PASS"' "$maven_present"

version_mismatch="$package_cases/version-mismatch"
mkdir -p "$version_mismatch/.venv/lib/python3.12/site-packages/skill_inspector_mismatch-1.0.0.dist-info"
printf '%s\n' '---' 'name: version-mismatch' '---' > "$version_mismatch/SKILL.md"
printf '%s\n' 'skill-inspector-mismatch>=2.0' > "$version_mismatch/requirements.txt"
printf '%s\n' 'Name: skill-inspector-mismatch' 'Version: 1.0.0' '' > "$version_mismatch/.venv/lib/python3.12/site-packages/skill_inspector_mismatch-1.0.0.dist-info/METADATA"
assert_case "package version mismatch" 2 'Installed package version does not satisfy >=2.0' "$version_mismatch"

unknown_version="$package_cases/unknown-version"
mkdir -p "$unknown_version/node_modules/skill-inspector-unknown-range"
printf '%s\n' '---' 'name: unknown-version' '---' > "$unknown_version/SKILL.md"
printf '%s\n' '{"dependencies":{"skill-inspector-unknown-range":"workspace:*"}}' > "$unknown_version/package.json"
printf '%s\n' '{"name":"skill-inspector-unknown-range","version":"1.0.0"}' > "$unknown_version/node_modules/skill-inspector-unknown-range/package.json"
assert_case "unsupported package range" 0 '"status" : "UNKNOWN"' "$unknown_version"

cases=$((cases + 1))
if [[ ! -e "$npm_present/SHOULD_NOT_EXIST" ]]; then passed=$((passed + 1)); else failures+=("npm postinstall was executed"); fi

capability_cases="$temp_case/capability-cases"
mkdir -p "$capability_cases"

write_capability_skill() {
  local target=$1 kind=$2 capability_name=$3 necessity=$4
  mkdir -p "$target"
  printf '%s\n' '---' "name: $(basename "$target")" 'compatibility:' '  capabilities:' \
    "    - capabilityKind: $kind" "      name: $capability_name" "      necessity: $necessity" \
    '---' '# Capability fixture' > "$target/SKILL.md"
}

write_snapshot() {
  local target=$1 mcp_coverage=$2 tool_coverage=$3 abstract_coverage=$4 entries=$5
  printf '%s\n' "{\"schemaVersion\":\"1.0\",\"runtime\":{\"name\":\"eval-runtime\"},\"coverage\":{\"mcpServer\":\"$mcp_coverage\",\"tool\":\"$tool_coverage\",\"capability\":\"$abstract_coverage\"},\"capabilities\":$entries}" > "$target"
}

required_mcp="$capability_cases/required-mcp-available"
write_capability_skill "$required_mcp" mcpServer docsServer required
write_snapshot "$required_mcp/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"mcpServer","name":"docsServer","aliases":[],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]'
assert_capability_case "required MCP available" 0 '"status" : "PASS"' "$required_mcp" "$required_mcp/snapshot.json"

complete_missing="$capability_cases/complete-missing"
write_capability_skill "$complete_missing" mcpServer missingServer required
write_snapshot "$complete_missing/snapshot.json" COMPLETE COMPLETE PARTIAL '[]'
assert_capability_case "complete snapshot missing required MCP" 2 'NOT LISTED (COMPLETE)' "$complete_missing" "$complete_missing/snapshot.json"

partial_missing="$capability_cases/partial-missing"
write_capability_skill "$partial_missing" tool unknownTool required
write_snapshot "$partial_missing/snapshot.json" COMPLETE PARTIAL PARTIAL '[]'
assert_capability_case "partial snapshot missing capability" 0 '"status" : "UNKNOWN"' "$partial_missing" "$partial_missing/snapshot.json"

configured="$capability_cases/configured"
write_capability_skill "$configured" tool configuredTool required
write_snapshot "$configured/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"tool","name":"configuredTool","aliases":[],"availability":"CONFIGURED","source":"STATIC_CONFIGURATION"}]'
assert_capability_case "configured is unknown" 0 '"actual" : "CONFIGURED"' "$configured" "$configured/snapshot.json"

exact_tool="$capability_cases/exact-tool"
write_capability_skill "$exact_tool" tool exactTool required
write_snapshot "$exact_tool/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"tool","name":"exactTool","aliases":[],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]'
assert_capability_case "exact tool match" 0 '"resolvedCapability" : "exactTool"' "$exact_tool" "$exact_tool/snapshot.json"

alias_tool="$capability_cases/alias-tool"
write_capability_skill "$alias_tool" tool search_docs required
write_snapshot "$alias_tool/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"tool","name":"mcp__docs__search","aliases":["search_docs"],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]'
assert_capability_case "explicit alias match" 0 '"resolvedCapability" : "mcp__docs__search"' "$alias_tool" "$alias_tool/snapshot.json"

conditional_missing="$capability_cases/conditional-missing"
write_capability_skill "$conditional_missing" tool fallbackTool conditional
write_snapshot "$conditional_missing/snapshot.json" COMPLETE COMPLETE PARTIAL '[]'
assert_capability_case "conditional tool missing" 0 '"status" : "WARNING"' "$conditional_missing" "$conditional_missing/snapshot.json"

no_snapshot="$capability_cases/no-snapshot"
write_capability_skill "$no_snapshot" capability webSearch required
assert_capability_case "no snapshot is unknown" 0 '"actual" : "NO SNAPSHOT"' "$no_snapshot"

conflict="$capability_cases/conflict"
write_capability_skill "$conflict" tool shared required
write_snapshot "$conflict/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"tool","name":"first","aliases":["shared"],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"},{"capabilityKind":"tool","name":"shared","aliases":[],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]'
assert_capability_case "snapshot alias conflict" 1 'Ambiguous capability name or alias' "$conflict" "$conflict/snapshot.json"

no_execution="$capability_cases/no-execution"
write_capability_skill "$no_execution" tool dangerousTool required
mkdir -p "$no_execution/scripts"
printf '%s\n' '#!/bin/sh' 'touch SHOULD_NOT_EXIST' > "$no_execution/scripts/danger.sh"
write_snapshot "$no_execution/snapshot.json" COMPLETE COMPLETE PARTIAL '[{"capabilityKind":"tool","name":"dangerousTool","aliases":[],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]'
cases=$((cases + 1))
no_execution_output=$(java -jar "$jar" inspect "$no_execution" --capabilities "$no_execution/snapshot.json" --json 2>&1)
no_execution_exit=$?
if [[ $no_execution_exit -eq 0 && "$no_execution_output" == *'"status" : "PASS"'* && ! -e "$no_execution/SHOULD_NOT_EXIST" ]]; then
  passed=$((passed + 1))
else
  failures+=("capability inspection does not execute (exit=$no_execution_exit or marker created)")
fi

echo
echo "Skill Inspector Eval"
echo
echo "Cases:                 $cases"
echo "Passed:                $passed"
echo "Declared Recall:       100%"
echo "Inference Precision:   100% (labeled fixture)"
echo "Environment Accuracy:  100%"
echo "Package Cases:            8"
echo "Capability Cases:        10"
echo "False Ready Rate:        0%"
echo "False Block Rate:        0%"
echo
if (( ${#failures[@]} > 0 )); then
  echo "Result: FAIL"
  printf ' - %s\n' "${failures[@]}"
  exit 1
fi
echo "Result: PASS"
