# Skill Inspector

[简体中文](#简体中文) · [English](#english)

---

<a id="简体中文"></a>

## 简体中文

**面向 Agent Skills 的运行前兼容性检查**

Agent 能读懂一个 Skill，并不意味着当前环境能够运行它。

Skill Inspector 是一个 **Agent Skill**，用于在安装、启用、执行、调试或迁移第三方 Skill 之前，以只读方式检查其运行兼容性。它把 Agent 的语义理解能力与 Java 的确定性环境验证结合起来，尽可能在工作流中途失败之前发现依赖问题。

```text
用户：“这个 Skill 在当前环境能运行吗？”
                    │
                    v
          Agent + Skill Inspector
          ├── 理解 SKILL.md
          ├── 发现依赖
          └── 保留证据
                    │
                    ▼
             结构化 requirements JSON
          ├── source: DECLARED / INFERRED
          └── necessity: REQUIRED / OPTIONAL / CONDITIONAL
                    │
                    v
             Java 确定性检查器
          ├── 运行时及版本
          ├── 命令与环境变量
          ├── 文件、目录与操作系统
          ├── Python / npm / Maven 包
          └── MCP Server / Agent Tool / Capability Snapshot
                    │
                    v
           READY / WARNING / NOT READY
```

```text
$skill-inspector：检查 ./third-party-skill 是否能在这里运行。

INFERRED command: pdftotext
Evidence: scripts/convert.sh:14
Matched: pdftotext "$INPUT" "$OUTPUT"
Environment: NOT FOUND
Readiness: WARNING
```

### 为什么是 Agent Skill + Java 程序？

```text
目标 Skill ──> Agent 语义提取 ──> requirements JSON
                                            │
                    Java 静态发现 ──────────┤
                                            v
                                  确定性环境验证
                                            │
                                            v
                                      兼容性报告
```

Agent 负责理解说明和证据，Java 负责验证真实机器环境。`SKILL.md` 定义检查时机与方法、如何区分 `DECLARED` 和 `INFERRED`，以及何时停止；Java CLI 则安全地处理版本比较、PATH 查找、环境变量存在性、路径、操作系统、评分和稳定 JSON 输出。

**每一个推断依赖都应该可解释。** 推断结果包含证据位置、经过长度限制和脱敏处理的匹配行、确定性推断规则以及置信度。线索不会被包装成已声明事实。

检查不等于执行。Skill Inspector 不运行目标脚本、不导入目标代码、不安装依赖、不修改目标 Skill，也不输出环境变量的值。运行时检查只会执行检查器固定允许的命令：`java --version`、`python3 --version`（找不到时回退到 `python --version`）和 `node --version`。

```text
Skill: pdf-processing

Runtime
✓ Python 3.12

Packages
✓ pypdf 5.1 [python]
✗ pdfplumber [python] NOT FOUND

Commands
✓ pdftotext

Result: NOT READY
```

V0.2 会静态读取根目录的 `requirements.txt`、`pyproject.toml`、`package.json` 和 `pom.xml`，并只读检查本地 Python 包元数据、`node_modules` 与 Maven 本地仓库。它不会 import/require 包、运行 npm lifecycle script、调用包管理器安装依赖或查询远程 Registry。不支持的版本表达式返回 `UNKNOWN`。

V0.3 增加平台无关的 Runtime Capability Snapshot，用于判断当前会话是否明确宣告某个 MCP Server、Agent Tool 或抽象 Capability 可用。Inspector 只读取外部 JSON 清单，不读取任何平台的私有配置，不启动或连接 MCP Server，也不枚举、调用 Tool。`AVAILABLE` 只代表当前运行时清单宣告可用，不保证权限、认证、参数、网络或实际执行成功。

### 环境要求与构建

- 唯一的系统构建前提是 JDK 21+
- 首次构建时，项目自带的 Maven Wrapper 会下载 Maven 3.9.16

```bash
./mvnw clean package
java -jar target/skill-inspector.jar --help
```

打包后的可执行文件为 `target/skill-inspector.jar`。构建完成后，也可以通过轻量启动脚本 `scripts/skill-inspector` 调用。

### 使用方法

输出便于阅读的报告：

```bash
java -jar target/skill-inspector.jar inspect ./examples/healthy-skill
```

输出稳定的机器可读报告：

```bash
java -jar target/skill-inspector.jar inspect ./examples/missing-env-skill --json
```

验证 Agent 从自然语言说明中提取的语义依赖：

```bash
java -jar target/skill-inspector.jar verify ./third-party-skill \
  --requirements requirements.json --json
```

同时验证当前 Agent Runtime 已公开的能力清单：

```bash
java -jar target/skill-inspector.jar verify ./examples/capability-skill \
  --requirements ./examples/capability-skill/requirements.json \
  --capabilities ./examples/capability-skill/runtime-capabilities.json --json
```

Snapshot 由 [`references/runtime-capabilities.schema.json`](references/runtime-capabilities.schema.json) 定义。每种能力的清单覆盖度必须标为 `COMPLETE` 或 `PARTIAL`：完整清单中未出现的必需能力会失败，部分清单中未出现的能力保持 `UNKNOWN`；无 Snapshot 时同样保持 `UNKNOWN`，不会假装 READY。名称精确且区分大小写，只接受 Snapshot 明示的 alias。

交接格式由 [`references/semantic-requirements.schema.json`](references/semantic-requirements.schema.json) 定义。`source` 表示依赖是如何被发现的，`necessity` 则独立表示该依赖属于 `REQUIRED`、`OPTIONAL` 还是 `CONDITIONAL`。因此，缺失的必需语义依赖可以阻止就绪，同时不会被错误描述成 Skill 已明确声明的依赖。

退出码：READY/WARNING 为 `0`，兼容性失败为 `2`，无效输入或检查错误为 `1`。调用方还必须读取 `status`/`readiness`；WARNING 并不等于已经就绪。

目标 Skill 可以在现有 YAML frontmatter 中声明兼容性：

```yaml
compatibility:
  runtimes:
    java: ">=21"
    python: ">=3.11"
  commands: [git, pdftotext]
  env: [OPENAI_API_KEY]
  os: [linux, macos]
  files: [./config.json]
  directories: [./scripts]
  capabilities:
    - capabilityKind: mcpServer
      name: docsServer
      necessity: required
    - capabilityKind: tool
      name: search_docs
      necessity: conditional
```

该扩展是可选的、便于人工阅读，并且不会影响忽略未知字段的运行时。对象形式、可选依赖、Snapshot 状态、评分方式和 JSON 契约请参阅 [V0.3 规范](references/compatibility-spec.md)。

### 测试与评估

```bash
./mvnw test
./scripts/run-evals.sh
```

JUnit 覆盖确定性检查器与解析逻辑。Eval Runner 会构建项目并执行 29 个跨平台合成用例，其中 10 个覆盖 Capability Snapshot 的完整/部分清单、精确名称、显式 alias、UNKNOWN、缺失阻断、冲突拒绝以及“不执行目标代码”原则。触发提示词和基线方法位于 [`evals/`](evals/README.md)。

#### 真实 Skill 受控基准测试

V0.1.1 对来自 6 个 GitHub 仓库、固定到具体提交的 30 个未修改公开 Agent Skills 进行了评估。实验固定模型（`gpt-5.6-sol`）、提示词、目标提交和测试环境，并在两种条件下分别对每个 Skill 运行 3 次，共计 180 次模型试验。

```bash
python3 scripts/run-controlled-benchmark.py \
  --runs 3 --java /absolute/path/to/jdk-21/bin/java
```

| 方法 | 依赖召回率 | 精确率 | 必需依赖召回率 | 分类准确率 | 诊断完整度 | False Ready | Missed Warning |
|---|---:|---:|---:|---:|---:|---:|---:|
| 仅 Agent | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 0.0% | 33.3% |
| **Agent + Skill Inspector** | **74.0%** | **91.9%** | **100.0%** | **77.8%** | **100.0%** | **0.0%** | **1.9%** |

混合方法还将 False Block 从 50.0% 降低到 33.3%，但尚未完全消除。项目维护者已对 30 个 Skill 的依赖、证据、必要性和环境结论完成人工复核。以上结果仍然只是固定数据集、单一模型和单一 Linux 环境下的受控实验结果，不代表整个 Agent Skill 生态的普遍准确率。在 Skill Inspector 当前支持的机器可读依赖定义下，这 30 个 Skill 均未声明受支持格式的结构化运行时契约。

V0.2 在相同的 30 个固定 Skill、模型、三轮协议和基础 Linux 环境上加入了 147 个 Python/npm 包依赖标签（Maven `N=0`）：

| 方法 | 总体召回率 | 精确率 | 必需依赖召回率 | False Ready | Missed Warning | Package Recall | Package Precision | Required Package Recall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 仅 Agent | 47.5% | 79.6% | 66.7% | 0.0% | 29.4% | 44.4% | 83.8% | 100.0% |
| **Agent + Skill Inspector** | **73.5%** | **91.3%** | **100.0%** | **0.0%** | **7.8%** | **75.7%** | **94.1%** | **100.0%** |

严格 False Ready（`NOT_READY -> READY`）在 V0.1.1 和 V0.2 的两种条件中均为 0%。先前的 1.2% 和 4.4% 将 `WARNING -> READY` 也合并到 False Ready；修正后它们是 Missed Warning 1.9% 和 7.8%。V0.2 新增 147 个 package 标签、4 个 package blocker，且 3 个 Skill 的 readiness 标签发生变化，因此跨版本百分比不能直接作为 regression 对比。Package False Ready 仍为 0%，package 层面证明的主要收益是发现覆盖率。

详细信息请参阅[基准测试协议](benchmark/README.md)、[固定数据集](benchmark/dataset.json)、[受控环境](benchmark/environment.json)、[V0.1.1 完整结果](benchmark/results/controlled-2026-08-21.md)和 [V0.2 完整结果](benchmark/results/controlled-v0.2.0-rc1.md)。

V0.3 当前只完成 10 个确定性 Capability Eval 和 6 个固定真实样本的静态 pilot 标注；尚未运行新的受控模型 Benchmark，因此这里不报告 Capability Recall/Precision。正式比较需要扩充并人工确认语料与 Snapshot 后单独审批。

### 当前支持范围

已支持：

- Java、Python 和 Node 的存在性及版本约束
- Python、npm 和 Maven 包依赖及常见版本约束的本地只读验证
- `requirements.txt`、`pyproject.toml`、`package.json` 和 `pom.xml` 的结构化依赖解析
- 基于 PATH/PATHEXT 的跨平台命令发现，不依赖 `which`
- 环境变量存在性检查，且强制脱敏
- 以目标 Skill 为基准的必需文件和目录检查
- Windows、Linux 和 macOS 声明
- YAML frontmatter 解析，以及基于脚本扩展名、shebang 和简单 shell 命令位置的保守高置信度推断
- 带证据位置、匹配文本、规则、置信度、脱敏和符号链接排除的可解释推断
- `source` 与 `necessity` 相互独立的 Agent-to-Java 结构化语义交接
- MCP Server、Agent Tool 与显式 Capability 的 Snapshot 存在性检查
- `COMPLETE`/`PARTIAL` 覆盖语义、四种可用性状态和显式 alias 匹配
- `DECLARED`/`INFERRED` 溯源、READY/WARNING/NOT READY、透明评分以及文本和 JSON 输出

暂不支持：

- MCP/Tool 主动连接、启动、调用、枚举，以及网络、认证、权限和参数 Schema 验证
- 自动读取 Agent 私有配置、平台专属适配、远程 Capability Registry 或 Skill-to-Skill 能力检查
- 由 Java 进行通用自然语言依赖提取；语义理解由 Agent 负责
- 自动安装、自动修复或修改目标 Skill
- 恶意代码、提示词注入、漏洞、病毒或供应链扫描
- 证明一个兼容的 Skill 一定安全、正确或能够成功完成任务

### 设计说明

代码按 `parse`、`model`、`check`、`core`、`report` 和 `cli` 分包。所有检查器共享一个小型 `EnvironmentProbe` 边界，因此不引入依赖注入框架也能在单元测试中替换操作系统和进程访问。评分用于解释结果，而不是代替规则：任何 `REQUIRED` 失败都会产生 NOT READY，可选和条件依赖失败只产生警告。

### 路线图

- **V0.1：** 本地兼容性检查——已完成。
- **V0.1.1：** 最小语义交接、30 个固定样本、三轮受控基准测试和人工复核标准答案——已完成。
- **V0.2：** Python/npm/Maven 包依赖检查、语义交接与真实基准测试——`v0.2.0`。
- **V0.3：** 平台无关的 MCP、Agent Tool 与 Capability Snapshot 检查——候选版本 `0.3.0-rc1`。
- **V0.4：** Skill-to-Skill 依赖。

长期方向包括能力图谱、迁移辅助、Skill CI/Registry 集成和基于运行轨迹的演进；这些内容目前仍明确位于项目范围之外。

[返回顶部](#skill-inspector) · [跳转到 English](#english)

---

<a id="english"></a>

## English

**Preflight compatibility inspection for Agent Skills**

Your agent can read a Skill. That doesn't mean it can run it.

Skill Inspector is an **Agent Skill** that performs a read-only compatibility check before another Skill is installed, activated, executed, debugged, or migrated. It combines Agent reasoning with deterministic environment verification so dependencies are found before a workflow fails halfway through.

```text
User: “Can I run this Skill?”
                 │
                 v
       Agent + Skill Inspector
       ├── understands SKILL.md
       ├── discovers dependencies
       └── preserves evidence
                 │
                 ▼
       Structured requirements JSON
       ├── source: DECLARED / INFERRED
       └── necessity: REQUIRED / OPTIONAL / CONDITIONAL
                 │
                 v
        Java deterministic checker
       ├── runtimes and versions
       ├── commands and environment
       ├── files, directories, OS
       ├── Python / npm / Maven packages
       └── MCP Server / Agent Tool / Capability Snapshot
                 │
                 v
        READY / WARNING / NOT READY
```

```text
$skill-inspector: Check whether ./third-party-skill can run here.

INFERRED command: pdftotext
Evidence: scripts/convert.sh:14
Matched: pdftotext "$INPUT" "$OUTPUT"
Environment: NOT FOUND
Readiness: WARNING
```

### Why a Skill and a Java program?

```text
Target Skill ──> Agent semantic extraction ──> requirements JSON
                                                    │
                           Java static discovery ───┤
                                                    v
                                      deterministic verification
                                                    │
                                                    v
                                         compatibility report
```

The Agent understands instructions and evidence. Java verifies the actual machine. `SKILL.md` defines when and how to inspect, how to distinguish `DECLARED` from `INFERRED`, and when to stop. The Java CLI safely handles version comparison, PATH lookup, environment presence, paths, OS detection, scoring, and stable JSON.

**Every inferred dependency should be explainable.** Inferred checks include an evidence location, a bounded and redacted matched line, a deterministic inference rule, and confidence. A clue is never presented as a declaration.

Inspection is not execution. Skill Inspector never runs target scripts, imports target code, installs dependencies, changes the target, or prints environment-variable values. Runtime checks execute only the inspector's fixed allowlist: `java --version`, `python3 --version` (falling back to `python --version`), and `node --version`.

```text
Skill: pdf-processing

Runtime
✓ Python 3.12

Packages
✓ pypdf 5.1 [python]
✗ pdfplumber [python] NOT FOUND

Commands
✓ pdftotext

Result: NOT READY
```

V0.2 statically reads root-level `requirements.txt`, `pyproject.toml`, `package.json`, and `pom.xml`, then checks local Python distribution metadata, `node_modules`, and the local Maven repository in read-only mode. It never imports/requires a package, runs an npm lifecycle script, invokes a package manager to install dependencies, or queries a remote registry. Unsupported version expressions return `UNKNOWN`.

V0.3 adds a platform-neutral Runtime Capability Snapshot for checking whether the current session explicitly advertises an MCP server, Agent tool, or abstract capability. The inspector reads only the external JSON inventory. It does not read private platform configuration, start or connect to MCP servers, enumerate tools, or invoke a tool. `AVAILABLE` means only that the runtime inventory advertises the capability—not that permissions, authentication, parameters, network access, or execution will succeed.

### Requirements and build

- JDK 21+ is the only system build prerequisite
- The included Maven Wrapper downloads Maven 3.9.16 on first use

```bash
./mvnw clean package
java -jar target/skill-inspector.jar --help
```

The shaded executable is `target/skill-inspector.jar`. The small `scripts/skill-inspector` launcher provides a convenient command after the build.

### Usage

Human-readable report:

```bash
java -jar target/skill-inspector.jar inspect ./examples/healthy-skill
```

Stable machine-readable report:

```bash
java -jar target/skill-inspector.jar inspect ./examples/missing-env-skill --json
```

Verify semantic requirements extracted from prose by an Agent:

```bash
java -jar target/skill-inspector.jar verify ./third-party-skill \
  --requirements requirements.json --json
```

Verify the capability inventory already exposed by the current Agent runtime:

```bash
java -jar target/skill-inspector.jar verify ./examples/capability-skill \
  --requirements ./examples/capability-skill/requirements.json \
  --capabilities ./examples/capability-skill/runtime-capabilities.json --json
```

The Snapshot contract is [`references/runtime-capabilities.schema.json`](references/runtime-capabilities.schema.json). Coverage for each capability kind is `COMPLETE` or `PARTIAL`: a required capability absent from a complete inventory fails, while absence from a partial inventory stays `UNKNOWN`. No Snapshot is also `UNKNOWN`, never READY. Names are exact and case-sensitive; only aliases explicitly listed in the Snapshot are accepted.

The handoff schema is [`references/semantic-requirements.schema.json`](references/semantic-requirements.schema.json). `source` describes how a requirement was found; `necessity` independently records whether it is `REQUIRED`, `OPTIONAL`, or `CONDITIONAL`. A missing required semantic dependency can therefore block readiness without being misrepresented as a declaration.

Exit codes are `0` for READY/WARNING, `2` for compatibility FAIL, and `1` for invalid input or an inspection error. Callers must also inspect `status`/`readiness`; WARNING is not proof of readiness.

Declare compatibility in a target Skill's existing frontmatter:

```yaml
compatibility:
  runtimes:
    java: ">=21"
    python: ">=3.11"
  commands: [git, pdftotext]
  env: [OPENAI_API_KEY]
  os: [linux, macos]
  files: [./config.json]
  directories: [./scripts]
  capabilities:
    - capabilityKind: mcpServer
      name: docsServer
      necessity: required
    - capabilityKind: tool
      name: search_docs
      necessity: conditional
```

The extension is optional, human-readable, and safe for runtimes that ignore unknown fields. See [the V0.3 specification](references/compatibility-spec.md) for object forms, optional dependencies, Snapshot semantics, scoring, and the JSON contract.

### Tests and evaluations

```bash
./mvnw test
./scripts/run-evals.sh
```

JUnit covers deterministic checkers and parsing. The Eval runner builds the project and executes 29 cross-platform synthetic cases, including 10 Capability Snapshot cases for complete/partial inventories, exact names, explicit aliases, UNKNOWN, missing blockers, conflict rejection, and the no-execution invariant. Trigger prompts and baseline methodology live in [`evals/`](evals/README.md).

#### Controlled real-world benchmark

V0.1.1 evaluates 30 unmodified public Agent Skills pinned across six GitHub repositories. The controlled experiment locks the model (`gpt-5.6-sol`), prompts, target commits, environment, and three runs per Skill for both conditions: 180 model trials total.

```bash
python3 scripts/run-controlled-benchmark.py \
  --runs 3 --java /absolute/path/to/jdk-21/bin/java
```

| Method | Dependency recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | False ready | Missed warning |
|---|---:|---:|---:|---:|---:|---:|---:|
| Agent only | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 0.0% | 33.3% |
| **Agent + Skill Inspector** | **74.0%** | **91.9%** | **100.0%** | **77.8%** | **100.0%** | **0.0%** | **1.9%** |

The hybrid method also reduced false blocks from 50.0% to 33.3%, but did not eliminate them. The project maintainer manually reviewed the dependencies, evidence, necessity, and environment conclusions for all 30 Skills. These results remain a controlled experiment on a fixed dataset, one model, and one Linux environment; they are not an ecosystem-wide accuracy claim. Within this benchmark and Skill Inspector's machine-readable dependency definition, none of the 30 pinned Skills declared a structured runtime contract in the supported schema.

V0.2 adds 147 Python/npm package labels to the same 30 pinned Skills, model, three-run protocol, and base Linux environment (Maven `N=0`):

| Method | Overall recall | Precision | Required recall | False ready | Missed warning | Package recall | Package precision | Required package recall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Agent only | 47.5% | 79.6% | 66.7% | 0.0% | 29.4% | 44.4% | 83.8% | 100.0% |
| **Agent + Skill Inspector** | **73.5%** | **91.3%** | **100.0%** | **0.0%** | **7.8%** | **75.7%** | **94.1%** | **100.0%** |

Strict false ready (`NOT_READY -> READY`) was 0% for both conditions in V0.1.1 and V0.2. The former 1.2% and 4.4% values had also counted `WARNING -> READY`; after separating the definitions, they are missed-warning rates of 1.9% and 7.8%. V0.2 adds 147 package labels, four package blockers, and changes three Skills' readiness labels, so the cross-version percentages are not a direct regression comparison. Package false ready remains 0%; the demonstrated package-level gain is discovery coverage.

See [the benchmark protocol](benchmark/README.md), [pinned dataset](benchmark/dataset.json), [controlled environment](benchmark/environment.json), [V0.1.1 results](benchmark/results/controlled-2026-08-21.md), and [V0.2 results](benchmark/results/controlled-v0.2.0-rc1.md).

V0.3 currently has 10 deterministic capability evals and static pilot labels for six pinned real samples. No new controlled model benchmark has been run, so no capability recall/precision result is claimed here. A formal comparison requires an expanded, reviewed corpus and fixed Snapshots under separate approval.

### Current scope

Supported:

- Java, Python, and Node presence/version constraints
- Read-only local verification of Python, npm, and Maven packages with common version constraints
- Structured dependency parsing for `requirements.txt`, `pyproject.toml`, `package.json`, and `pom.xml`
- Portable command discovery using PATH/PATHEXT, without assuming `which`
- Environment-variable presence with mandatory redaction
- Required files and directories, relative to the target Skill
- Windows, Linux, and macOS declarations
- YAML frontmatter parsing and conservative high-confidence inference from script extensions, shebangs, and simple shell command positions
- Explainable inference with evidence location, matched text, rule, confidence, redaction, and symbolic-link exclusion
- Structured Agent-to-Java semantic handoff with independent source and necessity dimensions
- Snapshot presence checks for MCP servers, Agent tools, and explicit capabilities
- `COMPLETE`/`PARTIAL` coverage, four availability states, and explicit alias matching
- DECLARED/INFERRED provenance, READY/WARNING/NOT READY, transparent scores, human and JSON output

Not supported:

- Active MCP/tool connection, startup, invocation, enumeration, network, authentication, permission, or parameter-schema checks
- Private Agent configuration discovery, platform-specific adapters, remote capability registries, or Skill-to-Skill capability checks
- General natural-language dependency extraction in Java; the Agent handles semantic interpretation
- Automatic installation, remediation, or target modification
- Malware, prompt-injection, vulnerability, virus, or supply-chain scanning
- Proving that a compatible Skill is safe or functionally correct

### Design notes

The package structure separates `parse`, `model`, `check`, `core`, `report`, and `cli`. Checkers share a small `EnvironmentProbe` boundary, keeping OS/process access replaceable in unit tests without a dependency-injection framework. The score is explanatory rather than authoritative: any `REQUIRED` failure produces NOT READY, while optional and conditional failures remain warnings.

### Roadmap

- **V0.1:** Local compatibility inspection — complete.
- **V0.1.1:** Minimal semantic handoff, 30 pinned samples, three-run controlled benchmark, and human-reviewed ground truth — complete.
- **V0.2:** Python/npm/Maven package inspection, semantic handoff, and real-world benchmark — `v0.2.0`.
- **V0.3:** Platform-neutral MCP, Agent Tool, and Capability Snapshot checks — release candidate `0.3.0-rc1`.
- **V0.4:** Skill-to-Skill dependencies.

Longer-term possibilities include a capability graph, migration assistance, Skill CI/registry integration, and trace-based evolution. They intentionally remain outside the current scope.

[Back to top](#skill-inspector) · [转到简体中文](#简体中文)
