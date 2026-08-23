# Skill Inspector

<p align="center"><strong>在执行 Agent Skill 之前，先证明当前 Runtime 能否满足它</strong></p>

<p align="center">
  <a href="https://github.com/zsy-0605/skill-inspector/tags"><img alt="Version v0.4.0" src="https://img.shields.io/badge/version-v0.4.0-blue.svg"></a>
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/license-MIT-yellow.svg"></a>
  <img alt="Java 21+" src="https://img.shields.io/badge/Java-21%2B-orange.svg">
  <img alt="Java tests 61 passed" src="https://img.shields.io/badge/Java_tests-61_passed-brightgreen.svg">
  <img alt="Synthetic eval 41 passed" src="https://img.shields.io/badge/Synthetic_Eval-41_passed-brightgreen.svg">
</p>

<p align="center">
  <a href="#简体中文">简体中文</a> · <a href="#english">English</a>
</p>

---

<a id="简体中文"></a>

## 简体中文

> Agent 能读懂一个 Skill，不代表当前环境真的能运行它。

Skill Inspector 是一个面向第三方 Agent Skills 的运行前兼容性检查 Skill。它在安装、启用、执行、调试或迁移 Skill 之前，以只读方式回答三个问题：

```text
它需要什么？ → 当前 Runtime 有什么？ → 为什么判定 READY / WARNING / NOT READY？
```

Agent 负责理解 `SKILL.md` 中的语义和证据，Java 负责确定性验证运行时、命令、环境变量、文件、包、Agent Capability 和 Skill 依赖图。

### 适合什么任务

```text
检查这个 GitHub Skill 在我的环境里能不能运行。

为什么这个 Skill 已经安装，却仍然无法执行？

启用第三方 Skill 前，检查它缺少哪些 Runtime、CLI、包和环境变量。

检查它依赖哪些 MCP Server、Agent Tool 和其他 Skills。

给我一份带证据、依赖路径和确定性状态的兼容性报告。
```

它不是恶意代码扫描器、包漏洞审计器或通用 Skill Runtime。

### 15 秒看懂结果

```text
Skill: report-composer

Skill Dependencies
✗ document-reader >=2
  Actual: AVAILABLE 1.8.0
  Status: FAIL
  Dependency Path: report-composer -> data-extractor -> document-reader

Readiness: NOT READY
```

结果不仅说明“缺了什么”，还保留它从哪里被发现、是否为必需依赖、实际检测到什么，以及完整的传递依赖路径。

### V0.4：真正的 Skill 依赖图检查

一个 Skill 可以依赖另一个 Skill，而后者还可能继续依赖第三个 Skill：

```text
report-composer
└── data-extractor >=1.2
    └── document-reader 1.x
```

V0.4 使用调用方提供的只读 Skill Inventory，确定性验证：

- 直接依赖与传递依赖；
- 最小版本约束；
- 完整 `dependencyPath`；
- REQUIRED、OPTIONAL、CONDITIONAL 路径传播；
- REQUIRED cycle 与非必需路径 cycle；
- `COMPLETE` 与 `PARTIAL` Inventory 的不同语义。

两条规则不会被混淆：

> **Missing information ≠ Missing Skill**

> **Inspection ≠ Resolution ≠ Execution**

这里的 Resolution 只表示遍历已经提供的 JSON 依赖图。Inspector 不搜索、下载、安装、激活或执行任何 Skill。

### 核心能力

| 层级 | 检查内容 | 确定性输入 |
|---|---|---|
| 基础环境 | Java、Python、Node、CLI、环境变量、文件、目录、OS | 本机只读探测 |
| Package | Python、npm、Maven 包及保守版本约束 | 本地元数据、`node_modules`、Maven 本地仓库 |
| Agent Runtime | MCP Server、Agent Tool、显式 Capability | Runtime Capability Snapshot |
| Skill Graph | 直接/传递 Skill、版本、路径、cycle | Skill Inventory |
| Semantic Layer | Skill 正文中的自然语言依赖 | Agent → Java Semantic Handoff |

所有发现都同时保留两个互不替代的维度：

```text
source:    DECLARED / INFERRED
necessity: REQUIRED / OPTIONAL / CONDITIONAL
```

