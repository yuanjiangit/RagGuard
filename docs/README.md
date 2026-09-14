# RagGuard Documentation

**用户文档 / User docs**

| Doc | Language | Content |
|---|---|---|
| [Quick start (README)](../README.md#quick-start-3-steps) | EN | 3 steps to a quality gate |
| [中文快速上手](zh/getting-started.md) | 中文 | 从零接入到 CI 质量门的完整路径 |
| [Metrics reference](metrics.md) | EN | formulas, short-circuit rules, thresholds, symptom → fix table（中文排障对照见[快速上手 §6](zh/getting-started.md)） |
| [Test set format](test-set-format.md) | EN | YAML/JSON schema, validation rules, writing guidelines, generation workflow |
| [Configuration reference](configuration.md) | EN | All `ragguard.*` properties, JUnit 5 options, output files, programmatic usage |
| [GitHub Action](../action/README.md) | EN | PR evaluation + metric summary comment |
| [Cost estimation](cost-estimation.md) | 中文 | 调用次数模型、token 量级、省钱杠杆 |

**设计笔记 / Design notes**（公开算法与调研笔记 — the "why" behind the code）

| Doc | Content |
|---|---|
| [01 — Ragas metric algorithms](design/01-ragas-metrics-notes.md) | 四指标公式与 RagGuard 工程决策（中文） |
| [02 — Python ecosystem survey](design/02-python-ecosystem-notes.md) | Ragas / DeepEval / TruLens 调研与差异点声明（中文） |
| [03 — Test-set generation](design/03-testset-generation-notes.md) | 生成管线设计：为什么产物是候选清单（中文） |

**架构与流程 / Architecture & process**

| Doc | Content |
|---|---|
| [Architecture](architecture.md) | Module map, core abstractions, data flow, status semantics, caching model |
| [Release checklist](release.md) | 发布 0.1.0 到 Maven Central 的完整步骤（中文） |
| [Retrospective draft](retrospective-draft.md) | 复盘文章素材（中文，内部） |
| [Project plan](RagGuard开工计划.md) | 立项计划原文（中文，历史文档） |

**示例 / Examples**

| Demo | Stack |
|---|---|
| [spring-ai-es-demo](../examples/spring-ai-es-demo/README.md) | Spring Boot + Spring AI + Elasticsearch vector store |
| [langchain4j-demo](../examples/langchain4j-demo/README.md) | langchain4j, no Spring — pure JUnit 5 extension route |