**Every inferred dependency should be explainable.** 每一个推断依赖都应包含证据位置、经过长度限制与脱敏处理的匹配文本、推断规则和置信度，而不是把线索包装成声明事实。

### 工作原理

```text
目标 Skill
    │
    ├── SKILL.md / compatibility frontmatter
    ├── requirements.txt / pyproject.toml
    ├── package.json / pom.xml
    └── 有限范围的静态脚本证据
    │
    ▼
Agent 语义提取 ───────────────┐
                              ├── Semantic Handoff
Java 静态发现 ────────────────┘
    │
    ├── 本机 Environment Probe
    ├── Runtime Capability Snapshot
    └── Skill Inventory 只读图解析
    │
    ▼
READY / WARNING / NOT READY
```

状态语义：

| 状态 | 含义 |
|---|---|
| `PASS` | 依赖已被确定性满足 |
| `FAIL` | REQUIRED 依赖被确定性证明不满足 |
| `WARNING` | OPTIONAL/CONDITIONAL 依赖缺失，或总体仍需确认 |
| `UNKNOWN` | 当前信息不足，不能证明满足，也不能假装缺失 |

任何 REQUIRED `FAIL` 都会得到 `NOT READY`。`UNKNOWN` 不会被当作 READY。

### 快速开始

系统构建前提是 JDK 21+。项目自带 Maven Wrapper，首次构建会下载 Maven 3.9.16。

```bash
git clone https://github.com/zsy-0605/skill-inspector.git
cd skill-inspector
./mvnw clean package
java -jar target/skill-inspector.jar --version
```

检查一个本地 Skill：

```bash
java -jar target/skill-inspector.jar inspect ./examples/healthy-skill
```

输出稳定 JSON：

```bash
java -jar target/skill-inspector.jar inspect ./examples/missing-env-skill --json
```

验证 Agent 从正文提取的语义依赖：

```bash
java -jar target/skill-inspector.jar verify ./third-party-skill \
  --requirements requirements.json --json
```

同时验证 Agent Runtime Capability：

```bash
java -jar target/skill-inspector.jar verify ./examples/capability-skill \
  --requirements ./examples/capability-skill/requirements.json \
  --capabilities ./examples/capability-skill/runtime-capabilities.json --json
```

验证直接与传递 Skill 依赖：

```bash
java -jar target/skill-inspector.jar inspect ./examples/skill-dependency-skill \
  --skills ./examples/skill-dependency-skill/skill-inventory.json --json
```

退出码：READY/WARNING 为 `0`，兼容性失败为 `2`，无效输入或检查错误为 `1`。调用方仍应读取 `status` 和 `readiness`；WARNING 不等于已经就绪。

### 声明兼容性

Skill 可以在现有 YAML frontmatter 中声明机器可读依赖：

```yaml
compatibility:
  runtimes:
    java: ">=21"
    python: ">=3.11"
  commands: [git, pdftotext]
  env: [OPENAI_API_KEY]
  os: [linux, macos]
  packages:
    - ecosystem: python
      name: pypdf
      version: ">=5"
      necessity: required
  capabilities:
    - capabilityKind: tool
      name: search_docs
      necessity: conditional
  skills:
    - namespace: acme
      name: data-extractor
      version: ">=1.2"
      necessity: required
```

完整对象形式、状态聚合和版本边界见 [Compatibility Spec](references/compatibility-spec.md)。

### 三个结构化契约

| 契约 | 当前版本 | 用途 |
|---|---:|---|
| [Semantic Handoff](references/semantic-requirements.schema.json) | 1.2 | Agent 将正文中的语义依赖交给 Java |
| [Runtime Capability Snapshot](references/runtime-capabilities.schema.json) | 1.0 | 描述当前会话已经公开的 MCP/Tool/Capability |
| [Skill Inventory](references/skill-inventory.schema.json) | 1.0 | 描述可用 Skill、版本、依赖边和覆盖完整度 |

名称、alias、coverage 与 availability 都由外部契约显式提供。Java core 不读取 Codex、Claude、Cursor 等平台的私有配置，也不内置平台别名。

### 安全原则

检查第三方 Skill 时，最危险的错误是“为了判断能不能执行，先执行一遍”。Skill Inspector 明确禁止这种行为。

它不会：

- 运行或 source 目标脚本；
- import/require 目标包；
- 运行 npm lifecycle script；
- 调用 `pip`、`npm`、`mvn` 安装依赖；
- 启动或连接 MCP Server；
- 枚举或调用 Agent Tool；
- 扫描任意 Skill 目录、下载、安装或激活 Skill；
- 输出环境变量值、endpoint、token 或认证配置；
- 修改被检查的 Skill。

运行时版本探测只使用固定允许的命令：`java --version`、`python3 --version`（必要时回退到 `python --version`）和 `node --version`。

### 项目结构

```text
skill-inspector/
├── SKILL.md               # Agent 的行为策略、工作流与停止条件
├── src/main/java/         # Java 检查器源代码
├── src/test/java/         # JUnit 测试；正常检查 Skill 时不会运行
├── references/            # Semantic Handoff、Snapshot、Inventory 与报告 Schema
├── examples/              # 可直接运行的最小示例
├── evals/                 # Synthetic Eval
├── benchmark/             # 固定真实 Skill 数据集、标注与报告
└── target/                # 本地构建产物，不提交到 Git
    └── skill-inspector.jar
```

运行时真正执行的是构建后的 `target/skill-inspector.jar`。`src` 只在构建或测试阶段被编译，不会逐个执行其中的 Java 文件。

### 测试与发布验证

```bash
./mvnw test
./scripts/run-evals.sh
```

| 验证层 | V0.4 结果 |
|---|---:|
| Java Unit Tests | 61 / 61 PASS |
| Benchmark Tool Tests | 21 / 21 PASS |
| Synthetic Eval | 41 / 41 PASS |
| V0.4 Skill Dependency Eval | 12 / 12 PASS |
| 最小真实依赖图验证 | 8 / 8 PASS |
| 固定 Real-sample Pilot 证据 | 5 / 5 PASS |

完整 Agent-only vs Agent + Inspector Benchmark 不会在普通测试中自动运行。

### 真实 Skill 受控基准测试

V0.1.1 固定了来自 6 个 GitHub 仓库的 30 个未修改公开 Skills、模型、提示词、环境和三轮协议，两种条件共 180 次模型试验：

| 方法 | 依赖召回率 | 精确率 | 必需依赖召回率 | 分类准确率 | 诊断完整度 | False Ready | Missed Warning |
|---|---:|---:|---:|---:|---:|---:|---:|
| 仅 Agent | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 0.0% | 33.3% |
| **Agent + Skill Inspector** | **74.0%** | **91.9%** | **100.0%** | **77.8%** | **100.0%** | **0.0%** | **1.9%** |

V0.2 在同一固定语料上加入 147 个 Python/npm package 标签：

| 方法 | Package Recall | Package Precision | Required Package Recall | Package False Ready |
|---|---:|---:|---:|---:|
| 仅 Agent | 44.4% | 83.8% | 100.0% | 0.0% |
| **Agent + Skill Inspector** | **75.7%** | **94.1%** | **100.0%** | **0.0%** |

这些数字只代表固定数据集、单一模型和单一 Linux 环境，不是整个 Agent Skill 生态的普遍准确率。30 个 Skills 的依赖、证据、必要性和环境结论已由项目维护者人工复核。V0.3 Capability 与 V0.4 Skill Graph 目前只报告确定性 Eval 和固定 Pilot，不宣称未经正式受控实验得到的 Recall/Precision。

详见[基准测试协议](benchmark/README.md)、[固定数据集](benchmark/dataset.json)、[受控环境](benchmark/environment.json)、[V0.1.1 结果](benchmark/results/controlled-2026-08-21.md)和 [V0.2 结果](benchmark/results/controlled-v0.2.0-rc1.md)。

### 当前边界

已支持：

- Java、Python、Node、CLI、环境变量、文件、目录和 OS；
- Python/npm/Maven 包的本地只读验证；
- `requirements.txt`、`pyproject.toml`、`package.json`、`pom.xml`；
- 可解释静态推断、证据、置信度、脱敏和符号链接排除；
- MCP Server、Agent Tool、显式 Capability Snapshot；
- Skill 直接/传递依赖、版本、路径、cycle 和 necessity 传播；
- 人类可读报告与稳定 JSON Report 1.2。

暂不支持：

- MCP/Tool 主动连接、调用、认证、权限或参数 Schema 验证；
- Capability Provider Resolution、`anyOf`、替代提供者；
- 远程 Registry、Skill 下载、安装、激活或修复；
- 递归检查子 Skill 的 Runtime/Package；
- 恶意代码、提示词注入、漏洞、病毒或供应链扫描；
- 保证一个兼容的 Skill 一定安全、正确或执行成功。

### 路线图

- **V0.1：** 本地 Runtime/Command/Env/File/OS 兼容性检查。
- **V0.1.1：** Semantic Handoff、30 个固定样本与三轮受控 Benchmark。
- **V0.2：** Python/npm/Maven PackageRequirement。
- **V0.3：** 平台无关的 MCP、Agent Tool 与 Capability Snapshot。
- **V0.4：** Skill-to-Skill 直接/传递依赖、版本、路径与 cycle——当前正式版本 `v0.4.0`。
- **V0.5：** Capability Composition / Provider Resolution。

[返回顶部](#skill-inspector) · [跳转到 English](#english)

---

<a id="english"></a>

## English

> An Agent can understand a Skill without being able to run it in the current runtime.

Skill Inspector is a preflight compatibility Skill for third-party Agent Skills. Before installation, activation, execution, debugging, or migration, it answers three questions in read-only mode:

```text
What does it need? → What does this runtime have? → Why is it READY / WARNING / NOT READY?
```

The Agent understands semantics and evidence in `SKILL.md`; Java deterministically verifies runtimes, commands, environment variables, files, packages, Agent capabilities, and Skill dependency graphs.

### When to use it

```text
Can this GitHub Skill run in my current environment?

Why is this installed Skill still unable to execute?

Before activation, check its runtimes, CLIs, packages, and environment variables.

Which MCP servers, Agent tools, and other Skills does it require?

Give me an explainable compatibility report with dependency paths.
```

It is not a malware scanner, vulnerability auditor, or universal Skill runtime.

### Understand the result in 15 seconds

```text
Skill: report-composer

Skill Dependencies
✗ document-reader >=2
  Actual: AVAILABLE 1.8.0
  Status: FAIL
  Dependency Path: report-composer -> data-extractor -> document-reader

Readiness: NOT READY
```

The report explains not only what is missing, but where the requirement came from, whether it blocks execution, what was observed, and the full transitive path.

### V0.4: real Skill dependency graphs

A Skill may depend on another Skill, which may itself have dependencies:

```text
report-composer
└── data-extractor >=1.2
    └── document-reader 1.x
```

V0.4 deterministically verifies a caller-provided, read-only Skill Inventory:

- direct and transitive dependencies;
- minimal version constraints;
- complete `dependencyPath` values;
- REQUIRED, OPTIONAL, and CONDITIONAL propagation;
- required and non-required cycles;
- distinct `COMPLETE` and `PARTIAL` Inventory semantics.

Two rules are never conflated:

> **Missing information ≠ Missing Skill**

> **Inspection ≠ Resolution ≠ Execution**

Resolution means traversing supplied JSON edges only. The inspector never searches for, downloads, installs, activates, or executes a Skill.

### Core capabilities

| Layer | What is checked | Deterministic input |
|---|---|---|
| Base environment | Java, Python, Node, CLIs, env, files, directories, OS | Read-only local probes |
| Package | Python, npm, Maven packages and conservative constraints | Local metadata, `node_modules`, local Maven repository |
| Agent runtime | MCP servers, Agent tools, explicit capabilities | Runtime Capability Snapshot |
| Skill graph | Direct/transitive Skills, versions, paths, cycles | Skill Inventory |
| Semantic layer | Natural-language requirements in Skill prose | Agent → Java Semantic Handoff |

Every finding preserves two independent dimensions:

```text
source:    DECLARED / INFERRED
necessity: REQUIRED / OPTIONAL / CONDITIONAL
```

**Every inferred dependency should be explainable.** An inference carries an evidence location, bounded and redacted matched text, an inference rule, and confidence. A clue is never presented as a declaration.

### How it works

```text
Target Skill
    │
    ├── SKILL.md / compatibility frontmatter
    ├── requirements.txt / pyproject.toml
    ├── package.json / pom.xml
    └── bounded static script evidence
    │
    ▼
Agent semantic extraction ─────────┐
                                   ├── Semantic Handoff
Java static discovery ─────────────┘
    │
    ├── local Environment Probe
    ├── Runtime Capability Snapshot
    └── read-only Skill Inventory resolution
    │
    ▼
READY / WARNING / NOT READY
```

| Status | Meaning |
|---|---|
| `PASS` | The requirement is deterministically satisfied |
| `FAIL` | A REQUIRED requirement is deterministically unsatisfied |
| `WARNING` | An OPTIONAL/CONDITIONAL dependency is absent, or confirmation is still needed |
| `UNKNOWN` | Available information proves neither presence nor absence |

Any required `FAIL` produces `NOT READY`. `UNKNOWN` is never treated as READY.

### Quick start

JDK 21+ is the only system build prerequisite. The included Maven Wrapper downloads Maven 3.9.16 on first use.

```bash
git clone https://github.com/zsy-0605/skill-inspector.git
cd skill-inspector
./mvnw clean package
java -jar target/skill-inspector.jar --version
```

Inspect a local Skill:

```bash
java -jar target/skill-inspector.jar inspect ./examples/healthy-skill
```

Emit stable JSON:

```bash
java -jar target/skill-inspector.jar inspect ./examples/missing-env-skill --json
```

Verify semantic requirements extracted by an Agent:

```bash
java -jar target/skill-inspector.jar verify ./third-party-skill \
  --requirements requirements.json --json
```

Verify advertised Agent Runtime capabilities:

```bash
java -jar target/skill-inspector.jar verify ./examples/capability-skill \
  --requirements ./examples/capability-skill/requirements.json \
  --capabilities ./examples/capability-skill/runtime-capabilities.json --json
```

Verify direct and transitive Skill dependencies:

```bash
java -jar target/skill-inspector.jar inspect ./examples/skill-dependency-skill \
  --skills ./examples/skill-dependency-skill/skill-inventory.json --json
```

Exit codes are `0` for READY/WARNING, `2` for compatibility failure, and `1` for invalid input or inspection error. Callers must still read `status` and `readiness`; WARNING is not proof of readiness.

### Declare compatibility

Skills may declare machine-readable requirements in existing YAML frontmatter:

```yaml
compatibility:
  runtimes:
    java: ">=21"
    python: ">=3.11"
  commands: [git, pdftotext]
  env: [OPENAI_API_KEY]
  os: [linux, macos]
  packages:
    - ecosystem: python
      name: pypdf
      version: ">=5"
      necessity: required
  capabilities:
    - capabilityKind: tool
      name: search_docs
      necessity: conditional
  skills:
    - namespace: acme
      name: data-extractor
      version: ">=1.2"
      necessity: required
```

See the [Compatibility Spec](references/compatibility-spec.md) for full object shapes, aggregation, and version boundaries.

### Three structured contracts

| Contract | Version | Purpose |
|---|---:|---|
| [Semantic Handoff](references/semantic-requirements.schema.json) | 1.2 | Transfers prose requirements from Agent to Java |
| [Runtime Capability Snapshot](references/runtime-capabilities.schema.json) | 1.0 | Describes MCP/tool/capability inventory exposed by the current session |
| [Skill Inventory](references/skill-inventory.schema.json) | 1.0 | Describes available Skills, versions, dependency edges, and coverage |

Names, aliases, coverage, and availability are supplied explicitly. The Java core reads no private Codex, Claude, Cursor, or other platform configuration and contains no platform aliases.

### Safety principles

The most dangerous way to inspect a third-party Skill is to execute it first. Skill Inspector explicitly forbids that behavior.

It never:

- runs or sources target scripts;
- imports/requires target packages;
- runs npm lifecycle scripts;
- invokes `pip`, `npm`, or `mvn` to install dependencies;
- starts or connects to MCP servers;
- enumerates or invokes Agent tools;
- scans arbitrary Skill directories or downloads/installs/activates Skills;
- reports environment values, endpoints, tokens, or credentials;
- modifies the inspected Skill.

Runtime version probes use only a fixed allowlist: `java --version`, `python3 --version` (falling back to `python --version`), and `node --version`.

### Repository layout

```text
skill-inspector/
├── SKILL.md               # Agent behavior, workflow, and stop conditions
├── src/main/java/         # Java inspector source
├── src/test/java/         # JUnit tests; not run during normal inspection
├── references/            # Handoff, Snapshot, Inventory, and report schemas
├── examples/              # Runnable minimal examples
├── evals/                 # Synthetic Eval
├── benchmark/             # Pinned real-Skill corpus, labels, and reports
└── target/                # Local build output; not committed
    └── skill-inspector.jar
```

Normal operation executes the built `target/skill-inspector.jar`. Files under `src` are compiled during build/test; Java source files are not executed one by one.

### Tests and release validation

```bash
./mvnw test
./scripts/run-evals.sh
```

| Validation layer | V0.4 result |
|---|---:|
| Java Unit Tests | 61 / 61 PASS |
| Benchmark Tool Tests | 21 / 21 PASS |
| Synthetic Eval | 41 / 41 PASS |
| V0.4 Skill Dependency Eval | 12 / 12 PASS |
| Minimal real dependency-graph validation | 8 / 8 PASS |
| Pinned real-sample pilot evidence | 5 / 5 PASS |

The full Agent-only vs Agent + Inspector benchmark is never run as part of ordinary tests.

### Controlled real-Skill benchmark

V0.1.1 pins 30 unmodified public Skills from six GitHub repositories, plus the model, prompts, environment, and three-run protocol—180 model trials across two conditions:

| Method | Dependency recall | Precision | Required recall | Classification accuracy | Diagnosis completeness | False ready | Missed warning |
|---|---:|---:|---:|---:|---:|---:|---:|
| Agent only | 60.9% | 89.2% | 93.1% | 66.7% | 96.7% | 0.0% | 33.3% |
| **Agent + Skill Inspector** | **74.0%** | **91.9%** | **100.0%** | **77.8%** | **100.0%** | **0.0%** | **1.9%** |

V0.2 adds 147 Python/npm package labels to the same fixed corpus:

| Method | Package recall | Package precision | Required package recall | Package false ready |
|---|---:|---:|---:|---:|
| Agent only | 44.4% | 83.8% | 100.0% | 0.0% |
| **Agent + Skill Inspector** | **75.7%** | **94.1%** | **100.0%** | **0.0%** |

These numbers describe one fixed dataset, one model, and one Linux environment—not ecosystem-wide accuracy. The maintainer reviewed dependency, evidence, necessity, and environment labels for all 30 Skills. V0.3 Capability and V0.4 Skill Graph currently report deterministic evals and pinned pilots only; no unrun Recall/Precision result is claimed.

See the [benchmark protocol](benchmark/README.md), [pinned dataset](benchmark/dataset.json), [controlled environment](benchmark/environment.json), [V0.1.1 results](benchmark/results/controlled-2026-08-21.md), and [V0.2 results](benchmark/results/controlled-v0.2.0-rc1.md).

### Current boundaries

Supported:

- Java, Python, Node, CLIs, environment variables, files, directories, and OS;
- read-only local Python/npm/Maven verification;
- `requirements.txt`, `pyproject.toml`, `package.json`, and `pom.xml`;
- explainable inference, evidence, confidence, redaction, and symlink exclusion;
- MCP server, Agent tool, and explicit Capability Snapshots;
- direct/transitive Skill dependencies, versions, paths, cycles, and necessity propagation;
- human-readable output and stable JSON Report 1.2.

Not supported:

- active MCP/tool connection, invocation, authentication, permission, or parameter-schema checks;
- capability-provider resolution, `anyOf`, or alternative providers;
- remote registries or Skill download, installation, activation, or repair;
- recursive child-Skill runtime/package inspection;
- malware, prompt-injection, vulnerability, virus, or supply-chain scanning;
- guarantees that a compatible Skill is safe, correct, or functionally successful.

### Roadmap

- **V0.1:** Local Runtime/Command/Env/File/OS compatibility.
- **V0.1.1:** Semantic Handoff, 30 pinned samples, and a three-run controlled benchmark.
- **V0.2:** Python/npm/Maven PackageRequirement.
- **V0.3:** Platform-neutral MCP, Agent Tool, and Capability Snapshot.
- **V0.4:** Direct/transitive Skill dependencies, versions, paths, and cycles—current release `v0.4.0`.
- **V0.5:** Capability Composition / Provider Resolution.

[Back to top](#skill-inspector) · [转到简体中文](#简体中文)
